package com.gtnhkanban.network.message;

import java.util.UUID;

public final class C2SRemoveMember extends KanbanRequest {

    public C2SRemoveMember() {}

    public C2SRemoveMember(UUID projectId, UUID memberId) {
        super("", "", projectId, null, null, memberId, 0, false, null, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.REMOVE_MEMBER;
    }
}
