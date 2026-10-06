package com.gtnhkanban.planner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.Test;

import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.ItemRequirement;
import com.gtnhkanban.model.RecipeIngredient;
import com.gtnhkanban.model.RecipeTree;

/**
 * A small GTNH-shaped recipe graph. The machine can be hand-crafted from plates or assembled; plates can be
 * hand-hammered (2 ingots), forge-hammered (3 ingots for 2), bent (1 ingot), or made from ore (always refused).
 */
public class BreakdownPlannerTest {

    private static final ItemKey MACHINE = item("gregtech:steam_purifier");
    private static final ItemKey PLATE = item("gregtech:plate_steel");
    private static final ItemKey CIRCUIT = item("gregtech:circuit_basic");
    private static final ItemKey BOARD = item("gregtech:board");
    private static final ItemKey WIRE = item("gregtech:wire_tin");
    private static final ItemKey INGOT = item("gregtech:ingot_steel");
    private static final ItemKey TIN_INGOT = item("gregtech:ingot_tin");
    private static final ItemKey DUST = item("gregtech:dust_redstone");
    private static final ItemKey ORE = item("gregtech:ore_iron");
    private static final ItemKey HAMMER = item("gregtech:hammer");
    private static final ItemKey STEAM = new ItemKey("steam", 0, true, "");

    private final FakeSource source = new FakeSource();

    public BreakdownPlannerTest() {
        source.base.addAll(Arrays.asList(INGOT, TIN_INGOT, DUST));
        source.ores.add(ORE);
        source.add(
            MACHINE,
            craft(1, slot(PLATE, 2), slot(CIRCUIT, 1), tool(HAMMER)),
            machine("Assembler", 30, 1, slot(PLATE, 1), slot(CIRCUIT, 1), slot(STEAM, 1000)));
        source.add(
            PLATE,
            craft(1, slot(INGOT, 2), tool(HAMMER)),
            machine("Ore Smasher", 2, 1, slot(ORE, 1)),
            machine("Forge Hammer", 16, 2, slot(INGOT, 3)),
            machine("Bender", 24, 1, slot(INGOT, 1)));
        source.add(CIRCUIT, machine("Circuit Assembler", 16, 1, slot(BOARD, 1), slot(WIRE, 2)));
        source.add(BOARD, craft(1, slot(DUST, 2)));
        source.add(WIRE, machine("Wiremill", 4, 2, slot(TIN_INGOT, 1)));
        source.add(HAMMER, craft(1, slot(INGOT, 6)));
    }

    @Test
    public void craftingTableFirstBreaksEverythingDownToBaseMaterials() {
        RecipeTree tree = plan(BreakdownStrategy.CRAFTING_TABLE);

        assertEquals(
            "Shaped Crafting",
            tree.getPlan()
                .getName());
        Map<ItemKey, Long> totals = totals(tree, 1);
        assertEquals(Long.valueOf(4), totals.get(INGOT));
        assertEquals(Long.valueOf(1), totals.get(TIN_INGOT));
        assertEquals(Long.valueOf(2), totals.get(DUST));
        assertEquals(Long.valueOf(1), totals.get(HAMMER));
        assertEquals(4, totals.size());
    }

    @Test
    public void quantitiesScaleWithWholeRecipeBatches() {
        Map<ItemKey, Long> totals = totals(plan(BreakdownStrategy.CRAFTING_TABLE), 3);

        // 3 machines: 6 plates x 2 ingots; 3 circuits: 3 boards x 2 dust, 6 wires at 2 per tin ingot.
        assertEquals(Long.valueOf(12), totals.get(INGOT));
        assertEquals(Long.valueOf(6), totals.get(DUST));
        assertEquals(Long.valueOf(3), totals.get(TIN_INGOT));
        assertEquals(Long.valueOf(1), totals.get(HAMMER));
    }

    @Test
    public void lowestVoltagePrefersCheapMachinesButNeverOreRecipes() {
        RecipeTree tree = plan(BreakdownStrategy.LOW_VOLTAGE);

        assertEquals(
            "Assembler",
            tree.getPlan()
                .getName());
        assertEquals(
            "Forge Hammer",
            tree.getChildren()
                .get(0)
                .getPlan()
                .getName());
        Map<ItemKey, Long> totals = totals(tree, 1);
        assertEquals(Long.valueOf(3), totals.get(INGOT));
        assertEquals(Long.valueOf(1000), totals.get(STEAM));
        assertFalse(totals.containsKey(ORE));
    }

