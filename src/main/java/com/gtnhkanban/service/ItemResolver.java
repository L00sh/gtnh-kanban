package com.gtnhkanban.service;

import com.gtnhkanban.model.ItemKey;

/** Checks item identities when a new checklist entry is created. */
public interface ItemResolver {

    boolean isRegistered(ItemKey item);

    /** A readable name for activity entries; the registry name when nothing better is known. */
    default String displayName(ItemKey item) {
        return item.getRegistryName();
    }
}
