package com.gtnhkanban.planner;

import java.util.List;

import com.gtnhkanban.model.ItemKey;

/** Recipe and material knowledge the planner needs; the in-game implementation reads NEI and the ore dictionary. */
public interface RecipeSource {

    /** Every known way to make {@code material}, in the recipe viewer's order. May be slow. */
    List<RecipeCandidate> recipes(ItemKey material);

    /** Materials the breakdown stops at, such as ingots, nuggets, gems and dusts. */
    boolean isBaseMaterial(ItemKey material);

    /** Raw or partly processed ores. Recipes that consume them are never used. */
    boolean isOre(ItemKey material);

    /** Why the recipe viewer's recipes for {@code material} could not be used, for diagnostics. */
    default String rejectedRecipes(ItemKey material) {
        return "";
    }
}
