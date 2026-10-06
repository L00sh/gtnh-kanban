package com.gtnhkanban.model;

import java.util.Objects;

/** A persistent item identity that does not require its registry entry to be loaded. */
public final class ItemKey {

    private final String registryName;
    private final int metadata;

    public ItemKey(String registryName, int metadata) {
        this.registryName = Objects.requireNonNull(registryName, "registryName");
        this.metadata = metadata;
    }

    public String getRegistryName() {
        return registryName;
    }

    public int getMetadata() {
        return metadata;
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
        return metadata == that.metadata && registryName.equals(that.registryName);
    }

    @Override
    public int hashCode() {
        return 31 * registryName.hashCode() + metadata;
    }
}
