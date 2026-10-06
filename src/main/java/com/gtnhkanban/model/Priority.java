package com.gtnhkanban.model;

public enum Priority {

    NONE("None"),
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High");

    private final String label;

    Priority(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public Priority next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** Unknown or out-of-range ordinals read as {@link #NONE}. */
    public static Priority fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : NONE;
    }

    public static Priority fromName(String name) {
        for (Priority priority : values()) if (priority.name()
            .equals(name)) return priority;
        return NONE;
    }
}
