package com.gtnhkanban.service;

import com.gtnhkanban.model.ItemKey;

/** Checks item identities when a new checklist entry is created. */
public interface ItemResolver {

    boolean isRegistered(ItemKey item);
}
