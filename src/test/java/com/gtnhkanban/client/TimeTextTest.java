package com.gtnhkanban.client;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TimeTextTest {

    private static final long NOW = 1_780_000_000_000L;
    private static final long MINUTE = 60_000L, HOUR = 60 * MINUTE, DAY = 24 * HOUR;

    @Test
    public void agesReadNaturally() {
        assertEquals("just now", TimeText.ago(NOW - 5_000, NOW));
        assertEquals("5m ago", TimeText.ago(NOW - 5 * MINUTE, NOW));
        assertEquals("3h ago", TimeText.ago(NOW - 3 * HOUR, NOW));
        assertEquals("2d ago", TimeText.ago(NOW - 2 * DAY, NOW));
        assertEquals("6w ago", TimeText.ago(NOW - 42 * DAY, NOW));
        assertEquals("1y ago", TimeText.ago(NOW - 400 * DAY, NOW));
    }

    @Test
    public void unknownTimesStayBlank() {
        assertEquals("", TimeText.ago(0, NOW));
        assertEquals("unknown", TimeText.full(0));
    }

    @Test
    public void cyclingColorsWrapsAround() {
        int last = TypeColors.PALETTE[TypeColors.PALETTE.length - 1];
        assertEquals(TypeColors.PALETTE[0], TypeColors.cycle(last, 1));
        assertEquals(last, TypeColors.cycle(TypeColors.PALETTE[0], -1));
        assertEquals("Unknown colors restart the palette", TypeColors.PALETTE[0], TypeColors.cycle(0x123456, 1));
    }
}
