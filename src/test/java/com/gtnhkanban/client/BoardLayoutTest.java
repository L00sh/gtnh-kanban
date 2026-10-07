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
    public void dropIndexSwitchesHalfwayDownEachCard() {
        // Cards 52 tall with a 3px gap: pitch 55, first card at y=50.
        assertEquals(0, BoardLayout.dropIndex(50, 50, 55, 0, 3));
        assertEquals(0, BoardLayout.dropIndex(76, 50, 55, 0, 3));
        assertEquals(1, BoardLayout.dropIndex(78, 50, 55, 0, 3));
        assertEquals(3, BoardLayout.dropIndex(500, 50, 55, 0, 3));
        assertEquals(0, BoardLayout.dropIndex(10, 50, 55, 0, 3));
        assertEquals("Scrolled columns count from the first shown card", 3, BoardLayout.dropIndex(78, 50, 55, 2, 9));
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
