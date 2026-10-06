package com.gtnhkanban.api;

import java.util.Objects;
import java.util.UUID;

public final class ProjectSummary {

    private final UUID id;
    private final String name;
    private final boolean actorIsOwner;

    public ProjectSummary(UUID id, String name, boolean actorIsOwner) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.actorIsOwner = actorIsOwner;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isActorIsOwner() {
        return actorIsOwner;
    }
}
