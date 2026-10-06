package com.gtnhkanban.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.TreeSet;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;

/** Bounded binary identity; unlike 1.7.10 SNBT, preserves arrays and quoted strings. */
public final class MaterialNbt {

    private static final int MAX_BYTES = 3072;

    private MaterialNbt() {}

    public static String encode(NBTTagCompound tag) {
        if (tag == null) return "";
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            CompressedStreamTools.write((NBTTagCompound) canonical(tag, 0), new DataOutputStream(bytes));
            if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("Ingredient data is too large to track.");
            return Base64.getEncoder()
                .encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to encode ingredient data.", exception);
        }
    }

    private static NBTBase canonical(NBTBase tag, int depth) {
        if (depth > 32) throw new IllegalArgumentException("Ingredient data is too deeply nested.");
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound source = (NBTTagCompound) tag, result = new NBTTagCompound();
            for (String key : new TreeSet<String>(source.func_150296_c()))
                result.setTag(key, canonical(source.getTag(key), depth + 1));
            return result;
        }
        if (tag instanceof NBTTagList) {
            NBTTagList source = (NBTTagList) tag.copy(), result = new NBTTagList();
            while (source.tagCount() > 0) result.appendTag(canonical(source.removeTag(0), depth + 1));
            return result;
        }
        return tag.copy();
    }

    public static NBTTagCompound decode(String text) {
        if (text == null || text.isEmpty()) return null;
        if (text.length() > 4096) throw new IllegalArgumentException("Ingredient data is too large.");
        try {
            byte[] bytes = Base64.getDecoder()
                .decode(text);
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Ingredient data is too large.");
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
            if (input.readUnsignedByte() != 10)
                throw new IllegalArgumentException("Ingredient data must be a compound.");
            input.readUTF();
            NBTTagCompound result = (NBTTagCompound) read(input, 10, 0);
            if (input.available() != 0) throw new IllegalArgumentException("Trailing ingredient data.");
            return result;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid ingredient data.", exception);
        }
    }

    /** Validate lengths against the actual input before allocating; old NBT readers do not. */
    private static NBTBase read(DataInputStream input, int type, int depth) throws IOException {
        if (depth > 32) throw new IllegalArgumentException("Ingredient data is too deeply nested.");
        switch (type) {
            case 1:
                return new NBTTagByte(input.readByte());
            case 2:
                return new NBTTagShort(input.readShort());
            case 3:
                return new NBTTagInt(input.readInt());
            case 4:
                return new NBTTagLong(input.readLong());
            case 5:
                return new NBTTagFloat(input.readFloat());
            case 6:
                return new NBTTagDouble(input.readDouble());
            case 7: {
                int length = length(input, 1);
                byte[] bytes = new byte[length];
                input.readFully(bytes);
                return new NBTTagByteArray(bytes);
            }
            case 8:
                return new NBTTagString(input.readUTF());
            case 9: {
                int childType = input.readUnsignedByte(), count = length(input, 1);
                if (childType == 0 && count > 0) throw new IllegalArgumentException("Invalid ingredient list.");
                NBTTagList list = new NBTTagList();
                for (int i = 0; i < count; i++) list.appendTag(read(input, childType, depth + 1));
                return list;
            }
            case 10: {
                NBTTagCompound result = new NBTTagCompound();
                int childType;
                while ((childType = input.readUnsignedByte()) != 0) {
                    String key = input.readUTF();
                    result.setTag(key, read(input, childType, depth + 1));
                }
                return result;
            }
            case 11: {
                int count = length(input, 4);
                int[] values = new int[count];
                for (int i = 0; i < count; i++) values[i] = input.readInt();
                return new NBTTagIntArray(values);
            }
            default:
                throw new IllegalArgumentException("Unknown ingredient tag type.");
        }
    }

    private static int length(DataInputStream input, int bytesPerEntry) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > input.available() / bytesPerEntry)
            throw new IllegalArgumentException("Invalid ingredient array length.");
        return count;
    }
}
