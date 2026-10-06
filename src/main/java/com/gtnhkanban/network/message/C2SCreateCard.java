package com.gtnhkanban.network.message;

import java.util.UUID;

public final class C2SCreateCard extends KanbanRequest {

    public C2SCreateCard() {}

    public C2SCreateCard(UUID projectId, String title, String description) {
        super(title, description, projectId, null, null, null, 0, false, null, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.CREATE_CARD;
    }
}
