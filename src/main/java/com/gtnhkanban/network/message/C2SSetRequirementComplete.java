package com.gtnhkanban.network.message;

import java.util.UUID;

public final class C2SSetRequirementComplete extends KanbanRequest {

    public C2SSetRequirementComplete() {}

    public C2SSetRequirementComplete(UUID projectId, UUID cardId, UUID entryId, boolean complete) {
        super("", "", projectId, cardId, entryId, null, 0, complete, null, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.SET_REQUIREMENT_COMPLETE;
    }
}
