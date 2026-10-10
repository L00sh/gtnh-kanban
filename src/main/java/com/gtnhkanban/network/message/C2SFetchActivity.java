package com.gtnhkanban.network.message;

import java.util.UUID;

/** Asks for a board's activity log; answered with {@link S2CActivity}. */
public final class C2SFetchActivity extends KanbanRequest {

    public C2SFetchActivity() {}

    public C2SFetchActivity(UUID projectId) {
        super("", "", projectId, null, null, null, 0, false, null, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.FETCH_ACTIVITY;
    }
}
