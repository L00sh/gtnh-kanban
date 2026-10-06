package com.gtnhkanban.api;

import java.util.Objects;
import java.util.UUID;

public final class MemberSummary {

    private final UUID playerId;
    private final String displayName;
    private final boolean owner;

    public MemberSummary(UUID playerId, String displayName, boolean owner) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.owner = owner;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isOwner() {
        return owner;
    }
}
