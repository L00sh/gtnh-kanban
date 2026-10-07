package com.gtnhkanban.planner;

/**
 * Which recipe a breakdown prefers when a material has several: a recipe type as the recipe viewer names it (such as
 * "Assembler" or "Shapeless Crafting"). Materials with no recipe of that type fall back to the usual order: crafting
 * table, then GregTech machines from the lowest voltage up, then furnace, then anything else.
 */
public final class RecipePreference {

    /** No particular type: crafting-table recipes first. */
    public static final RecipePreference CRAFTING_TABLE = new RecipePreference(null);

    private final String recipeType;

    private RecipePreference(String recipeType) {
        this.recipeType = recipeType;
    }

    /** @param recipeType a recipe viewer's recipe type name; null or blank for {@link #CRAFTING_TABLE} */
    public static RecipePreference of(String recipeType) {
        return recipeType == null || recipeType.trim()
            .isEmpty() ? CRAFTING_TABLE : new RecipePreference(recipeType.trim());
    }

    /** Null for the default crafting-table-first order. */
    public String getRecipeType() {
        return recipeType;
    }

    public boolean prefers(RecipeCandidate candidate) {
        return recipeType != null && recipeType.equalsIgnoreCase(candidate.getName());
    }

    public String label() {
        return recipeType == null ? "Crafting table" : recipeType;
    }
}
