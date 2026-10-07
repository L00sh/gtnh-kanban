package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.ICraftingHandler;

/** The recipe types NEI knows (crafting tables, furnace, every machine), for choosing a breakdown preference. */
final class NeiRecipeTypes {

    private static List<String> cached;

    private NeiRecipeTypes() {}

    /** Sorted, without duplicates; read from NEI once per session. */
    static List<String> names() {
        if (cached != null) return cached;
        TreeMap<String, String> names = new TreeMap<String, String>();
        collect(GuiCraftingRecipe.craftinghandlers, names);
        collect(GuiCraftingRecipe.serialCraftingHandlers, names);
        cached = new ArrayList<String>(names.values());
        return cached;
    }

    private static void collect(List<ICraftingHandler> handlers, TreeMap<String, String> names) {
        if (handlers == null) return;
        for (ICraftingHandler handler : handlers) {
            try {
                String name = handler.getRecipeName();
                if (name != null && !name.trim()
                    .isEmpty()) {
                    names.put(
                        name.trim()
                            .toLowerCase(Locale.ROOT),
                        name.trim());
                }
            } catch (RuntimeException ignored) {
                // A handler that cannot name itself outside its own screen is simply not offered.
            }
        }
    }
}
