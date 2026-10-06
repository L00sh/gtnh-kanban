package com.gtnhkanban.model;

import java.util.Objects;

/** A concrete ingredient and its amount for one recipe batch. */
public final class RecipeIngredient {

    private final ItemKey material;
    private final int amount;
    private final boolean reusable;

    public RecipeIngredient(ItemKey material, int amount, boolean reusable) {
        this.material = Objects.requireNonNull(material, "material");
        if (amount < 1) throw new IllegalArgumentException("Ingredient amount must be positive.");
        this.amount = amount;
        this.reusable = reusable;
    }

    public ItemKey getMaterial() {
        return material;
    }

    public int getAmount() {
        return amount;
    }

    public boolean isReusable() {
        return reusable;
    }
}
