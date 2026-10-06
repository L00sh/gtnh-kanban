package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.RecipeIngredient;
import com.gtnhkanban.model.RecipePlan;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.ICraftingHandler;

/** Extracts concrete planning inputs from NEI, including GT fluid display stacks. */
final class NeiRecipeCatalog {

    static final class Input {

        final List<RecipeIngredient> alternatives = new ArrayList<RecipeIngredient>();
    }

    static final class Choice {

        final String name;
        final int output;
        final List<Input> inputs;
        final String error;

        Choice(String name, int output, List<Input> inputs, String error) {
            this.name = name;
            this.output = output;
            this.inputs = inputs;
            this.error = error;
        }

        RecipePlan plan(int[] selection) {
            if (!error.isEmpty()) throw new IllegalArgumentException(error);
            Map<ItemKey, RecipeIngredient> consumed = new LinkedHashMap<ItemKey, RecipeIngredient>();
            Map<ItemKey, RecipeIngredient> reusable = new LinkedHashMap<ItemKey, RecipeIngredient>();
            for (int i = 0; i < inputs.size(); i++) {
                if (selection[i] < 0) throw new IllegalArgumentException("Choose an alternative for every ingredient.");
                RecipeIngredient input = inputs.get(i).alternatives.get(selection[i]);
                Map<ItemKey, RecipeIngredient> target = input.isReusable() ? reusable : consumed;
                RecipeIngredient previous = target.get(input.getMaterial());
                long amount = previous == null ? input.getAmount()
                    : input.isReusable() ? Math.max(previous.getAmount(), input.getAmount())
                        : (long) previous.getAmount() + input.getAmount();
                if (amount > Integer.MAX_VALUE) throw new IllegalArgumentException("Ingredient quantity is too large.");
                target.put(
                    input.getMaterial(),
                    new RecipeIngredient(input.getMaterial(), (int) amount, input.isReusable()));
            }
            List<RecipeIngredient> result = new ArrayList<RecipeIngredient>(consumed.values());
            result.addAll(reusable.values());
            return new RecipePlan(name.length() > 128 ? name.substring(0, 128) : name, output, result);
        }
    }

    private NeiRecipeCatalog() {}

    static List<Choice> find(ItemKey requested) {
        List<Choice> choices = new ArrayList<Choice>();
        ItemStack query = MaterialDisplay.stack(requested);
        if (query == null) return choices;
        for (ICraftingHandler handler : GuiCraftingRecipe.getCraftingHandlers("item", query)) {
            for (int recipe = 0; recipe < handler.numRecipes(); recipe++) {
                try {
                    Output output = matchingOutput(handler, recipe, requested);
                    if (output == null) continue;
                    int amount = output.amount;
                    List<Input> inputs = new ArrayList<Input>();
                    String error = "";
                    if (amount < 1 || output.probabilistic)
                        error = "Probabilistic or unknown output amount cannot be expanded.";
                    List<PositionedStack> ingredients = handler.getIngredientStacks(recipe);
                    if (ingredients == null || ingredients.isEmpty() || ingredients.size() > 256)
                        error = "This recipe does not expose usable ingredients.";
                    else for (PositionedStack slot : ingredients) {
                        Input input = new Input();
                        slot.generatePermutations();
                        if (slot.items != null) for (ItemStack stack : slot.items) {
                            try {
                                ItemKey key = MaterialDisplay.key(stack);
                                int count = inputAmount(slot, stack);
                                if (key.getNbt()
                                    .length() > 4096)
                                    throw new IllegalArgumentException("Ingredient data is too large.");
                                boolean duplicate = false;
                                for (RecipeIngredient previous : input.alternatives) if (previous.getMaterial()
                                    .equals(key)) duplicate = true;
                                if (!duplicate) input.alternatives
                                    .add(new RecipeIngredient(key, Math.max(1, count), isReusable(slot, stack, count)));
                            } catch (IllegalArgumentException exception) {
                                error = exception.getMessage();
                            }
                        }
                        if (input.alternatives.isEmpty())
                            error = "An ingredient has no supported concrete alternatives.";
                        inputs.add(input);
                    }
                    choices.add(new Choice(handler.getRecipeName(), Math.max(1, amount), inputs, error));
                } catch (RuntimeException exception) {
                    choices.add(
                        new Choice(
                            handler.getRecipeName(),
                            1,
                            new ArrayList<Input>(),
                            "This NEI handler cannot expose a complete material list."));
                }
                if (choices.size() >= 4096) return choices;
            }
        }
        return choices;
    }

