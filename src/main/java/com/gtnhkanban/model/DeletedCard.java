package com.gtnhkanban.model;

import java.util.Objects;
import java.util.UUID;

/** A deleted card, kept whole so it can be restored, with who deleted it and when. */
public final class DeletedCard {

    private final KanbanCard card;
    private final long deletedAt;
    private final UUID deletedBy;

    public DeletedCard(KanbanCard card, long deletedAt, UUID deletedBy) {
        this.card = Objects.requireNonNull(card, "card");
        this.deletedAt = deletedAt;
        this.deletedBy = deletedBy;
    }

    public KanbanCard getCard() {
        return card;
    }

    public long getDeletedAt() {
        return deletedAt;
    }

    /** Null when not known. */
    public UUID getDeletedBy() {
        return deletedBy;
    }
}
