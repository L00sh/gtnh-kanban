package com.gtnhkanban.api;

import java.util.Objects;
import java.util.UUID;

import com.gtnhkanban.model.ItemKey;

public final class ProjectSummary {

    private final UUID id;
    private final String name;
    private final boolean actorIsOwner;
    private final ItemKey icon;
    private final int cardCount, doneCount;

    public ProjectSummary(UUID id, String name, boolean actorIsOwner) {
        this(id, name, actorIsOwner, null);
    }

    public ProjectSummary(UUID id, String name, boolean actorIsOwner, ItemKey icon) {
        this(id, name, actorIsOwner, icon, 0, 0);
    }

    /** @param doneCount cards in the done column */
    public ProjectSummary(UUID id, String name, boolean actorIsOwner, ItemKey icon, int cardCount, int doneCount) {
        this.cardCount = Math.max(0, cardCount);
        this.doneCount = Math.max(0, Math.min(doneCount, this.cardCount));
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

    public int getCardCount() {
        return cardCount;
    }

    public int getDoneCount() {
        return doneCount;
    }

    public boolean isActorIsOwner() {
        return actorIsOwner;
    }
}