    private static final class Output {

        final int amount;
        final boolean probabilistic;

        Output(int amount, boolean probabilistic) {
            this.amount = amount;
            this.probabilistic = probabilistic;
        }
    }

    private static int inputAmount(PositionedStack slot, ItemStack stack) {
        if (!MaterialDisplay.key(stack)
            .isFluid() && slot.getClass()
                .getName()
                .startsWith("gregtech.")) {
            try {
                int amount = slot.getClass()
                    .getField("realStackSize")
                    .getInt(slot);
                if (amount < 0) throw new IllegalArgumentException("Unknown GregTech ingredient amount.");
                return amount;
            } catch (NoSuchFieldException exception) {
                // Other GT handlers expose their original counts directly.
            } catch (IllegalAccessException exception) {
                throw new IllegalArgumentException("Unable to read GregTech ingredient amount.", exception);
            }
        }
        return MaterialDisplay.amount(stack);
    }

    private static boolean isReusable(PositionedStack slot, ItemStack stack, int amount) {
        if (amount == 0 || slot.getChance() == 0) return true;
        if (slot.getClass()
            .getName()
            .startsWith("gregtech.")) {
            try {
                // GT special recipe slots (data sticks, for example) are prerequisites.
                if (!slot.getClass()
                    .getField("mIsInput")
                    .getBoolean(slot)) return true;
            } catch (NoSuchFieldException | IllegalAccessException exception) {
                // Not a handler with exposed special-slot semantics.
            }
        }
        String itemClass = stack.getItem()
            .getClass()
            .getName();
        boolean tool = stack.isItemStackDamageable() || itemClass.contains("MetaGeneratedTool")
            || itemClass.contains("MetaGenerated_Tool");
        if (tool && stack.getItem()
            .hasContainerItem(stack)) {
            ItemStack returned = stack.getItem()
                .getContainerItem(stack);
            return returned != null && returned.getItem() == stack.getItem();
        }
        return false;
    }

    private static Output matchingOutput(ICraftingHandler handler, int recipe, ItemKey requested) {
        List<PositionedStack> outputs = new ArrayList<PositionedStack>();
        PositionedStack main = handler.getResultStack(recipe);
        if (main != null) outputs.add(main);
        List<PositionedStack> others = handler.getOtherStacks(recipe);
        if (others != null) for (PositionedStack output : others) if (output != main) outputs.add(output);
        long amount = 0;
        boolean found = false, probabilistic = false;
        for (PositionedStack output : outputs) {
            if (output == null || output.item == null) continue;
            ItemKey key = MaterialDisplay.key(output.item);
            if (key.isFluid() == requested.isFluid() && key.getRegistryName()
                .equals(requested.getRegistryName())
                && key.getMetadata() == requested.getMetadata()
                && (requested.getNbt()
                    .isEmpty()
                    || key.getNbt()
                        .equals(requested.getNbt()))) {
                found = true;
                amount += inputAmount(output, output.item);
                probabilistic |= output.getChance() < PositionedStack.CHANCE_FULL;
            }
        }
        if (amount > Integer.MAX_VALUE) throw new IllegalArgumentException("Recipe output is too large.");
        return found ? new Output((int) amount, probabilistic) : null;
    }
}
