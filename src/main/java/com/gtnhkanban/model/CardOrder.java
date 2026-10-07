package com.gtnhkanban.model;

import java.util.List;
import java.util.UUID;

/**
 * Card order on a board is the order of the project's card list; each column shows its cards in that order. Shared by
 * the server (saved cards) and the client (instant feedback while dragging) so both place a moved card identically.
 */
public final class CardOrder {

    /** Reads a card's column, so the rule works for saved cards and for client views alike. */
    public interface Columns<T> {

        UUID columnOf(T card);
    }

    private CardOrder() {}

    /**
     * Where a moved card goes in {@code cards} (which must no longer contain it): just before {@code before}, or after
     * the last card already in {@code columnId} when {@code before} is null, or at the end if that column is empty.
     */
    public static <T> int insertionIndex(List<T> cards, Columns<T> columns, UUID columnId, T before) {
        if (before != null) {
            int index = cards.indexOf(before);
            if (index >= 0) return index;
        }
        for (int i = cards.size() - 1; i >= 0; i--) {
            if (columnId.equals(columns.columnOf(cards.get(i)))) return i + 1;
        }
        return cards.size();
    }
}
