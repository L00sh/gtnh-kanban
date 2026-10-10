package com.gtnhkanban.model;

/** How one card can point at another. */
public enum CardLink {

    /** The other card must be done before this one can be: this card cannot move to the done column until it is. */
    DEPENDS_ON,
    /** The other card is holding this one up. Informational only; it never stops a move. */
    BLOCKED_BY;

    public static CardLink fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : null;
    }
}
