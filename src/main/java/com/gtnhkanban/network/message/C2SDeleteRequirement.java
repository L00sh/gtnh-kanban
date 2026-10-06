package com.gtnhkanban.network.message;

import java.util.UUID;

public final class C2SDeleteRequirement extends KanbanRequest {

    public C2SDeleteRequirement() {}

    public C2SDeleteRequirement(UUID projectId, UUID cardId, UUID entryId) {
        super("", "", projectId, cardId, entryId, null, 0, false, null, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.DELETE_REQUIREMENT;
    }
}
