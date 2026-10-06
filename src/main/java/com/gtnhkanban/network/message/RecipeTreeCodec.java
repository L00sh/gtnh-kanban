package com.gtnhkanban.network.message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.RecipeIngredient;
import com.gtnhkanban.model.RecipePlan;
import com.gtnhkanban.model.RecipeTree;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Compact wire form of a nested breakdown: a table of distinct materials, then nodes that refer to it by index. */
public final class RecipeTreeCodec {

    private static final int MAX_MATERIALS = 4096;
    private static final int MAX_NODES = 4096;
    private static final int MAX_LEVELS = 16;
    private static final int MAX_INGREDIENTS = 256;

    private RecipeTreeCodec() {}

    public static byte[] encode(RecipeTree tree) {
        Map<ItemKey, Integer> materials = new LinkedHashMap<ItemKey, Integer>();
        collect(tree, materials);
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeInt(materials.size());
            for (ItemKey material : materials.keySet()) PacketData.writeMaterial(buffer, material);
            write(buffer, tree, materials);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    public static RecipeTree decode(byte[] bytes) {
        ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
        int count = PacketData.readCount(buffer, MAX_MATERIALS);
        List<ItemKey> materials = new ArrayList<ItemKey>();
        for (int i = 0; i < count; i++) materials.add(PacketData.readMaterial(buffer));
        RecipeTree tree = read(buffer, materials, 1, new int[] { MAX_NODES });
        if (buffer.isReadable()) throw new IllegalArgumentException("Unexpected data after material tree.");
        return tree;
    }

    private static void collect(RecipeTree tree, Map<ItemKey, Integer> materials) {
        for (int i = 0; i < tree.getChildren()
            .size(); i++) {
            ItemKey material = tree.getPlan()
                .getIngredients()
                .get(i)
                .getMaterial();
            if (!materials.containsKey(material)) materials.put(material, materials.size());
            RecipeTree child = tree.getChildren()
                .get(i);
            if (child != null) collect(child, materials);
        }
    }

    private static void write(ByteBuf buffer, RecipeTree tree, Map<ItemKey, Integer> materials) {
        RecipePlan plan = tree.getPlan();
        PacketData.writeString(buffer, plan.getName());
        buffer.writeInt(plan.getOutputAmount());
        buffer.writeInt(
            plan.getIngredients()
                .size());
        for (int i = 0; i < plan.getIngredients()
            .size(); i++) {
            RecipeIngredient ingredient = plan.getIngredients()
                .get(i);
            buffer.writeInt(
                materials.get(ingredient.getMaterial())
                    .intValue());
            buffer.writeInt(ingredient.getAmount());
            buffer.writeBoolean(ingredient.isReusable());
            RecipeTree child = tree.getChildren()
                .get(i);
            buffer.writeBoolean(child != null);
            if (child != null) write(buffer, child, materials);
        }
    }

    private static RecipeTree read(ByteBuf buffer, List<ItemKey> materials, int level, int[] remaining) {
        if (level > MAX_LEVELS) throw new IllegalArgumentException("Material tree is too deep.");
        String name = PacketData.readString(buffer, 512);
        int output = buffer.readInt();
        int count = PacketData.readCount(buffer, MAX_INGREDIENTS);
        remaining[0] -= count;
        if (remaining[0] < 0) throw new IllegalArgumentException("Material tree is too large.");
        List<RecipeIngredient> ingredients = new ArrayList<RecipeIngredient>();
        List<RecipeTree> children = new ArrayList<RecipeTree>();
        for (int i = 0; i < count; i++) {
            int index = buffer.readInt();
            if (index < 0 || index >= materials.size())
                throw new IllegalArgumentException("Unknown material index: " + index);
            ingredients.add(new RecipeIngredient(materials.get(index), buffer.readInt(), buffer.readBoolean()));
            children.add(buffer.readBoolean() ? read(buffer, materials, level + 1, remaining) : null);
        }
        return new RecipeTree(new RecipePlan(name, output, ingredients), children);
    }
}
