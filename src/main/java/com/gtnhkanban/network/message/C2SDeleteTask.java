package com.gtnhkanban.network.message;

import java.util.UUID;

public final class C2SDeleteTask extends KanbanRequest {

    public C2SDeleteTask() {}

    public C2SDeleteTask(UUID projectId, UUID cardId, UUID taskId) {
        super("", "", projectId, cardId, taskId, null, 0, false, null, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.DELETE_TASK;
    }
}
