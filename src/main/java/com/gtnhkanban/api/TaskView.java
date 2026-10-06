package com.gtnhkanban.api;

import java.util.Objects;
import java.util.UUID;

public final class TaskView {

    private final UUID id;
    private final String text;
    private final boolean done;

    public TaskView(UUID id, String text, boolean done) {
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

    public TaskView withDone(boolean value) {
        return new TaskView(id, text, value);
    }
}
