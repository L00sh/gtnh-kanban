package com.gtnhkanban.client;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.MaterialNbt;

import codechicken.nei.recipe.StackInfo;

/** Client material identity, display, and query stacks; never loaded by the server. */
final class MaterialDisplay {

    private MaterialDisplay() {}

    static ItemKey key(ItemStack stack) {
        if (stack == null || stack.getItem() == null) throw new IllegalArgumentException("Missing ingredient.");
        if (StackInfo.isFluidDisplayItem(stack)) {
            FluidStack fluid = StackInfo.getFluid(stack);
            if (fluid == null) throw new IllegalArgumentException("Unrecognized fluid ingredient.");
            return new ItemKey(
                fluid.getFluid()
                    .getName(),
                0,
                true,
                MaterialNbt.encode(fluid.tag));
        }
        Object name = Item.itemRegistry.getNameForObject(stack.getItem());
        if (name == null || stack.getItemDamage() == 32767)
            throw new IllegalArgumentException("Choose a concrete item variant.");
        return new ItemKey(name.toString(), stack.getItemDamage(), false, MaterialNbt.encode(stack.getTagCompound()));
    }

    static int amount(ItemStack stack) {
        NBTTagCompound tag = StackInfo.itemStackToNBT(stack);
        long amount = tag == null ? stack.stackSize : tag.getLong("Count");
        if (amount > Integer.MAX_VALUE || amount < 0)
            throw new IllegalArgumentException("Unsupported ingredient amount.");
        return (int) amount;
    }

    static NBTTagCompound nbt(ItemKey key) {
        if (key.getNbt()
            .isEmpty()) return null;
        try {
            return MaterialNbt.decode(key.getNbt());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    static ItemStack stack(ItemKey key) {
        if (key.isFluid()) {
            if (FluidRegistry.getFluid(key.getRegistryName()) == null) return null;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("neiFluidName", key.getRegistryName());
            tag.setLong("Count", 1);
            return StackInfo.loadFromNBT(tag);
        }
        Object registered = Item.itemRegistry.getObject(key.getRegistryName());
        if (!(registered instanceof Item)) return null;
        ItemStack stack = new ItemStack((Item) registered, 1, key.getMetadata());
        stack.setTagCompound(nbt(key));
        return stack;
    }

    static String name(ItemKey key) {
        if (key.isFluid()) {
            Fluid fluid = FluidRegistry.getFluid(key.getRegistryName());
            return fluid == null ? key.getRegistryName() : fluid.getLocalizedName(new FluidStack(fluid, 1));
        }
        ItemStack stack = stack(key);
        return stack == null ? key.getRegistryName() : stack.getDisplayName();
    }

    static boolean matches(ItemStack stack, ItemKey key) {
        if (stack == null || key.isFluid()) return false;
        Object item = Item.itemRegistry.getObject(key.getRegistryName());
        return stack.getItem() == item && stack.getItemDamage() == key.getMetadata()
            && (key.getNbt()
                .isEmpty() || java.util.Objects.equals(stack.getTagCompound(), nbt(key)));
    }
}
