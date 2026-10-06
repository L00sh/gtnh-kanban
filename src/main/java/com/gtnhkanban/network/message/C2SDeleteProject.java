package com.gtnhkanban.network.message;

import java.util.UUID;

public final class C2SDeleteProject extends KanbanRequest {

    public C2SDeleteProject() {}

    public C2SDeleteProject(UUID projectId) {
        super("", "", projectId, null, null, null, 0, false, null, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.DELETE_PROJECT;
    }
}
