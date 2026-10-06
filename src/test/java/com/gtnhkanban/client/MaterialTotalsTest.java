package com.gtnhkanban.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.Test;

import com.gtnhkanban.api.RequirementView;
import com.gtnhkanban.model.ItemKey;

public class MaterialTotalsTest {

    private static final ItemKey INGOT = new ItemKey("gregtech:ingot", 0);
    private static final ItemKey DUST = new ItemKey("gregtech:dust", 0);
    private static final ItemKey HAMMER = new ItemKey("gregtech:hammer", 0);

    @Test
    public void sumsConsumedMaterialsAndListsToolsSeparately() {
        RequirementView plates = row(
            "gregtech:plate",
            2,
            false,
            false,
            leaf(INGOT, 4, false, false),
            leaf(HAMMER, 1, true, false));
        RequirementView board = row("gregtech:board", 1, false, false, leaf(DUST, 2, false, false));
        RequirementView casing = row(
            "gregtech:casing",
            1,
            false,
            false,
            leaf(INGOT, 3, false, false),
            leaf(HAMMER, 1, true, false));

        List<RequirementView> roots = Arrays.asList(row("gregtech:machine", 1, false, false, plates, board), casing);
        List<MaterialTotals.Total> totals = MaterialTotals.of(roots);
        List<MaterialTotals.Total> tools = MaterialTotals.tools(roots);

        assertEquals(2, totals.size());
        assertEquals(INGOT, totals.get(0).material);
        assertEquals(7, totals.get(0).amount);
        assertEquals(DUST, totals.get(1).material);
        assertEquals(2, totals.get(1).amount);
        assertEquals(1, tools.size());
        assertEquals(HAMMER, tools.get(0).material);
        assertEquals(1, tools.get(0).amount);
        assertTrue(tools.get(0).reusable);
    }

    @Test
    public void completedRowsNeedNothingBelowThem() {
        RequirementView donePlates = row("gregtech:plate", 2, false, true, leaf(INGOT, 4, false, false));
        RequirementView doneDust = leaf(DUST, 2, false, true);

        List<MaterialTotals.Total> totals = MaterialTotals
            .of(Collections.singletonList(row("gregtech:machine", 1, false, false, donePlates, doneDust)));

        assertTrue(totals.isEmpty());
    }

    @Test
    public void toolNamesReadAsWords() {
        assertEquals("Hard Hammer", MaterialDisplay.splitWords("HardHammer"));
        assertEquals("File", MaterialDisplay.splitWords("File"));
    }

    @Test
    public void onlyCardsWithABrokenDownRowShowTotals() {
        assertFalse(MaterialTotals.hasBreakdown(Collections.singletonList(leaf(INGOT, 1, false, false))));
        assertTrue(
            MaterialTotals.hasBreakdown(
                Collections.singletonList(row("gregtech:plate", 1, false, false, leaf(INGOT, 1, false, false)))));
    }

    private static RequirementView leaf(ItemKey material, int quantity, boolean reusable, boolean complete) {
        return new RequirementView(
            UUID.randomUUID(),
            material,
            quantity,
            complete,
            1,
            reusable,
            "",
            0,
            0,
            Collections.<RequirementView>emptyList());
    }

    private static RequirementView row(String name, int quantity, boolean reusable, boolean complete,
        RequirementView... children) {
        return new RequirementView(
            UUID.randomUUID(),
            new ItemKey(name, 0),
            quantity,
            complete,
            1,
            reusable,
            "Recipe",
            1,
            0,
            Arrays.asList(children));
    }
}
