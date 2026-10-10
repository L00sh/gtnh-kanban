package com.gtnhkanban.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.gtnhkanban.model.ItemKey;

import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.ICraftingHandler;
import cpw.mods.fml.common.FMLLog;

/**
 * The blocks a multiblock needs, as the NEI multiblock preview tab lists them (from blockrenderer6343, which GregTech
 * and other StructureLib machines use). The tab builds the structure in a dummy world at its lowest tier and counts
 * what it placed; this asks it to do the same without being on screen.
 *
 * <p>
 * blockrenderer6343 is optional and its classes are internal, so everything here is reflective and any failure means
 * "not a multiblock we can read".
 */
final class MultiblockStructure {

    private static final String HANDLER = "blockrenderer6343.integration.nei.MultiblockHandler";
    private static final String CONSTRUCTABLE = "com.gtnewhorizon.structurelib.alignment.constructable.IConstructable";
    private static final String TIER_DATA = "blockrenderer6343.client.utils.ConstructableData";
    private static boolean warned;

    /** A block and how many the structure needs. */
    static final class Part {

        final ItemKey item;
        final int count;

        Part(ItemKey item, int count) {
            this.item = item;
            this.count = count;
        }
    }

    private MultiblockStructure() {}

    /** The structure's blocks, merged by item and without the controller itself; empty if it is not a multiblock. */
    static List<Part> parts(ItemStack controller) {
        List<Part> parts = new ArrayList<Part>();
        if (controller == null) return parts;
        try {
            ItemStack query = controller.copy();
            query.stackSize = 1;
            ItemKey controllerKey = MaterialDisplay.key(query);
            // Only the multiblock tab is asked: a full NEI lookup runs every recipe handler in the pack.
            for (ICraftingHandler registered : GuiCraftingRecipe.craftinghandlers) {
                if (!isMultiblockHandler(registered)) continue;
                ICraftingHandler handler = registered.getRecipeHandler("item", query);
                if (handler == null || !isMultiblockHandler(handler) || handler.numRecipes() == 0) continue;
                List<ItemStack> blocks = structure(handler, query);
                if (blocks.isEmpty()) continue;
                Map<ItemKey, Long> counts = new LinkedHashMap<ItemKey, Long>();
                for (ItemStack block : blocks) {
                    if (block == null || block.getItem() == null || block.stackSize <= 0 || isHint(block)) continue;
                    ItemKey key;
                    try {
                        key = MaterialDisplay.key(block);
                    } catch (IllegalArgumentException exception) {
                        continue;
                    }
                    if (key.equals(controllerKey)) continue;
                    Long previous = counts.get(key);
                    counts.put(key, (previous == null ? 0L : previous) + block.stackSize);
                }
                for (Map.Entry<ItemKey, Long> entry : counts.entrySet())
                    parts.add(new Part(entry.getKey(), (int) Math.min(Integer.MAX_VALUE, entry.getValue())));
                return parts;
            }
        } catch (Throwable throwable) {
            // Anything from a changed or missing blockrenderer6343: treat as not a multiblock, but say so once.
            if (!warned) {
                warned = true;
                FMLLog.warning("GTNH Kanban could not read a multiblock structure from NEI: %s", throwable);
            }
            parts.clear();
        }
        return parts;
    }

    private static boolean isMultiblockHandler(ICraftingHandler handler) {
        for (Class<?> type = handler.getClass(); type != null; type = type.getSuperclass()) if (type.getName()
            .equals(HANDLER)) return true;
        return false;
    }

    /** Has the tab's preview place the handler's first multiblock and hands back the block list it publishes. */
    @SuppressWarnings("unchecked")
    private static List<ItemStack> structure(ICraftingHandler handler, ItemStack query) throws Exception {
        Object[] multiblocks = (Object[]) field(handler.getClass(), "currentMultiblocks").get(handler);
        if (multiblocks == null || multiblocks.length == 0) return new ArrayList<ItemStack>();
        Object constructable = multiblocks[0];
        Object preview = field(handler.getClass(), "guiHandler").get(handler);
        Class<?> constructableType = Class.forName(CONSTRUCTABLE);
        ItemStack stack = (ItemStack) handler.getClass()
            .getMethod("getConstructableStack", constructableType)
            .invoke(handler, constructable);
        Class<?> tierType = Class.forName(TIER_DATA);
        Object tiers = tierType.getMethod("getTierData", constructableType)
            .invoke(null, constructable);
        // Same as the tab: the tier follows the stack size, so one controller is the lowest tier.
        tiers = tierType.getMethod("setTierFromStack", ItemStack.class)
            .invoke(tiers, query);

        final List<ItemStack> captured = new ArrayList<ItemStack>();
        Field listener = field(preview.getClass(), "onIngredientChanged");
        Object previous = listener.get(preview);
        listener.set(preview, new Consumer<List<ItemStack>>() {

            @Override
            public void accept(List<ItemStack> blocks) {
                captured.clear();
                if (blocks != null) for (ItemStack block : blocks) if (block != null) captured.add(block.copy());
            }
        });
        try {
            Method load = preview.getClass()
                .getMethod("loadMultiblock", constructableType, ItemStack.class, tierType);
            load.invoke(preview, constructable, stack, tiers);
        } finally {
            listener.set(preview, previous);
        }
        return captured;
    }

    /** StructureLib hint blocks mark spots in previews; they are not real blocks to build with. */
    private static boolean isHint(ItemStack stack) {
        Object name = Item.itemRegistry.getNameForObject(stack.getItem());
        return name != null && name.toString()
            .startsWith("structurelib:");
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Declared further up.
            }
        }
        throw new NoSuchFieldException(name);
    }
}
