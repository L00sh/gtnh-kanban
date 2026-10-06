package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.planner.RecipeCandidate;
import com.gtnhkanban.planner.RecipeSource;

/**
 * The running game's recipes (from NEI) and material classes (from the ore dictionary). Client thread only. Holds no
 * per-material state of its own; {@link BreakdownJobs} wraps it in a session cache.
 */
final class NeiRecipeSource implements RecipeSource {

    /** Breakdowns stop here: smelted or mined forms, never the ore processing that produces them. */
    private static final String[] BASE_PREFIXES = { "ingot", "nugget", "gem", "dust", "crystal", "cell" };
    private static final Set<String> BASE_NAMES = new HashSet<String>(
        Arrays.asList("sand", "gravel", "cobblestone", "stone", "blockGlass", "blockGlassColorless"));
    private static final String[] ORE_PREFIXES = { "ore", "rawOre", "crushed", "dustImpure", "dustPure", "cluster" };

    /** Why the most recent {@link #recipes} call rejected recipes; read right after it by the cache. */
    private ItemKey lastLookup;
    private String lastRejected = "";

    @Override
    public List<RecipeCandidate> recipes(ItemKey material) {
        List<RecipeCandidate> candidates = new ArrayList<RecipeCandidate>();
        List<String> rejected = new ArrayList<String>();
        for (NeiRecipeCatalog.Choice choice : NeiRecipeCatalog.find(material)) {
            try {
                RecipeCandidate candidate = choice.candidate();
                if (candidate != null) candidates.add(candidate);
                else rejected.add(choice.name + ": " + (choice.error.isEmpty() ? "no ingredients" : choice.error));
            } catch (IllegalArgumentException exception) {
                rejected.add(choice.name + ": " + exception.getMessage());
            }
        }
        lastLookup = material;
        lastRejected = rejected.isEmpty() ? "" : rejected.toString();
        return candidates;
    }

    @Override
    public String rejectedRecipes(ItemKey material) {
        return material.equals(lastLookup) ? lastRejected : "";
    }

    @Override
    public boolean isBaseMaterial(ItemKey material) {
        // Buckets and cells are needed as they are, never broken down. IFluidContainerItem is deliberately not used:
        // GT's base item and machine item classes implement it, so it would match nearly every GT part and machine.
        ItemStack stack = material.isFluid() ? null : MaterialDisplay.stack(material);
        if (stack != null && (FluidContainerRegistry.isContainer(stack) || NeiRecipeCatalog.isTool(stack))) return true;
        for (String name : names(material)) {
            if (BASE_NAMES.contains(name)) return true;
            for (String prefix : BASE_PREFIXES) if (hasPrefix(name, prefix)) return true;
        }
        return false;
    }

    @Override
    public boolean isOre(ItemKey material) {
        for (String name : names(material)) for (String prefix : ORE_PREFIXES) if (hasPrefix(name, prefix)) return true;
        return false;
    }

    /** "dustIron" has the prefix "dust"; "oreberryIron" does not have the prefix "ore". */
    static boolean hasPrefix(String name, String prefix) {
        return name.length() > prefix.length() && name.startsWith(prefix)
            && Character.isUpperCase(name.charAt(prefix.length()));
    }

    private List<String> names(ItemKey material) {
        List<String> names = new ArrayList<String>();
        ItemStack stack = material.isFluid() ? null : MaterialDisplay.stack(material);
        if (stack != null) {
            for (int id : OreDictionary.getOreIDs(stack)) names.add(OreDictionary.getOreName(id));
        }
        return names;
    }
}
