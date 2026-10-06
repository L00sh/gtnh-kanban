package com.gtnhkanban.network.message;

import java.util.UUID;

import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.Priority;

public final class C2SUpdateCard extends CardFieldsRequest {

    public C2SUpdateCard() {}

    public C2SUpdateCard(UUID projectId, UUID cardId, String title, String description, UUID columnId, UUID typeId,
        Priority priority, ItemKey icon) {
        super(title, description, projectId, cardId, columnId, typeId, priority, icon);
    }

    @Override
    public RequestType getType() {
        return RequestType.UPDATE_CARD;
    }
}
