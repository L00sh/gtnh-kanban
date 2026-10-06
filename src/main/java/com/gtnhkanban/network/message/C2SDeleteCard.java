package com.gtnhkanban.network.message;

import java.util.UUID;

public final class C2SDeleteCard extends KanbanRequest {

    public C2SDeleteCard() {}

    public C2SDeleteCard(UUID projectId, UUID cardId) {
        super("", "", projectId, cardId, null, null, 0, false, null, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.DELETE_CARD;
    }
}
