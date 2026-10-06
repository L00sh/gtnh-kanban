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

        Total(ItemKey material, long amount, boolean reusable) {
            this.material = material;
            this.amount = amount;
            this.reusable = reusable;
        }
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
        Map<ItemKey, Long> consumed = new LinkedHashMap<ItemKey, Long>();
        Map<ItemKey, Long> reusable = new LinkedHashMap<ItemKey, Long>();
        for (RequirementView root : roots) collect(root, consumed, reusable);
        List<Total> totals = new ArrayList<Total>();
        for (Map.Entry<ItemKey, Long> entry : (wantReusable ? reusable : consumed).entrySet())
            totals.add(new Total(entry.getKey(), entry.getValue(), wantReusable));
        return totals;
    }

    /** True when any row on the card has been broken down, so totals differ from the rows themselves. */
    static boolean hasBreakdown(List<RequirementView> roots) {
        for (RequirementView root : roots) if (!root.getChildren()
            .isEmpty()) return true;
        return false;
    }

    private static void collect(RequirementView row, Map<ItemKey, Long> consumed, Map<ItemKey, Long> reusable) {
        if (row.isComplete()) return;
        if (!row.getChildren()
            .isEmpty()) {
            for (RequirementView child : row.getChildren()) collect(child, consumed, reusable);
            return;
        }
        Long previous = (row.isReusable() ? reusable : consumed).get(row.getItem());
        long amount = row.getQuantity();
        if (row.isReusable()) {
            reusable.put(row.getItem(), previous == null ? amount : Math.max(previous.longValue(), amount));
        } else consumed.put(row.getItem(), previous == null ? amount : previous.longValue() + amount);
    }
}
