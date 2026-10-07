package com.gtnhkanban.network.message;

import java.util.UUID;

/** Asks for the usernames the project owner could add as members. */
public final class C2SListPlayerNames extends KanbanRequest {

    public C2SListPlayerNames() {}

    public C2SListPlayerNames(UUID projectId) {
        super("", "", projectId, null, null, null, 0, false, null, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.LIST_PLAYER_NAMES;
    }
}
