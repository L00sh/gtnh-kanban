package com.gtnhkanban.planner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.RecipeIngredient;

public class CachingRecipeSourceTest {

    private static final ItemKey HULL = item("hull"), CASING = item("casing"), PLATE = item("plate"),
        SCREW = item("screw"), INGOT = item("ingot");

    private final CountingSource source = new CountingSource();

    public CachingRecipeSourceTest() {
        source.add(HULL, recipe(PLATE, 8), recipe(SCREW, 2));
        source.add(CASING, recipe(PLATE, 6), recipe(SCREW, 4));
        source.add(PLATE, recipe(INGOT, 2));
        source.add(SCREW, recipe(INGOT, 1));
    }

    @Test
    public void secondBreakdownReusesSharedPartsInsteadOfAskingAgain() {
        CachingRecipeSource cache = new CachingRecipeSource(source, 100);

        plan(HULL, cache);
        int afterFirst = source.lookups.size();
        plan(CASING, cache);

        // Only the casing itself is new; plates and screws come from the cache.
        assertEquals(afterFirst + 1, source.lookups.size());
        assertEquals(1, Collections.frequency(source.lookups, PLATE));
        assertEquals(1, Collections.frequency(source.lookups, SCREW));
        assertEquals(2, cache.hits());
        assertEquals(4, cache.misses());
        assertEquals(
            1,
            source.baseChecks.get(INGOT)
                .intValue());
    }

    @Test
    public void cachedResultsMatchUncachedOnesForEveryStrategy() {
        CachingRecipeSource cache = new CachingRecipeSource(source, 100);
        for (BreakdownStrategy strategy : BreakdownStrategy.values()) {
            BreakdownPlanner direct = new BreakdownPlanner(HULL, strategy, 1, 4096);
            direct.step(source, Long.MAX_VALUE);
            BreakdownPlanner cached = new BreakdownPlanner(HULL, strategy, 1, 4096);
            cached.step(cache, Long.MAX_VALUE);
            assertEquals(
                direct.result()
                    .nodeCount(),
                cached.result()
                    .nodeCount());
        }
    }

    @Test
    public void leastRecentlyUsedEntriesAreDroppedPastTheCap() {
        CachingRecipeSource cache = new CachingRecipeSource(source, 2);
        cache.recipes(PLATE);
        cache.recipes(SCREW);
        cache.recipes(PLATE);
        cache.recipes(HULL);

        assertEquals(2, cache.size());
        cache.recipes(PLATE);
        assertEquals("Recently used plate stayed cached", 1, Collections.frequency(source.lookups, PLATE));
        cache.recipes(SCREW);
        assertEquals("Screw was evicted and looked up again", 2, Collections.frequency(source.lookups, SCREW));
    }

    @Test
    public void failedLookupsAreNotCachedAndClearForgetsEverything() {
        CachingRecipeSource cache = new CachingRecipeSource(source, 100);
        source.failing = true;
        try {
            cache.recipes(PLATE);
            fail("lookup failure was swallowed");
        } catch (IllegalStateException expected) {}
        source.failing = false;
        assertFalse(
            cache.recipes(PLATE)
                .isEmpty());

        cache.clear();

        assertEquals(0, cache.size());
        assertEquals(0, cache.hits());
        cache.recipes(PLATE);
        assertEquals(3, Collections.frequency(source.lookups, PLATE));
    }

    @Test
    public void cachedListsCannotBeChangedByCallers() {
        CachingRecipeSource cache = new CachingRecipeSource(source, 100);
        List<RecipeCandidate> recipes = cache.recipes(PLATE);
        assertNotNull(recipes);
        try {
            recipes.clear();
            fail("cached list was mutable");
        } catch (UnsupportedOperationException expected) {}
        assertTrue(
            !cache.recipes(PLATE)
                .isEmpty());
    }

    private static void plan(ItemKey root, RecipeSource recipes) {
        assertTrue(new BreakdownPlanner(root, BreakdownStrategy.CRAFTING_TABLE, 1, 4096).step(recipes, Long.MAX_VALUE));
    }

    private static ItemKey item(String name) {
        return new ItemKey("test:" + name, 0);
    }

    private static List<RecipeIngredient> recipe(ItemKey material, int amount) {
        return Collections.singletonList(new RecipeIngredient(material, amount, false));
    }

    private static final class CountingSource implements RecipeSource {

        final Map<ItemKey, List<RecipeCandidate>> recipes = new HashMap<ItemKey, List<RecipeCandidate>>();
        final List<ItemKey> lookups = new ArrayList<ItemKey>();
        final Map<ItemKey, Integer> baseChecks = new HashMap<ItemKey, Integer>();
        boolean failing;

        @SafeVarargs
        final void add(ItemKey material, List<RecipeIngredient>... slots) {
            recipes.put(
                material,
                Collections.singletonList(
                    new RecipeCandidate("Craft", RecipeCandidate.Kind.CRAFTING_TABLE, -1, 1, Arrays.asList(slots))));
        }

        @Override
        public List<RecipeCandidate> recipes(ItemKey material) {
            lookups.add(material);
            if (failing) throw new IllegalStateException("NEI handler crashed");
            List<RecipeCandidate> found = recipes.get(material);
            return found == null ? new ArrayList<RecipeCandidate>() : new ArrayList<RecipeCandidate>(found);
        }

        @Override
        public boolean isBaseMaterial(ItemKey material) {
            Integer count = baseChecks.get(material);
            baseChecks.put(material, count == null ? 1 : count + 1);
            return material.equals(INGOT);
        }

        @Override
        public boolean isOre(ItemKey material) {
            return false;
        }
    }
}
