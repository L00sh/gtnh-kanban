package com.gtnhkanban.planner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.RecipeIngredient;
import com.gtnhkanban.model.RecipePlan;
import com.gtnhkanban.model.RecipeTree;

/**
 * Breaks a material down through its recipes until every branch ends at a base material (ingot, nugget, gem, dust,
 * fluid and similar), a reusable input, or something with no usable recipe. Recipes that consume ores are never used.
 *
 * <p>
 * Recipe lookups can be slow, so planning runs in {@link #step} slices: call it repeatedly until it returns true, then
 * read {@link #result()}. Each material is looked up once; the tree is assembled from those lookups at the end.
 */
public final class BreakdownPlanner {

    /** Deepest checklist level the server accepts; root checklist rows are level 1. */
    public static final int MAX_LEVEL = 16;
    static final int MAX_LOOKUPS = 3000;
    /** "Cheapest" compares every recipe this many levels down before settling on crafting-table order. */
    static final int CHEAPEST_EXPLORE_LEVELS = 6;
    static final int CHEAPEST_EXPLORE_LOOKUPS = 500;
    private static final double FLUID_UNIT_COST = 1.0 / 144;

    private final ItemKey root;
    private final BreakdownStrategy strategy;
    private final int rootLevel;
    private final int nodeBudget;
    private final List<ItemKey> ancestors;
    private final Map<ItemKey, Entry> entries = new HashMap<ItemKey, Entry>();
    private final ArrayDeque<Pending> queue = new ArrayDeque<Pending>();
    private final Map<ItemKey, Double> costs = new HashMap<ItemKey, Double>();
    private final Map<ItemKey, String> stops = new LinkedHashMap<ItemKey, String>();
    private boolean started, finished, truncated;
    private int lookups;
    private RecipeTree result;

    /**
     * @param rootLevel  checklist level of the row being broken down (1 for a top-level item)
     * @param nodeBudget how many rows the breakdown may add to the card
     */
    public BreakdownPlanner(ItemKey root, BreakdownStrategy strategy, int rootLevel, int nodeBudget) {
        this(root, strategy, rootLevel, nodeBudget, Collections.<ItemKey>emptyList());
    }

    /** @param ancestors materials of the rows above {@code root}, which no recipe in the breakdown may consume */
    public BreakdownPlanner(ItemKey root, BreakdownStrategy strategy, int rootLevel, int nodeBudget,
        List<ItemKey> ancestors) {
        this.root = root;
        this.strategy = strategy;
        this.rootLevel = rootLevel;
        this.nodeBudget = nodeBudget;
        this.ancestors = new ArrayList<ItemKey>(ancestors);
    }

    private static final class Pending {

        final ItemKey material;
        final int level;
        final boolean explore;

        Pending(ItemKey material, int level, boolean explore) {
            this.material = material;
            this.level = level;
            this.explore = explore;
        }
    }

    private static final class Option {

        final RecipeCandidate candidate;
        /** Acceptable alternatives per slot, never empty. */
        final List<List<RecipeIngredient>> slots;

        Option(RecipeCandidate candidate, List<List<RecipeIngredient>> slots) {
            this.candidate = candidate;
            this.slots = slots;
        }
    }

    private static final class Entry {

        final List<Option> options;
        boolean explored;
        RecipePlan chosen;

        Entry(List<Option> options) {
            this.options = options;
        }
    }

    /**
     * Does planning work until {@code deadlineNanos} (at least one lookup per call).
     *
     * @return true once planning has finished
     */
    public boolean step(RecipeSource source, long deadlineNanos) {
        if (finished) return true;
        if (!started) {
            started = true;
            queue.add(new Pending(root, 0, strategy == BreakdownStrategy.CHEAPEST));
        }
        boolean worked = false;
        while (!queue.isEmpty()) {
            if (worked && System.nanoTime() > deadlineNanos) return false;
            worked |= visit(source, queue.poll());
        }
        choose();
        result = build(root, 0, new HashSet<ItemKey>(ancestors), new int[] { nodeBudget });
        finished = true;
        return true;
    }

    /** The breakdown, or null when the material is a base material or has no usable recipe. */
    public RecipeTree result() {
        if (!finished) throw new IllegalStateException("Planning has not finished.");
        return result;
    }

    /** True when a size, depth or lookup limit left part of the tree unexpanded. */
    public boolean isTruncated() {
        return truncated;
    }

    /**
     * Why materials that are not base materials were left unexpanded (ores, no recipes, rejected recipes, limits).
     * Base materials, fluids and reusable tools are expected leaves and are not listed.
     */
    public Map<ItemKey, String> stopReasons() {
        return Collections.unmodifiableMap(stops);
    }

    /** Number of materials whose recipes have been looked up so far. */
    public int lookups() {
        return lookups;
    }

