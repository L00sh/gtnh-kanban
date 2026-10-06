package com.gtnhkanban.client;

import java.text.SimpleDateFormat;
import java.util.Date;

/** Card and comment time labels: a short age on the card, the full date and time on hover. */
final class TimeText {

    private TimeText() {}

    /** "just now", "5m", "3h", "2d", "6w", "1y"; empty when the time is unknown (0). */
    static String ago(long createdAt, long now) {
        if (createdAt <= 0) return "";
        long seconds = Math.max(0, (now - createdAt) / 1000);
        if (seconds < 60) return "just now";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        if (days < 14) return days + "d ago";
        if (days < 365) return days / 7 + "w ago";
        return days / 365 + "y ago";
    }

    /** Local date and time, e.g. "2026-10-06 17:42"; "unknown" when 0. */
    static String full(long time) {
        return time <= 0 ? "unknown" : new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(time));
    }
}
