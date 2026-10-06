package com.gtnhkanban.network.message;

import java.util.UUID;

import com.gtnhkanban.model.CardStatus;

public final class C2SMoveCard extends KanbanRequest {

    public C2SMoveCard() {}

    public C2SMoveCard(UUID projectId, UUID cardId, CardStatus status) {
        super("", "", projectId, cardId, null, null, 0, false, status, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.MOVE_CARD;
    }
}
