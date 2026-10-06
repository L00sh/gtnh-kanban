package com.gtnhkanban.client;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class GuiPaginationTest {

    @Test
    public void emptyListHasOnePageAndNoItems() {
        assertEquals(1, PagedList.pageCount(0, 3));
        assertEquals(Collections.emptyList(), PagedList.pageItems(Collections.<String>emptyList(), 0, 3));
    }

    @Test
    public void oneItemAndExactPageRemainOnOnePage() {
        List<String> items = Arrays.asList("A", "B", "C");
        assertEquals(1, PagedList.pageCount(1, 3));
        assertEquals(1, PagedList.pageCount(items.size(), 3));
        assertEquals(items, PagedList.pageItems(items, 0, 3));
    }

    @Test
    public void exactTwoPagesPreserveOrderWithoutDuplicates() {
        List<String> items = Arrays.asList("A", "B", "C", "D");
        assertEquals(2, PagedList.pageCount(items.size(), 2));
        assertEquals(Arrays.asList("A", "B"), PagedList.pageItems(items, 0, 2));
        assertEquals(Arrays.asList("C", "D"), PagedList.pageItems(items, 1, 2));
    }

    @Test
    public void partialLastPageContainsRemainingItemsOnly() {
        List<String> items = Arrays.asList("A", "B", "C", "D", "E");
        assertEquals(3, PagedList.pageCount(items.size(), 2));
        assertEquals(Arrays.asList("E"), PagedList.pageItems(items, 2, 2));
        assertEquals(Collections.emptyList(), PagedList.pageItems(items, 3, 2));
        assertEquals(Collections.emptyList(), PagedList.pageItems(items, -1, 2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void pageCountRejectsNonpositivePageSize() {
        PagedList.pageCount(1, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void pageItemsRejectsNonpositivePageSize() {
        PagedList.pageItems(Arrays.asList("A"), 0, -1);
    }
}
