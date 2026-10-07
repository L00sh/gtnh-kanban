package com.gtnhkanban.network.message;

import java.util.UUID;

public final class C2SMoveCard extends CardFieldsRequest {

    public C2SMoveCard() {}

    /** @param beforeCardId the card in {@code columnId} to land just above, or null for the bottom of the column */
    public C2SMoveCard(UUID projectId, UUID cardId, UUID columnId, UUID beforeCardId) {
        super("", "", projectId, cardId, beforeCardId, columnId, null, null, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.MOVE_CARD;
    }
}
