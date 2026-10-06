package com.gtnhkanban.planner;

/** Which recipe the automatic breakdown prefers when a material has several. */
public enum BreakdownStrategy {

    CRAFTING_TABLE("Crafting table first"),
    LOW_VOLTAGE("Lowest voltage first"),
    CHEAPEST("Cheapest total");

    private final String label;

    BreakdownStrategy(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public BreakdownStrategy next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
