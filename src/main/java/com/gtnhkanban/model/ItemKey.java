package com.gtnhkanban.model;

import java.util.Objects;

/** A persistent item identity that does not require its registry entry to be loaded. */
public final class ItemKey {

    private final String registryName;
    private final int metadata;
    private final boolean fluid;
    private final String nbt;

    public ItemKey(String registryName, int metadata) {
        this(registryName, metadata, false, "");
    }

    public ItemKey(String registryName, int metadata, boolean fluid, String nbt) {
        this.registryName = Objects.requireNonNull(registryName, "registryName");
        this.metadata = metadata;
        this.fluid = fluid;
        this.nbt = nbt == null ? "" : nbt;
    }

    public String getRegistryName() {
        return registryName;
    }

    public int getMetadata() {
        return metadata;
    }

    public boolean isFluid() {
        return fluid;
    }

    public String getNbt() {
        return nbt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ItemKey)) {
            return false;
        }
        ItemKey that = (ItemKey) other;
        return metadata == that.metadata && fluid == that.fluid
            && registryName.equals(that.registryName)
            && nbt.equals(that.nbt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(registryName, metadata, fluid, nbt);
    }
}
