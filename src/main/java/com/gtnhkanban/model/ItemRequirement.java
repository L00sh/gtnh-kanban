package com.gtnhkanban.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** An item or fluid requirement, optionally expanded into recipe inputs. */
public final class ItemRequirement {

    private final UUID id;
    private final ItemKey item;
    private int quantity;
    private boolean complete;
    private final int amountPerBatch;
    private final boolean reusable;
    private String recipeName;
    private int recipeOutput;
    private long revision;
    private final List<ItemRequirement> children;

    public ItemRequirement(UUID id, ItemKey item, int quantity, boolean complete) {
        this(id, item, quantity, complete, 0, false, "", 0, 0, Collections.<ItemRequirement>emptyList());
    }

    public ItemRequirement(UUID id, ItemKey item, int quantity, boolean complete, int amountPerBatch, boolean reusable,
        String recipeName, int recipeOutput, long revision, List<ItemRequirement> children) {
        this.id = Objects.requireNonNull(id, "id");
        this.item = Objects.requireNonNull(item, "item");
        if (quantity < 1) throw new IllegalArgumentException("quantity must be positive");
        this.quantity = quantity;
        this.complete = complete;
        this.amountPerBatch = amountPerBatch;
        this.reusable = reusable;
        this.recipeName = recipeName;
        this.recipeOutput = recipeOutput;
        this.revision = revision;
        this.children = new ArrayList<ItemRequirement>(children);
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
        if (this.complete != complete) revision++;
        this.complete = complete;
    }

    public void touch() {
        revision++;
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

    public List<ItemRequirement> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public ItemRequirement find(UUID entryId) {
        if (id.equals(entryId)) return this;
        for (ItemRequirement child : children) {
            ItemRequirement found = child.find(entryId);
            if (found != null) return found;
        }
        return null;
    }

    public int nodeCount() {
        int count = 1;
        for (ItemRequirement child : children) count += child.nodeCount();
        return count;
    }

    public boolean removeDescendant(UUID entryId) {
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).id.equals(entryId)) {
                children.remove(i);
                revision++;
                return true;
            }
            if (children.get(i)
                .removeDescendant(entryId)) {
                revision++;
                return true;
            }
        }
        return false;
    }

    /** Preflight every descendant before mutating; overflow cannot leave a half-updated tree. */
    public void setQuantity(int quantity) {
        validateQuantity(quantity);
        applyQuantity(quantity);
    }

    private void validateQuantity(int value) {
        if (value < 1) throw new IllegalArgumentException("quantity must be positive");
        for (ItemRequirement child : children)
            child.validateQuantity(childAmount(value, recipeOutput, child.amountPerBatch, child.reusable));
    }

    private void applyQuantity(int value) {
        if (quantity != value) {
            complete = false;
            revision++;
        }
        quantity = value;
        for (ItemRequirement child : children)
            child.applyQuantity(childAmount(value, recipeOutput, child.amountPerBatch, child.reusable));
    }

    public static int childAmount(int parentAmount, int output, int perBatch, boolean reusable) {
        if (parentAmount < 1 || output < 1 || perBatch < 1)
            throw new IllegalArgumentException("Invalid recipe amounts.");
        long batches = ((long) parentAmount + output - 1) / output;
        long result = reusable ? perBatch : batches * perBatch;
        if (result > Integer.MAX_VALUE) throw new IllegalArgumentException("Required quantity is too large.");
        return (int) result;
    }

    public void expand(RecipePlan plan) {
        List<ItemRequirement> replacements = new ArrayList<ItemRequirement>();
        for (RecipeIngredient ingredient : plan.getIngredients()) {
            replacements.add(
                new ItemRequirement(
                    UUID.randomUUID(),
                    ingredient.getMaterial(),
                    childAmount(quantity, plan.getOutputAmount(), ingredient.getAmount(), ingredient.isReusable()),
                    false,
                    ingredient.getAmount(),
                    ingredient.isReusable(),
                    "",
                    0,
                    0,
                    Collections.<ItemRequirement>emptyList()));
        }
        children.clear();
        children.addAll(replacements);
        recipeName = plan.getName();
        recipeOutput = plan.getOutputAmount();
        revision++;
    }

    public void clearExpansion() {
        children.clear();
        recipeName = "";
        recipeOutput = 0;
        revision++;
    }
}
