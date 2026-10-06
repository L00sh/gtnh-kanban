package com.gtnhkanban.planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.gtnhkanban.model.RecipeIngredient;

/** One way to make a material, independent of the recipe viewer that reported it. */
public final class RecipeCandidate {

    public enum Kind {
        CRAFTING_TABLE,
        FURNACE,
        GT_MACHINE,
        OTHER
    }

    private final String name;
    private final Kind kind;
    private final long euPerTick;
    private final int output;
    private final List<List<RecipeIngredient>> slots;

    /**
     * @param euPerTick GregTech recipe power, or -1 when unknown
     * @param slots     one entry per ingredient slot, each listing the interchangeable materials for that slot
     */
    public RecipeCandidate(String name, Kind kind, long euPerTick, int output, List<List<RecipeIngredient>> slots) {
        this.name = Objects.requireNonNull(name, "name");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.euPerTick = euPerTick;
        if (output < 1) throw new IllegalArgumentException("Recipe output must be positive.");
        this.output = output;
        List<List<RecipeIngredient>> copy = new ArrayList<List<RecipeIngredient>>();
        for (List<RecipeIngredient> slot : slots)
            copy.add(Collections.unmodifiableList(new ArrayList<RecipeIngredient>(slot)));
        this.slots = Collections.unmodifiableList(copy);
    }

    public String getName() {
        return name;
    }

    public Kind getKind() {
        return kind;
    }

    public long getEuPerTick() {
        return euPerTick;
    }

    public int getOutput() {
        return output;
    }

    public List<List<RecipeIngredient>> getSlots() {
        return slots;
    }
}
