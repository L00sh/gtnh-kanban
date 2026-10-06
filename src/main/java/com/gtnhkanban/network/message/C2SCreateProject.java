package com.gtnhkanban.network.message;

public final class C2SCreateProject extends KanbanRequest {

    public C2SCreateProject() {}

    public C2SCreateProject(String name) {
        super(name, "", null, null, null, null, 0, false, null, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.CREATE_PROJECT;
    }
}
