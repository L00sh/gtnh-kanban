package com.gtnhkanban.client;

/** The colors offered for card types. */
final class TypeColors {

    static final int[] PALETTE = { 0xE04848, 0xFF9800, 0xFFEB3B, 0x8BC34A, 0x4CAF50, 0x009688, 0x00BCD4, 0x42A5F5,
        0x3F51B5, 0xAB47BC, 0xE91E63, 0x795548, 0x9E9E9E, 0x607D8B, 0xFFFFFF, 0x212121 };

    private TypeColors() {}

    /** The next ({@code step} 1) or previous ({@code step} -1) palette color; unknown colors restart the palette. */
    static int cycle(int color, int step) {
        int index = -1;
        for (int i = 0; i < PALETTE.length; i++) if (PALETTE[i] == color) index = i;
        if (index < 0) return PALETTE[0];
        return PALETTE[(index + step + PALETTE.length) % PALETTE.length];
    }
}
