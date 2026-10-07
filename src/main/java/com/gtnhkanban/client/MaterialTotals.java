package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gtnhkanban.api.RequirementView;
import com.gtnhkanban.model.ItemKey;

/**
 * What a card still needs, from the leaves of every unfinished branch. Consumed materials are summed per material.
 * Tools and other reusable inputs are not materials: they are listed separately, once each, at the largest amount any
 * recipe needs. A row marked complete needs nothing below it.
 */
final class MaterialTotals {

    static final class Total {

        final ItemKey material;
        final long amount;
        final boolean reusable;
        /**
         * What in this breakdown uses the material, and how much each needs: the parent rows that consume it, keyed by
         * their item. A null key means the material is listed directly on the card.
         */
        final Map<ItemKey, Long> usedFor;

        Total(ItemKey material, long amount, boolean reusable) {
            this(material, amount, reusable, new LinkedHashMap<ItemKey, Long>());
        }

        Total(ItemKey material, long amount, boolean reusable, Map<ItemKey, Long> usedFor) {
            this.material = material;
            this.amount = amount;
            this.reusable = reusable;
            this.usedFor = usedFor;
        }
    }

    /** Running sums for one material while walking the tree. */
    private static final class Tally {

        long amount;
        final Map<ItemKey, Long> usedFor = new LinkedHashMap<ItemKey, Long>();
    }

    private MaterialTotals() {}

    /** Materials the recipes consume. */
    static List<Total> of(List<RequirementView> roots) {
        return totals(roots, false);
    }

    /** Tools and other inputs the recipes need but do not consume. */
    static List<Total> tools(List<RequirementView> roots) {
        return totals(roots, true);
    }

    private static List<Total> totals(List<RequirementView> roots, boolean wantReusable) {
        Map<ItemKey, Tally> consumed = new LinkedHashMap<ItemKey, Tally>();
        Map<ItemKey, Tally> reusable = new LinkedHashMap<ItemKey, Tally>();
        for (RequirementView root : roots) collect(root, null, consumed, reusable);
        List<Total> totals = new ArrayList<Total>();
        for (Map.Entry<ItemKey, Tally> entry : (wantReusable ? reusable : consumed).entrySet())
            totals.add(new Total(entry.getKey(), entry.getValue().amount, wantReusable, entry.getValue().usedFor));
        return totals;
    }

    /** True when any row on the card has been broken down, so totals differ from the rows themselves. */
    static boolean hasBreakdown(List<RequirementView> roots) {
        for (RequirementView root : roots) if (!root.getChildren()
            .isEmpty()) return true;
        return false;
    }

    /** Consumed amounts add up; a reusable input is needed once, at the largest amount any one use asks for. */
    private static void collect(RequirementView row, ItemKey parent, Map<ItemKey, Tally> consumed,
        Map<ItemKey, Tally> reusable) {
        if (row.isComplete()) return;
        if (!row.getChildren()
            .isEmpty()) {
            for (RequirementView child : row.getChildren()) collect(child, row.getItem(), consumed, reusable);
            return;
        }
        Map<ItemKey, Tally> target = row.isReusable() ? reusable : consumed;
        Tally tally = target.get(row.getItem());
        if (tally == null) {
            tally = new Tally();
            target.put(row.getItem(), tally);
        }
        long amount = row.getQuantity();
        Long previous = tally.usedFor.get(parent);
        if (row.isReusable()) {
            tally.amount = Math.max(tally.amount, amount);
            tally.usedFor.put(parent, previous == null ? amount : Math.max(previous.longValue(), amount));
        } else {
            tally.amount += amount;
            tally.usedFor.put(parent, previous == null ? amount : previous.longValue() + amount);
        }
    }
}
