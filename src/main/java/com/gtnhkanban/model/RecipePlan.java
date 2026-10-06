package com.gtnhkanban.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A chosen planning breakdown, independent of any runtime NEI recipe handler. */
public final class RecipePlan {

    private final String name;
    private final int outputAmount;
    private final List<RecipeIngredient> ingredients;

    public RecipePlan(String name, int outputAmount, List<RecipeIngredient> ingredients) {
        this.name = Objects.requireNonNull(name, "name");
        if (outputAmount < 1 || ingredients == null || ingredients.isEmpty() || ingredients.size() > 256) {
            throw new IllegalArgumentException("Recipe must have a positive output and 1 to 256 inputs.");
        }
        this.outputAmount = outputAmount;
        this.ingredients = Collections.unmodifiableList(new ArrayList<RecipeIngredient>(ingredients));
    }

    public String getName() {
        return name;
    }

    public int getOutputAmount() {
        return outputAmount;
    }

    public List<RecipeIngredient> getIngredients() {
        return ingredients;
    }
}