    private boolean visit(RecipeSource source, Pending pending) {
        ItemKey material = pending.material;
        Entry known = entries.get(material);
        if (known != null) {
            if (pending.explore && !known.explored && !known.options.isEmpty()) {
                known.explored = true;
                enqueueIngredients(source, known, pending.level, true);
            }
            return false;
        }
        if (isLeafMaterial(source, material)) {
            if (source.isOre(material)) stops.put(material, "ore");
            entries.put(material, new Entry(Collections.<Option>emptyList()));
            return false;
        }
        if (lookups >= MAX_LOOKUPS) {
            truncated = true;
            stops.put(material, "recipe lookup limit reached");
            return false;
        }
        lookups++;
        List<RecipeCandidate> candidates;
        try {
            candidates = source.recipes(material);
        } catch (RuntimeException exception) {
            candidates = Collections.emptyList();
        }
        Entry entry = new Entry(rank(options(source, material, candidates)));
        entries.put(material, entry);
        if (entry.options.isEmpty()) {
            String rejected = source.rejectedRecipes(material);
            stops.put(
                material,
                candidates.isEmpty() ? (rejected.isEmpty() ? "no recipes found" : "no usable recipe " + rejected)
                    : "every recipe needs ore or itself (" + candidates.size() + " recipes)");
            return true;
        }
        entry.explored = pending.explore && pending.level < CHEAPEST_EXPLORE_LEVELS
            && lookups <= CHEAPEST_EXPLORE_LOOKUPS;
        enqueueIngredients(source, entry, pending.level, entry.explored);
        return true;
    }

    private void enqueueIngredients(RecipeSource source, Entry entry, int level, boolean explore) {
        // Ingredients sit one level below; only those still above the depth limit can be expanded further.
        boolean tooDeep = rootLevel + level + 2 > MAX_LEVEL;
        List<Option> options = explore ? entry.options : entry.options.subList(0, 1);
        for (Option option : options) for (List<RecipeIngredient> slot : option.slots) {
            RecipeIngredient first = slot.get(0);
            if (first.isReusable()) continue;
            if (!tooDeep) queue.add(new Pending(first.getMaterial(), level + 1, explore));
            else if (!isLeafMaterial(source, first.getMaterial())) truncated = true;
        }
    }

    private static boolean isLeafMaterial(RecipeSource source, ItemKey material) {
        return material.isFluid() || source.isBaseMaterial(material) || source.isOre(material);
    }

    private static List<Option> options(RecipeSource source, ItemKey material, List<RecipeCandidate> candidates) {
        List<Option> options = new ArrayList<Option>();
        for (RecipeCandidate candidate : candidates) {
            if (candidate.getSlots()
                .isEmpty()
                || candidate.getSlots()
                    .size() > 256)
                continue;
            List<List<RecipeIngredient>> slots = new ArrayList<List<RecipeIngredient>>();
            for (List<RecipeIngredient> alternatives : candidate.getSlots()) {
                List<RecipeIngredient> acceptable = new ArrayList<RecipeIngredient>();
                for (RecipeIngredient alternative : alternatives) {
                    ItemKey input = alternative.getMaterial();
                    if (!input.equals(material) && !source.isOre(input)) acceptable.add(alternative);
                }
                if (acceptable.isEmpty()) {
                    slots = null;
                    break;
                }
                slots.add(acceptable);
            }
            if (slots != null) options.add(new Option(candidate, slots));
        }
        return options;
    }

    /** Stable sort, so equally ranked recipes keep the recipe viewer's order. */
    private List<Option> rank(List<Option> options) {
        final BreakdownStrategy order = strategy == BreakdownStrategy.LOW_VOLTAGE ? BreakdownStrategy.LOW_VOLTAGE
            : BreakdownStrategy.CRAFTING_TABLE;
        List<Option> ranked = new ArrayList<Option>(options);
        Collections.sort(ranked, new Comparator<Option>() {

            @Override
            public int compare(Option first, Option second) {
                int byKind = Integer.compare(kindRank(order, first.candidate), kindRank(order, second.candidate));
                if (byKind != 0) return byKind;
                return Long.compare(voltage(first.candidate), voltage(second.candidate));
            }
        });
        return ranked;
    }

    private static int kindRank(BreakdownStrategy order, RecipeCandidate candidate) {
        switch (candidate.getKind()) {
            case CRAFTING_TABLE:
                return order == BreakdownStrategy.LOW_VOLTAGE ? 1 : 0;
            case GT_MACHINE:
                return order == BreakdownStrategy.LOW_VOLTAGE ? 0 : 2;
            case FURNACE:
                return order == BreakdownStrategy.LOW_VOLTAGE ? 2 : 1;
            default:
                return 3;
        }
    }

    private static long voltage(RecipeCandidate candidate) {
        return candidate.getEuPerTick() < 0 ? Long.MAX_VALUE : candidate.getEuPerTick();
    }

