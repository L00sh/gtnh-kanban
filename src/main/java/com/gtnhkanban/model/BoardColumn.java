package com.gtnhkanban.model;

import java.util.Objects;
import java.util.UUID;

/** A board column shared by every project on the server. */
public final class BoardColumn {

    private final UUID id;
    private final String name;

    public BoardColumn(UUID id, String name) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
