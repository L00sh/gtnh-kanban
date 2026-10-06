package com.gtnhkanban.network.message;

import java.util.UUID;

public final class C2SMoveCard extends CardFieldsRequest {

    public C2SMoveCard() {}

    public C2SMoveCard(UUID projectId, UUID cardId, UUID columnId) {
        super("", "", projectId, cardId, columnId, null, null, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.MOVE_CARD;
    }
}
