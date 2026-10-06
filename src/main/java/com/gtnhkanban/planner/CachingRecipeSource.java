package com.gtnhkanban.planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gtnhkanban.model.ItemKey;

/**
 * Remembers recipe lookups and material classes across breakdowns, so shared parts (plates, screws, circuits...) are
 * looked up once per session instead of once per breakdown. Recipe lists are cached as found, before any strategy
 * picks one, so every strategy can reuse them. Each cache keeps at most {@code capacity} materials and drops the least
 * recently used. A lookup that throws is not cached. Not thread-safe: use from one thread.
 */
public final class CachingRecipeSource implements RecipeSource {

    private final RecipeSource delegate;
    private final Map<ItemKey, List<RecipeCandidate>> recipes;
    private final Map<ItemKey, String> rejected;
    private final Map<ItemKey, Boolean> base;
    private final Map<ItemKey, Boolean> ores;
    private long hits, misses;

    public CachingRecipeSource(RecipeSource delegate, int capacity) {
        this.delegate = delegate;
        this.recipes = lru(capacity);
        this.rejected = lru(capacity);
        this.base = lru(capacity);
        this.ores = lru(capacity);
    }

    private static <V> Map<ItemKey, V> lru(final int capacity) {
        return new LinkedHashMap<ItemKey, V>(64, 0.75f, true) {

            @Override
            protected boolean removeEldestEntry(Map.Entry<ItemKey, V> eldest) {
                return size() > capacity;
            }
        };
    }

    @Override
    public List<RecipeCandidate> recipes(ItemKey material) {
        List<RecipeCandidate> cached = recipes.get(material);
        if (cached != null) {
            hits++;
            return cached;
        }
        misses++;
        List<RecipeCandidate> found = Collections
            .unmodifiableList(new ArrayList<RecipeCandidate>(delegate.recipes(material)));
        recipes.put(material, found);
        rejected.put(material, delegate.rejectedRecipes(material));
        return found;
    }

    @Override
    public String rejectedRecipes(ItemKey material) {
        String reason = rejected.get(material);
        return reason == null ? "" : reason;
    }

    @Override
    public boolean isBaseMaterial(ItemKey material) {
        Boolean cached = base.get(material);
        if (cached == null) {
            cached = Boolean.valueOf(delegate.isBaseMaterial(material));
            base.put(material, cached);
        }
        return cached.booleanValue();
    }

    @Override
    public boolean isOre(ItemKey material) {
        Boolean cached = ores.get(material);
        if (cached == null) {
            cached = Boolean.valueOf(delegate.isOre(material));
            ores.put(material, cached);
        }
        return cached.booleanValue();
    }

    /** Forgets everything, e.g. when leaving a world whose recipes may differ from the next one's. */
    public void clear() {
        recipes.clear();
        rejected.clear();
        base.clear();
        ores.clear();
        hits = 0;
        misses = 0;
    }

    /** Recipe lookups answered from the cache since the last {@link #clear()}. */
    public long hits() {
        return hits;
    }

    /** Recipe lookups that had to ask the underlying source since the last {@link #clear()}. */
    public long misses() {
        return misses;
    }

    public int size() {
        return recipes.size();
    }
}
