package com.gtnhkanban.network.message;

import java.util.UUID;

import com.gtnhkanban.model.ItemKey;

public final class C2SSetProjectIcon extends KanbanRequest {

    public C2SSetProjectIcon() {}

    /** A null icon clears it. */
    public C2SSetProjectIcon(UUID projectId, ItemKey icon) {
        super("", "", projectId, null, null, null, 0, false, null, icon);
    }

    @Override
    public RequestType getType() {
        return RequestType.SET_PROJECT_ICON;
    }
}
