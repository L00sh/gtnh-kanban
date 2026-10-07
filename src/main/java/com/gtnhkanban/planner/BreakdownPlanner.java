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

    private final ItemKey root;
    private final RecipePreference preference;
    private final int rootLevel;
    private final int nodeBudget;
    private final List<ItemKey> ancestors;
    private final Map<ItemKey, Entry> entries = new HashMap<ItemKey, Entry>();
    private final ArrayDeque<Pending> queue = new ArrayDeque<Pending>();
    private final Map<ItemKey, String> stops = new LinkedHashMap<ItemKey, String>();
    private boolean started, finished, truncated;
    private int lookups;
    private RecipeTree result;

    /**
     * @param rootLevel  checklist level of the row being broken down (1 for a top-level item)
     * @param nodeBudget how many rows the breakdown may add to the card
     */
    public BreakdownPlanner(ItemKey root, RecipePreference preference, int rootLevel, int nodeBudget) {
        this(root, preference, rootLevel, nodeBudget, Collections.<ItemKey>emptyList());
    }

    /** @param ancestors materials of the rows above {@code root}, which no recipe in the breakdown may consume */
    public BreakdownPlanner(ItemKey root, RecipePreference preference, int rootLevel, int nodeBudget,
        List<ItemKey> ancestors) {
        this.root = root;
        this.preference = preference == null ? RecipePreference.CRAFTING_TABLE : preference;
        this.rootLevel = rootLevel;
        this.nodeBudget = nodeBudget;
        this.ancestors = new ArrayList<ItemKey>(ancestors);
    }

    private static final class Pending {

        final ItemKey material;
        final int level;

        Pending(ItemKey material, int level) {
            this.material = material;
            this.level = level;
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
            queue.add(new Pending(root, 0));
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
        if (entries.containsKey(material)) return false;
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
        enqueueIngredients(source, entry, pending.level);
        return true;
    }

    /** Queues the ingredients of the recipe that will be used: the best-ranked one. */
    private void enqueueIngredients(RecipeSource source, Entry entry, int level) {
        // Ingredients sit one level below; only those still above the depth limit can be expanded further.
        boolean tooDeep = rootLevel + level + 2 > MAX_LEVEL;
        for (List<RecipeIngredient> slot : entry.options.get(0).slots) {
            RecipeIngredient first = slot.get(0);
            if (first.isReusable()) continue;
            if (!tooDeep) queue.add(new Pending(first.getMaterial(), level + 1));
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

    /**
     * Stable sort, so equally ranked recipes keep the recipe viewer's order: the preferred recipe type first, then
     * crafting table, GregTech machines from the lowest voltage up, furnace, and anything else.
     */
    private List<Option> rank(List<Option> options) {
        List<Option> ranked = new ArrayList<Option>(options);
        Collections.sort(ranked, new Comparator<Option>() {

            @Override
            public int compare(Option first, Option second) {
                int byKind = Integer.compare(rankOf(first.candidate), rankOf(second.candidate));
                if (byKind != 0) return byKind;
                return Long.compare(voltage(first.candidate), voltage(second.candidate));
            }
        });
        return ranked;
    }

    private int rankOf(RecipeCandidate candidate) {
        if (preference.prefers(candidate)) return 0;
        switch (candidate.getKind()) {
            case CRAFTING_TABLE:
                return 1;
            case GT_MACHINE:
                return 2;
            case FURNACE:
                return 3;
            default:
                return 4;
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
                entry.chosen = plan(entry.options.get(0), firstAlternatives(entry.options.get(0)));
            } catch (IllegalArgumentException exception) {
                entry.chosen = null;
            }
        }
    }

    private static int[] firstAlternatives(Option option) {
        return new int[option.slots.size()];
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
