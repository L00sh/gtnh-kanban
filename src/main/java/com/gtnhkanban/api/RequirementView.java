package com.gtnhkanban.api;

import java.util.Objects;
import java.util.UUID;

import com.gtnhkanban.model.ItemKey;

public final class RequirementView {

    private final UUID id;
    private final ItemKey item;
    private final int quantity;
    private final boolean complete;

    public RequirementView(UUID id, ItemKey item, int quantity, boolean complete) {
        this.id = Objects.requireNonNull(id, "id");
        this.item = Objects.requireNonNull(item, "item");
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
}
