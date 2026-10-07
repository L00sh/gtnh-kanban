package com.gtnhkanban.client;

/** Board geometry, kept free of Minecraft classes so it can be tested. */
final class BoardLayout {

    private BoardLayout() {}

    /**
     * Column lefts and widths. When every column fits at {@code minWidth} or wider, the columns share the area equally
     * and fill it exactly (the leftover pixels go one each to the first columns). Otherwise they are {@code minWidth}
     * wide from {@code areaLeft} and the board scrolls sideways.
     *
     * @return {@code [lefts, widths]}
     */
    static int[][] columns(int areaLeft, int areaWidth, int count, int gap, int minWidth) {
        int[] lefts = new int[count], widths = new int[count];
        if (count == 0) return new int[][] { lefts, widths };
        int usable = areaWidth - gap * (count - 1);
        boolean fits = usable >= minWidth * count;
        int base = fits ? usable / count : minWidth;
        int extra = fits ? usable % count : 0;
        int x = areaLeft;
        for (int i = 0; i < count; i++) {
            widths[i] = base + (i < extra ? 1 : 0);
            lefts[i] = x;
            x += widths[i] + gap;
        }
        return new int[][] { lefts, widths };
    }

    /** Total width of the columns and the gaps between them. */
    static int totalWidth(int[][] columns, int gap) {
        int[] widths = columns[1];
        int total = 0;
        for (int width : widths) total += width;
        return total + Math.max(0, widths.length - 1) * gap;
    }

    /** Tops of cards of the given heights stacked from 0 with {@code gap} between them. */
    static int[] stack(int[] heights, int gap) {
        int[] tops = new int[heights.length];
        int y = 0;
        for (int i = 0; i < heights.length; i++) {
            tops[i] = y;
            y += heights[i] + gap;
        }
        return tops;
    }

    /** Height of a stack: the bottom of its last card, or 0 when empty. */
    static int stackHeight(int[] tops, int[] heights) {
        return tops.length == 0 ? 0 : tops[tops.length - 1] + heights[heights.length - 1];
    }

    /** The card at {@code y} (measured from the top of the stack), or -1 for a gap or past the end. */
    static int cardAt(int y, int[] tops, int[] heights) {
        for (int i = 0; i < tops.length; i++) if (y >= tops[i] && y < tops[i] + heights[i]) return i;
        return -1;
    }

    /**
     * Where a dragged card would land: the index of the card it would go above, or the card count for the bottom. The
     * boundary between two cards is halfway down each card.
     *
     * @param y measured from the top of the stack
     */
    static int dropIndex(int y, int[] tops, int[] heights) {
        for (int i = 0; i < tops.length; i++) if (y < tops[i] + heights[i] / 2) return i;
        return tops.length;
    }

    /** @return {@code {thumbTop, thumbHeight}} for a scrollbar track; the thumb is at least 8px tall */
    static int[] thumb(int trackTop, int trackHeight, int visible, int total, int scroll) {
        int height = total <= 0 ? trackHeight : Math.max(8, trackHeight * Math.min(visible, total) / total);
        height = Math.min(height, trackHeight);
        int maxScroll = Math.max(0, total - visible);
        int top = maxScroll == 0 ? trackTop : trackTop + (trackHeight - height) * scroll / maxScroll;
        return new int[] { top, height };
    }

    /** The scroll position for a thumb dragged so its top is at {@code thumbTop}. */
    static int scrollForThumb(int thumbTop, int trackTop, int trackHeight, int thumbHeight, int maxScroll) {
        int travel = trackHeight - thumbHeight;
        if (travel <= 0 || maxScroll <= 0) return 0;
        int offset = Math.max(0, Math.min(travel, thumbTop - trackTop));
        return Math.round((float) offset * maxScroll / travel);
    }
}
