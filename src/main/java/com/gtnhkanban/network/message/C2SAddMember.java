package com.gtnhkanban.network.message;

import java.util.UUID;

public final class C2SAddMember extends KanbanRequest {

    public C2SAddMember() {}

    public C2SAddMember(UUID projectId, String username) {
        super(username, "", projectId, null, null, null, 0, false, null, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.ADD_MEMBER;
    }
}
