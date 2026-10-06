package com.gtnhkanban.api;

import java.util.Objects;
import java.util.UUID;

import com.gtnhkanban.model.ItemKey;

public final class ProjectSummary {

    private final UUID id;
    private final String name;
    private final boolean actorIsOwner;
    private final ItemKey icon;

    public ProjectSummary(UUID id, String name, boolean actorIsOwner) {
        this(id, name, actorIsOwner, null);
    }

    public ProjectSummary(UUID id, String name, boolean actorIsOwner, ItemKey icon) {
        this.icon = icon;
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

    /** Null when the project has no icon. */
    public ItemKey getIcon() {
        return icon;
    }

    public boolean isActorIsOwner() {
        return actorIsOwner;
    }
}
