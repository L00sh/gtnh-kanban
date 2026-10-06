package com.gtnhkanban.model;

import java.util.Objects;
import java.util.UUID;

/** A card category such as Bug or Feature, shared by every project on the server. */
public final class CardType {

    private final UUID id;
    private final String name;
    private final int color;

    /** @param color RGB, 0xRRGGBB */
    public CardType(UUID id, String name, int color) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.color = color & 0xFFFFFF;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getColor() {
        return color;
    }
}
