package com.gtnhkanban.api;

import java.util.Objects;
import java.util.UUID;

public final class CommentView {

    private final UUID id;
    private final UUID authorId;
    private final String authorName;
    private final long createdAt;
    private final String text;

    public CommentView(UUID id, UUID authorId, String authorName, long createdAt, String text) {
        this.id = Objects.requireNonNull(id, "id");
        this.authorId = authorId;
        this.authorName = authorName == null ? "" : authorName;
        this.createdAt = createdAt;
        this.text = Objects.requireNonNull(text, "text");
    }

    public UUID getId() {
        return id;
    }

    /** Null when unknown. */
    public UUID getAuthorId() {
        return authorId;
    }

    /** Empty when unknown. */
    public String getAuthorName() {
        return authorName;
    }

    /** Epoch milliseconds, or 0 when unknown. */
    public long getCreatedAt() {
        return createdAt;
    }

    public String getText() {
        return text;
    }
}
