package com.gtnhkanban.network.message;

import java.util.UUID;

public final class C2SAddComment extends KanbanRequest {

    public C2SAddComment() {}

    public C2SAddComment(UUID projectId, UUID cardId, String text) {
        super(text, "", projectId, cardId, null, null, 0, false, null, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.ADD_COMMENT;
    }
}
