package com.gtnhkanban.client;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BoardLayoutTest {

    @Test
    public void columnsShareTheAreaEquallyAndFillItExactly() {
        int[][] columns = BoardLayout.columns(15, 1000, 5, 4, 118);

        // 1000 - 4 gaps of 4 = 984 = 5 x 196 + 4 leftover pixels for the first four columns.
        assertArrayEquals(new int[] { 197, 197, 197, 197, 196 }, columns[1]);
        assertEquals(15, columns[0][0]);
        assertEquals(15 + 1000, columns[0][4] + columns[1][4]);
        assertEquals(1000, BoardLayout.totalWidth(columns, 4));
    }

    @Test
    public void tooManyColumnsKeepTheirMinimumWidthAndOverflow() {
        int[][] columns = BoardLayout.columns(0, 400, 5, 4, 118);

        assertArrayEquals(new int[] { 118, 118, 118, 118, 118 }, columns[1]);
        assertEquals(5 * 118 + 4 * 4, BoardLayout.totalWidth(columns, 4));
    }

    @Test
    public void cardsOfDifferentHeightsStackWithGaps() {
        int[] heights = { 40, 60, 50 };
        int[] tops = BoardLayout.stack(heights, 6);

        assertArrayEquals(new int[] { 0, 46, 112 }, tops);
        assertEquals(162, BoardLayout.stackHeight(tops, heights));
        assertEquals(0, BoardLayout.stackHeight(new int[0], new int[0]));
    }

    @Test
    public void cardAtFindsTheCardUnderThePointerButNotTheGaps() {
        int[] heights = { 40, 60, 50 };
        int[] tops = BoardLayout.stack(heights, 6);

        assertEquals(0, BoardLayout.cardAt(0, tops, heights));
        assertEquals(0, BoardLayout.cardAt(39, tops, heights));
        assertEquals(-1, BoardLayout.cardAt(42, tops, heights));
        assertEquals(1, BoardLayout.cardAt(46, tops, heights));
        assertEquals(2, BoardLayout.cardAt(161, tops, heights));
        assertEquals(-1, BoardLayout.cardAt(162, tops, heights));
        assertEquals(-1, BoardLayout.cardAt(-1, tops, heights));
    }

    @Test
    public void dropIndexSwitchesHalfwayDownEachCard() {
        int[] heights = { 40, 60, 50 };
        int[] tops = BoardLayout.stack(heights, 6);

        assertEquals(0, BoardLayout.dropIndex(-30, tops, heights));
        assertEquals(0, BoardLayout.dropIndex(19, tops, heights));
        assertEquals(1, BoardLayout.dropIndex(20, tops, heights));
        assertEquals("Halfway down the tall second card", 1, BoardLayout.dropIndex(75, tops, heights));
        assertEquals(2, BoardLayout.dropIndex(76, tops, heights));
        assertEquals(3, BoardLayout.dropIndex(137, tops, heights));
        assertEquals(0, BoardLayout.dropIndex(5, new int[0], new int[0]));
    }

    @Test
    public void thumbSizeAndPositionFollowTheScroll() {
        assertArrayEquals(new int[] { 100, 50 }, BoardLayout.thumb(100, 200, 4, 16, 0));
        assertArrayEquals(new int[] { 250, 50 }, BoardLayout.thumb(100, 200, 4, 16, 12));
        assertArrayEquals(
            "Very long columns keep a grabbable thumb",
            new int[] { 100, 8 },
            BoardLayout.thumb(100, 200, 1, 1000, 0));
        assertEquals(0, BoardLayout.scrollForThumb(100, 100, 200, 50, 12));
        assertEquals(12, BoardLayout.scrollForThumb(250, 100, 200, 50, 12));
        assertEquals(6, BoardLayout.scrollForThumb(175, 100, 200, 50, 12));
        assertEquals(12, BoardLayout.scrollForThumb(999, 100, 200, 50, 12));
    }
}
