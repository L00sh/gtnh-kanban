package com.gtnhkanban.model;

import java.util.Objects;
import java.util.UUID;

public final class ItemRequirement {

    private final UUID id;
    private final ItemKey item;
    private final int quantity;
    private boolean complete;

    public ItemRequirement(UUID id, ItemKey item, int quantity, boolean complete) {
        this.id = Objects.requireNonNull(id, "id");
        this.item = Objects.requireNonNull(item, "item");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.quantity = quantity;
        this.complete = complete;
    }

    public UUID getId() {
        return id;
    }

    public ItemKey getItem() {
        return item;
    }

    public int getQuantity() {
        return quantity;
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }
}