    @Test
    public void cheapestPicksTheRecipeNeedingTheFewestBaseMaterials() {
        RecipeTree tree = plan(BreakdownStrategy.CHEAPEST);

        // Hand crafting (2 plates, no steam) beats the assembler once 1000 mB of steam is costed in.
        assertEquals(
            "Shaped Crafting",
            tree.getPlan()
                .getName());
        assertEquals(
            "Bender",
            tree.getChildren()
                .get(0)
                .getPlan()
                .getName());
        assertEquals(Long.valueOf(2), totals(tree, 1).get(INGOT));
    }

    @Test
    public void neverLooksUpBaseMaterialsFluidsOresOrReusableTools() {
        plan(BreakdownStrategy.CRAFTING_TABLE);
        plan(BreakdownStrategy.LOW_VOLTAGE);
        plan(BreakdownStrategy.CHEAPEST);

        for (ItemKey never : Arrays.asList(INGOT, TIN_INGOT, DUST, ORE, STEAM, HAMMER))
            assertFalse(never.getRegistryName(), source.lookedUp.contains(never));
    }

    @Test
    public void materialsWithOnlyOreRecipesStayUnexpanded() {
        ItemKey crushedPlate = item("gregtech:plate_from_ore");
        source.add(crushedPlate, machine("Ore Smasher", 2, 1, slot(ORE, 1)));

        BreakdownPlanner planner = planner(crushedPlate, BreakdownStrategy.CRAFTING_TABLE, 1, 4096);

        assertNull(planner.result());
        assertTrue(
            planner.stopReasons()
                .get(crushedPlate)
                .startsWith("every recipe needs ore"));
        assertFalse(
            planner.stopReasons()
                .containsKey(INGOT));
    }

    @Test
    public void recipesThatUseToolsAreStillBrokenDown() {
        RecipeTree tree = plan(BreakdownStrategy.CRAFTING_TABLE);

        // The plate's only crafting-table recipe needs a hammer; the plate is still broken down to ingots.
        assertNotNull(
            tree.getChildren()
                .get(0));
        assertTrue(
            planner(MACHINE, BreakdownStrategy.CRAFTING_TABLE, 1, 4096).stopReasons()
                .isEmpty());
    }

    @Test
    public void baseMaterialsAndUncraftableItemsAreNotBrokenDown() {
        assertNull(planner(INGOT, BreakdownStrategy.CRAFTING_TABLE, 1, 4096).result());
        assertNull(planner(item("minecraft:log"), BreakdownStrategy.CRAFTING_TABLE, 1, 4096).result());
    }

    @Test
    public void recipesThatLoopBackStopAtTheRepeatedMaterial() {
        ItemKey a = item("test:a"), b = item("test:b");
        source.add(a, craft(1, slot(b, 1), slot(DUST, 1)));
        source.add(b, craft(1, slot(a, 1)));

        RecipeTree tree = planner(a, BreakdownStrategy.CRAFTING_TABLE, 1, 4096).result();

        assertNotNull(tree);
        assertNull(
            tree.getChildren()
                .get(0));
    }

    @Test
    public void recipesConsumingAnAncestorOfTheRowAreNotUsed() {
        BreakdownPlanner planner = new BreakdownPlanner(
            PLATE,
            BreakdownStrategy.CRAFTING_TABLE,
            2,
            4096,
            Collections.singletonList(INGOT));
        run(planner);

        assertNull(planner.result());
    }

    @Test
    public void depthLimitMatchesTheServer() {
        BreakdownPlanner planner = planner(MACHINE, BreakdownStrategy.CRAFTING_TABLE, 15, 4096);
        RecipeTree tree = planner.result();

        assertEquals(1, tree.depth());
        assertTrue(planner.isTruncated());
        assertNull(planner(MACHINE, BreakdownStrategy.CRAFTING_TABLE, 16, 4096).result());
    }

    @Test
    public void rowBudgetStopsExpansionInsteadOfOverflowingTheCard() {
        BreakdownPlanner small = planner(MACHINE, BreakdownStrategy.CRAFTING_TABLE, 1, 2);

        assertNull(small.result());
        assertTrue(small.isTruncated());
        BreakdownPlanner enough = planner(MACHINE, BreakdownStrategy.CRAFTING_TABLE, 1, 5);
        assertTrue(
            enough.result()
                .nodeCount() <= 5);
    }

