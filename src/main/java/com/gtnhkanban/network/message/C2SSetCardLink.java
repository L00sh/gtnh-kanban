package com.gtnhkanban.network.message;

import java.util.UUID;

import com.gtnhkanban.model.CardLink;

/** Links or unlinks another card from a card, as a dependency or a blocker. */
public final class C2SSetCardLink extends KanbanRequest {

    public C2SSetCardLink() {}

    public C2SSetCardLink(UUID projectId, UUID cardId, UUID otherCardId, CardLink link, boolean linked) {
        super("", "", projectId, cardId, otherCardId, null, link.ordinal(), linked, null, null);
    }

    @Override
    public RequestType getType() {
        return RequestType.SET_CARD_LINK;
    }
}
