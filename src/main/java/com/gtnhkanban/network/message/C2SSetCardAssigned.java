package com.gtnhkanban.network.message;

import java.util.UUID;

public final class C2SSetCardAssigned extends KanbanRequest {

    public C2SSetCardAssigned() {}

    public C2SSetCardAssigned(UUID projectId, UUID cardId, UUID memberId, boolean assigned) {
        super("", "", projectId, cardId, null, memberId, 0, assigned, null, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.SET_CARD_ASSIGNED;
    }
}
