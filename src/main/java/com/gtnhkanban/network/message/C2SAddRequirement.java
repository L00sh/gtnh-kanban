package com.gtnhkanban.network.message;

import java.util.UUID;

import com.gtnhkanban.model.ItemKey;

public final class C2SAddRequirement extends KanbanRequest {

    public C2SAddRequirement() {}

    public C2SAddRequirement(UUID projectId, UUID cardId, ItemKey item, int quantity) {
        super("", "", projectId, cardId, null, null, quantity, false, null, item);
    }

    @Override
    public RequestType getType() {
        return RequestType.ADD_REQUIREMENT;
    }
}
