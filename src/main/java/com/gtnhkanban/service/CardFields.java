package com.gtnhkanban.service;

import java.util.UUID;

import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.Priority;

/** The editable fields of a card, as submitted from the card screen. */
public final class CardFields {

    final String title;
    final String description;
    final UUID columnId;
    final UUID typeId;
    final Priority priority;
    final ItemKey icon;

    /**
     * @param columnId null for the first column (new cards) or the current column (edits)
     * @param typeId   null for no type
     * @param icon     null for no icon
     */
    public CardFields(String title, String description, UUID columnId, UUID typeId, Priority priority, ItemKey icon) {
        this.title = title;
        this.description = description;
        this.columnId = columnId;
        this.typeId = typeId;
        this.priority = priority == null ? Priority.NONE : priority;
        this.icon = icon;
    }

    public static CardFields of(String title, String description) {
        return new CardFields(title, description, null, null, Priority.NONE, null);
    }
}
