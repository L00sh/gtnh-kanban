package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.RecipeIngredient;
import com.gtnhkanban.model.RecipePlan;
import com.gtnhkanban.planner.RecipeCandidate;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.FurnaceRecipeHandler;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.ShapedRecipeHandler;
import codechicken.nei.recipe.ShapelessRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

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
        final RecipeCandidate.Kind kind;
        final long euPerTick;

        Choice(String name, int output, List<Input> inputs, String error) {
            this(name, output, inputs, error, RecipeCandidate.Kind.OTHER, -1);
        }

        Choice(String name, int output, List<Input> inputs, String error, RecipeCandidate.Kind kind, long euPerTick) {
            this.name = name;
            this.output = output;
            this.inputs = inputs;
            this.error = error;
            this.kind = kind;
            this.euPerTick = euPerTick;
        }

        /** The planner's view of this recipe, or null when it cannot be used for a breakdown. */
        RecipeCandidate candidate() {
            if (!error.isEmpty() || inputs.isEmpty()) return null;
            List<List<RecipeIngredient>> slots = new ArrayList<List<RecipeIngredient>>();
            for (Input input : inputs) slots.add(input.alternatives);
            return new RecipeCandidate(name, kind, euPerTick, output, slots);
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

    private static final String[] GREGTECH_PACKAGES = { "gregtech.", "gtPlusPlus.", "bartworks.", "tectech.", "ggfab.",
        "kubatech.", "goodgenerator.", "gtnhintergalactic." };
    private static final String[] EXCLUDED_HANDLERS = { "disassembl", "recycl", "scanner", "replicat", "mass fab",
        "amplifab", "uncraft" };

    private NeiRecipeCatalog() {}

    static List<Choice> find(ItemKey requested) {
        List<Choice> choices = new ArrayList<Choice>();
        ItemStack query = MaterialDisplay.stack(requested);
        if (query == null) return choices;
        for (ICraftingHandler handler : GuiCraftingRecipe.getCraftingHandlers("item", query)) {
            RecipeCandidate.Kind kind = kind(handler);
            for (int recipe = 0; recipe < handler.numRecipes(); recipe++) {
                try {
                    Output output = matchingOutput(handler, recipe, requested);
                    if (output == null) continue;
                    if (isExcluded(handler) || isFakeGregTechRecipe(handler, recipe)) continue;
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
                        String skipped = "";
                        slot.generatePermutations();
                        if (isProgrammedCircuit(slot)) continue;
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
                                // Skip just this variant; the slot fails only if no variant is usable.
                                skipped = exception.getMessage();
                            }
                        }
                        if (input.alternatives.isEmpty())
                            error = skipped.isEmpty() ? "An ingredient has no supported concrete alternatives."
                                : skipped;
                        inputs.add(input);
                    }
                    choices.add(
                        new Choice(
                            handler.getRecipeName(),
                            Math.max(1, amount),
                            inputs,
                            error,
                            kind,
                            euPerTick(handler, recipe)));
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

    private static RecipeCandidate.Kind kind(ICraftingHandler handler) {
        if (handler instanceof ShapedRecipeHandler || handler instanceof ShapelessRecipeHandler)
            return RecipeCandidate.Kind.CRAFTING_TABLE;
        if (handler instanceof FurnaceRecipeHandler) return RecipeCandidate.Kind.FURNACE;
        String type = handler.getClass()
            .getName();
        for (String prefix : GREGTECH_PACKAGES) if (type.startsWith(prefix)) return RecipeCandidate.Kind.GT_MACHINE;
        return RecipeCandidate.Kind.OTHER;
    }

    /** Handlers that take things apart or copy them rather than make them. */
    private static boolean isExcluded(ICraftingHandler handler) {
        String name = handler.getRecipeName()
            .toLowerCase(Locale.ROOT);
        for (String fragment : EXCLUDED_HANDLERS) if (name.contains(fragment)) return true;
        return false;
    }

    /** GT's cached NEI recipe exposes the underlying recipe; fake recipes are display-only. */
    private static Object gregTechRecipe(ICraftingHandler handler, int recipe) {
        if (!(handler instanceof TemplateRecipeHandler)) return null;
        try {
            Object cached = ((TemplateRecipeHandler) handler).arecipes.get(recipe);
            return cached.getClass()
                .getField("mRecipe")
                .get(cached);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static boolean isFakeGregTechRecipe(ICraftingHandler handler, int recipe) {
        Object gtRecipe = gregTechRecipe(handler, recipe);
        if (gtRecipe == null) return false;
        try {
            return gtRecipe.getClass()
                .getField("mFakeRecipe")
                .getBoolean(gtRecipe);
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    private static long euPerTick(ICraftingHandler handler, int recipe) {
        Object gtRecipe = gregTechRecipe(handler, recipe);
        if (gtRecipe == null) return -1;
        try {
            return Math.abs(
                gtRecipe.getClass()
                    .getField("mEUt")
                    .getInt(gtRecipe));
        } catch (ReflectiveOperationException exception) {
            return -1;
        }
    }

    /** GT programmed circuits only select a machine mode; they are never part of the material cost. */
    private static boolean isProgrammedCircuit(PositionedStack slot) {
        if (slot.items == null || slot.items.length == 0) return false;
        for (ItemStack stack : slot.items) if (stack == null || stack.getItem() == null
            || !stack.getItem()
                .getClass()
                .getName()
                .contains("IntegratedCircuit"))
            return false;
        return true;
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
        if (isTool(stack)) return true;
        if (stack.isItemStackDamageable() && stack.getItem()
            .hasContainerItem(stack)) {
            ItemStack returned = stack.getItem()
                .getContainerItem(stack);
            return returned != null && returned.getItem() == stack.getItem();
        }
        return false;
    }

    /**
     * GT crafting tools (hammer, file, wrench, saw...). NEI shows them without durability data, so they cannot be
     * recognized by being returned after crafting; recognize them by type and ore dictionary name instead.
     */
    static boolean isTool(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        for (Class<?> type = stack.getItem()
            .getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName()
                .equals("gregtech.api.items.MetaGeneratedTool")) return true;
        }
        for (int id : OreDictionary.getOreIDs(stack)) {
            if (OreDictionary.getOreName(id)
                .startsWith("craftingTool")) return true;
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
