package com.gtnhkanban.network.message;

import java.util.UUID;

public final class C2SDeleteComment extends KanbanRequest {

    public C2SDeleteComment() {}

    public C2SDeleteComment(UUID projectId, UUID cardId, UUID commentId) {
        super("", "", projectId, cardId, commentId, null, 0, false, null, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.DELETE_COMMENT;
    }
}
