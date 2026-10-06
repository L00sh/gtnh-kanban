package com.gtnhkanban.network.message;

import java.util.UUID;

public final class C2SSetTaskDone extends KanbanRequest {

    public C2SSetTaskDone() {}

    public C2SSetTaskDone(UUID projectId, UUID cardId, UUID taskId, boolean done) {
        super("", "", projectId, cardId, taskId, null, 0, done, null, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.SET_TASK_DONE;
    }
}
