package com.gtnhkanban.network.message;

import java.util.UUID;

public final class C2SFetchBoard extends KanbanRequest {

    public C2SFetchBoard() {}

    public C2SFetchBoard(UUID projectId) {
        super("", "", projectId, null, null, null, 0, false, null, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.FETCH_BOARD;
    }
}
