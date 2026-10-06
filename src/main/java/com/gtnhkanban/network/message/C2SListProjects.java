package com.gtnhkanban.network.message;

public final class C2SListProjects extends KanbanRequest {

    public C2SListProjects() {}

    @Override
    public RequestType getType() {
        return RequestType.LIST_PROJECTS;
    }
}