    @Test
    public void planningRunsInSlicesAndFinishesWithTheSameResult() {
        BreakdownPlanner sliced = new BreakdownPlanner(MACHINE, BreakdownStrategy.CRAFTING_TABLE, 1, 4096);
        int slices = 0;
        while (!sliced.step(source, 0)) slices++;

        assertTrue(slices > 1);
        assertEquals(totals(plan(BreakdownStrategy.CRAFTING_TABLE), 1), totals(sliced.result(), 1));
    }

    @Test
    public void failingRecipeLookupsLeaveTheMaterialUnexpanded() {
        source.failing.add(CIRCUIT);

        RecipeTree tree = plan(BreakdownStrategy.CRAFTING_TABLE);

        assertNull(
            tree.getChildren()
                .get(1));
        assertEquals(Long.valueOf(1), totals(tree, 1).get(CIRCUIT));
    }

    private RecipeTree plan(BreakdownStrategy strategy) {
        RecipeTree tree = planner(MACHINE, strategy, 1, 4096).result();
        assertNotNull(tree);
        return tree;
    }

    private BreakdownPlanner planner(ItemKey root, BreakdownStrategy strategy, int level, int budget) {
        BreakdownPlanner planner = new BreakdownPlanner(root, strategy, level, budget);
        run(planner);
        return planner;
    }

    private void run(BreakdownPlanner planner) {
        assertTrue(planner.step(source, Long.MAX_VALUE));
    }

    /** Applies the tree the way the server does and sums the leaves, counting reusable tools once. */
    private static Map<ItemKey, Long> totals(RecipeTree tree, int quantity) {
        ItemRequirement root = new ItemRequirement(UUID.randomUUID(), MACHINE, quantity, false);
        root.expandTree(tree);
        Map<ItemKey, Long> totals = new LinkedHashMap<ItemKey, Long>();
        collect(root, totals);
        return totals;
    }

    private static void collect(ItemRequirement row, Map<ItemKey, Long> totals) {
        if (!row.getChildren()
            .isEmpty()) {
            for (ItemRequirement child : row.getChildren()) collect(child, totals);
            return;
        }
        Long previous = totals.get(row.getItem());
        long amount = row.getQuantity();
        totals.put(
            row.getItem(),
            previous == null ? amount
                : row.isReusable() ? Math.max(previous.longValue(), amount) : previous.longValue() + amount);
    }

    private static ItemKey item(String name) {
        return new ItemKey(name, 0);
    }

    private static List<RecipeIngredient> slot(ItemKey material, int amount) {
        return Collections.singletonList(new RecipeIngredient(material, amount, false));
    }

    private static List<RecipeIngredient> tool(ItemKey material) {
        return Collections.singletonList(new RecipeIngredient(material, 1, true));
    }

    @SafeVarargs
    private static RecipeCandidate craft(int output, List<RecipeIngredient>... slots) {
        return new RecipeCandidate(
            "Shaped Crafting",
            RecipeCandidate.Kind.CRAFTING_TABLE,
            -1,
            output,
            Arrays.asList(slots));
    }

    @SafeVarargs
    private static RecipeCandidate machine(String name, long eu, int output, List<RecipeIngredient>... slots) {
        return new RecipeCandidate(name, RecipeCandidate.Kind.GT_MACHINE, eu, output, Arrays.asList(slots));
    }

    private static final class FakeSource implements RecipeSource {

        final Map<ItemKey, List<RecipeCandidate>> recipes = new HashMap<ItemKey, List<RecipeCandidate>>();
        final Set<ItemKey> base = new HashSet<ItemKey>();
        final Set<ItemKey> ores = new HashSet<ItemKey>();
        final Set<ItemKey> failing = new HashSet<ItemKey>();
        final List<ItemKey> lookedUp = new ArrayList<ItemKey>();

        void add(ItemKey material, RecipeCandidate... candidates) {
            recipes.put(material, Arrays.asList(candidates));
        }

        @Override
        public List<RecipeCandidate> recipes(ItemKey material) {
            lookedUp.add(material);
            if (failing.contains(material)) throw new IllegalStateException("NEI handler crashed");
            List<RecipeCandidate> found = recipes.get(material);
            return found == null ? Collections.<RecipeCandidate>emptyList() : found;
        }

        @Override
        public boolean isBaseMaterial(ItemKey material) {
            return base.contains(material);
        }

        @Override
        public boolean isOre(ItemKey material) {
            return ores.contains(material);
        }
    }
}
