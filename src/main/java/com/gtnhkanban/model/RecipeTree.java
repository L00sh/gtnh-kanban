package com.gtnhkanban.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A recipe choice for one material plus, for each of its ingredients, that ingredient's own breakdown. A null child
 * means the ingredient is a leaf: a base material, a reusable input, or something with no usable recipe.
 */
public final class RecipeTree {

    private final RecipePlan plan;
    private final List<RecipeTree> children;

    public RecipeTree(RecipePlan plan, List<RecipeTree> children) {
        this.plan = Objects.requireNonNull(plan, "plan");
        if (children == null || children.size() != plan.getIngredients()
            .size()) {
            throw new IllegalArgumentException("A breakdown needs exactly one entry per recipe ingredient.");
        }
        this.children = Collections.unmodifiableList(new ArrayList<RecipeTree>(children));
    }

    public RecipePlan getPlan() {
        return plan;
    }

    /** One entry per ingredient of {@link #getPlan()}, in the same order; entries may be null. */
    public List<RecipeTree> getChildren() {
        return children;
    }

    /** Number of checklist rows this breakdown adds below the material it expands. */
    public int nodeCount() {
        int count = children.size();
        for (RecipeTree child : children) if (child != null) count += child.nodeCount();
        return count;
    }

    /** Levels of rows this breakdown adds below the material it expands. */
    public int depth() {
        int deepest = 0;
        for (RecipeTree child : children) if (child != null) deepest = Math.max(deepest, child.depth());
        return deepest + 1;
    }
}
