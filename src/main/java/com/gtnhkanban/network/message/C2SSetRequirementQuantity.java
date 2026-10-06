package com.gtnhkanban.network.message;

import java.util.UUID;

public final class C2SSetRequirementQuantity extends KanbanRequest {

    public C2SSetRequirementQuantity() {}

    public C2SSetRequirementQuantity(UUID projectId, UUID cardId, UUID entryId, int quantity) {
        super("", "", projectId, cardId, entryId, null, quantity, false, null, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.SET_REQUIREMENT_QUANTITY;
    }
}