    private void choose() {
        for (Map.Entry<ItemKey, Entry> known : entries.entrySet()) {
            Entry entry = known.getValue();
            if (entry.options.isEmpty()) continue;
            try {
                if (strategy == BreakdownStrategy.CHEAPEST && entry.explored) {
                    unitCost(known.getKey(), new HashSet<ItemKey>());
                } else entry.chosen = plan(entry.options.get(0), firstAlternatives(entry.options.get(0)));
            } catch (IllegalArgumentException exception) {
                entry.chosen = null;
            }
        }
    }

    private static int[] firstAlternatives(Option option) {
        return new int[option.slots.size()];
    }

    /** Estimated base-material count per unit of {@code material}; also records the cheapest plan. */
    private double unitCost(ItemKey material, Set<ItemKey> inProgress) {
        Double memo = costs.get(material);
        if (memo != null) return memo.doubleValue();
        double leaf = material.isFluid() ? FLUID_UNIT_COST : 1;
        Entry entry = entries.get(material);
        if (entry == null || entry.options.isEmpty() || !inProgress.add(material)) return leaf;
        List<Option> options = entry.explored ? entry.options : entry.options.subList(0, 1);
        double best = Double.POSITIVE_INFINITY;
        RecipePlan bestPlan = null;
        for (Option option : options) {
            int[] selection = new int[option.slots.size()];
            double total = 0;
            for (int slot = 0; slot < option.slots.size(); slot++) {
                List<RecipeIngredient> alternatives = option.slots.get(slot);
                double slotCost = Double.POSITIVE_INFINITY;
                for (int index = 0; index < alternatives.size(); index++) {
                    RecipeIngredient alternative = alternatives.get(index);
                    // Unlooked-up alternatives would look artificially cheap, so only the first one counts unseen.
                    if (index > 0 && !entries.containsKey(alternative.getMaterial())) continue;
                    double cost = alternative.isReusable() ? 0
                        : alternative.getAmount() * unitCost(alternative.getMaterial(), inProgress);
                    if (cost < slotCost) {
                        slotCost = cost;
                        selection[slot] = index;
                    }
                }
                total += slotCost;
            }
            total /= option.candidate.getOutput();
            if (total < best) {
                try {
                    bestPlan = plan(option, selection);
                    best = total;
                } catch (IllegalArgumentException exception) {
                    // Amounts too large for a checklist; try the next recipe.
                }
            }
        }
        inProgress.remove(material);
        if (bestPlan == null) return leaf;
        entry.chosen = bestPlan;
        costs.put(material, best);
        return best;
    }

    /** Builds a plan from the chosen alternatives, merging repeated materials as a recipe viewer would. */
    private static RecipePlan plan(Option option, int[] selection) {
        Map<ItemKey, RecipeIngredient> consumed = new LinkedHashMap<ItemKey, RecipeIngredient>();
        Map<ItemKey, RecipeIngredient> reusable = new LinkedHashMap<ItemKey, RecipeIngredient>();
        for (int slot = 0; slot < option.slots.size(); slot++) {
            RecipeIngredient input = option.slots.get(slot)
                .get(selection[slot]);
            Map<ItemKey, RecipeIngredient> target = input.isReusable() ? reusable : consumed;
            RecipeIngredient previous = target.get(input.getMaterial());
            long amount = previous == null ? input.getAmount()
                : input.isReusable() ? Math.max(previous.getAmount(), input.getAmount())
                    : (long) previous.getAmount() + input.getAmount();
            if (amount > Integer.MAX_VALUE) throw new IllegalArgumentException("Ingredient quantity is too large.");
            target
                .put(input.getMaterial(), new RecipeIngredient(input.getMaterial(), (int) amount, input.isReusable()));
        }
        List<RecipeIngredient> ingredients = new ArrayList<RecipeIngredient>(consumed.values());
        ingredients.addAll(reusable.values());
        String name = option.candidate.getName();
        return new RecipePlan(
            name.length() > 128 ? name.substring(0, 128) : name,
            option.candidate.getOutput(),
            ingredients);
    }

    private RecipeTree build(ItemKey material, int level, Set<ItemKey> path, int[] budget) {
        Entry entry = entries.get(material);
        if (entry == null || entry.chosen == null) return null;
        RecipePlan plan = entry.chosen;
        // A recipe that consumes one of its own ancestors would loop; stop at this material instead.
        for (RecipeIngredient ingredient : plan.getIngredients()) if (path.contains(ingredient.getMaterial())) {
            stops.put(material, "recipe loops back to a parent material");
            return null;
        }
        if (rootLevel + level + 1 > MAX_LEVEL || budget[0] < plan.getIngredients()
            .size()) {
            truncated = true;
            stops.put(material, "card size or depth limit");
            return null;
        }
        budget[0] -= plan.getIngredients()
            .size();
        path.add(material);
        List<RecipeTree> children = new ArrayList<RecipeTree>();
        for (RecipeIngredient ingredient : plan.getIngredients()) {
            children.add(ingredient.isReusable() ? null : build(ingredient.getMaterial(), level + 1, path, budget));
        }
        path.remove(material);
        return new RecipeTree(plan, children);
    }
}
