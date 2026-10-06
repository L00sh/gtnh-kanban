package com.gtnhkanban.network.message;

import java.util.UUID;

public final class C2SAddTask extends KanbanRequest {

    public C2SAddTask() {}

    public C2SAddTask(UUID projectId, UUID cardId, String text) {
        super(text, "", projectId, cardId, null, null, 0, false, null, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.ADD_TASK;
    }
}
