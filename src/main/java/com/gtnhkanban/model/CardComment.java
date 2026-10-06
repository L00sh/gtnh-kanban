package com.gtnhkanban.model;

import java.util.Objects;
import java.util.UUID;

public final class CardComment {

    private final UUID id;
    private final UUID authorId;
    private final long createdAt;
    private final String text;

    /** @param createdAt epoch milliseconds, or 0 when unknown */
    public CardComment(UUID id, UUID authorId, long createdAt, String text) {
        this.id = Objects.requireNonNull(id, "id");
        this.authorId = authorId;
        this.createdAt = createdAt;
        this.text = Objects.requireNonNull(text, "text");
    }

    public UUID getId() {
        return id;
    }

    /** Null when the author is unknown. */
    public UUID getAuthorId() {
        return authorId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getText() {
        return text;
    }
}
