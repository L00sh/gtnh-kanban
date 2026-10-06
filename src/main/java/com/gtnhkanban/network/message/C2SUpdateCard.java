package com.gtnhkanban.network.message;

import java.util.UUID;

import com.gtnhkanban.model.CardStatus;

public final class C2SUpdateCard extends KanbanRequest {

    public C2SUpdateCard() {}

    public C2SUpdateCard(UUID projectId, UUID cardId, String title, String description, CardStatus status) {
        super(title, description, projectId, cardId, null, null, 0, false, status, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.UPDATE_CARD;
    }
}
