package com.gtnhkanban.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.gtnhkanban.model.ItemKey;

public final class RequirementView {

    private final List<RequirementView> children;
    private final int amountPerBatch;
    private final boolean reusable;
    private final String recipeName;
    private final int recipeOutput;
    private final long revision;
    private final UUID id;
    private final ItemKey item;
    private final int quantity;
    private final boolean complete;

    public RequirementView(UUID id, ItemKey item, int quantity, boolean complete) {
        this(id, item, quantity, complete, 0, false, "", 0, 0, Collections.<RequirementView>emptyList());
    }

    public RequirementView(UUID id, ItemKey item, int quantity, boolean complete, int amountPerBatch, boolean reusable,
        String recipeName, int recipeOutput, long revision, List<RequirementView> children) {
        this.children = Collections.unmodifiableList(new ArrayList<RequirementView>(children));
        this.amountPerBatch = amountPerBatch;
        this.reusable = reusable;
        this.recipeName = recipeName;
        this.recipeOutput = recipeOutput;
        this.revision = revision;
        this.id = Objects.requireNonNull(id, "id");
        this.item = Objects.requireNonNull(item, "item");
        this.quantity = quantity;
        this.complete = complete;
    }

    public List<RequirementView> getChildren() {
        return children;
    }

    public int getAmountPerBatch() {
        return amountPerBatch;
    }

    public boolean isReusable() {
        return reusable;
    }

    public String getRecipeName() {
        return recipeName;
    }

    public int getRecipeOutput() {
        return recipeOutput;
    }

    public long getRevision() {
        return revision;
    }

    public RequirementView find(UUID entryId) {
        if (id.equals(entryId)) return this;
        for (RequirementView child : children) {
            RequirementView found = child.find(entryId);
            if (found != null) return found;
        }
        return null;
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
