package com.gtnhkanban.network.message;

import java.util.UUID;

import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.Priority;

public final class C2SCreateCard extends CardFieldsRequest {

    public C2SCreateCard() {}

    /** @param columnId null for the first column */
    public C2SCreateCard(UUID projectId, String title, String description, UUID columnId, UUID typeId,
        Priority priority, ItemKey icon) {
        super(title, description, projectId, null, columnId, typeId, priority, icon);
    }

    @Override
    public RequestType getType() {
        return RequestType.CREATE_CARD;
    }
}
