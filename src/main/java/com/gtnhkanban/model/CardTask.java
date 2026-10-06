package com.gtnhkanban.model;

import java.util.Objects;
import java.util.UUID;

/** A plain checklist task such as "do x", alongside the card's item requirements. */
public final class CardTask {

    private final UUID id;
    private final String text;
    private boolean done;

    public CardTask(UUID id, String text, boolean done) {
        this.id = Objects.requireNonNull(id, "id");
        this.text = Objects.requireNonNull(text, "text");
        this.done = done;
    }

    public UUID getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }
}
