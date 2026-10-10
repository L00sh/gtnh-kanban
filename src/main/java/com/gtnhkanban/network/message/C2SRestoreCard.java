package com.gtnhkanban.network.message;

import java.util.UUID;

/** Brings a deleted card back onto its board. */
public final class C2SRestoreCard extends KanbanRequest {

    public C2SRestoreCard() {}

    public C2SRestoreCard(UUID projectId, UUID cardId) {
        super("", "", projectId, cardId, null, null, 0, false, null, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.RESTORE_CARD;
    }
}
