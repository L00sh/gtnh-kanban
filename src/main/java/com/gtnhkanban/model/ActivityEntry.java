package com.gtnhkanban.model;

import java.util.UUID;

/**
 * One thing that happened on a board. The card's number and title are copied in, so the entry still reads right after
 * the card is renamed or deleted.
 */
public final class ActivityEntry {

    /** Longest detail kept; longer text is cut. */
    public static final int MAX_DETAIL = 200;

    public enum Kind {

        PROJECT,
        MEMBERS,
        CARD_CREATED,
        CARD_EDITED,
        CARD_MOVED,
        CARD_DELETED,
        CARD_RESTORED,
        TASKS,
        COMMENTS,
        ITEMS,
        ASSIGNEES,
        LINKS;

        /** Unknown names, e.g. from a newer version, read as a general project entry. */
        public static Kind fromName(String name) {
            for (Kind kind : values()) if (kind.name()
                .equals(name)) return kind;
            return PROJECT;
        }
    }

    private final long time;
    private final UUID actorId;
    private final Kind kind;
    private final UUID cardId;
    private final int cardNumber;
    private final String cardTitle;
    private final String detail;

    public ActivityEntry(long time, UUID actorId, Kind kind, UUID cardId, int cardNumber, String cardTitle,
        String detail) {
        this.time = time;
        this.actorId = actorId;
        this.kind = kind == null ? Kind.PROJECT : kind;
        this.cardId = cardId;
        this.cardNumber = cardNumber;
        this.cardTitle = cardTitle == null ? "" : cardTitle;
        String text = detail == null ? "" : detail;
        this.detail = text.length() > MAX_DETAIL ? text.substring(0, MAX_DETAIL - 3) + "..." : text;
    }

    public long getTime() {
        return time;
    }

    /** Null when not known. */
    public UUID getActorId() {
        return actorId;
    }

    public Kind getKind() {
        return kind;
    }

    /** Null for entries about the project rather than a card. */
    public UUID getCardId() {
        return cardId;
    }

    public int getCardNumber() {
        return cardNumber;
    }

    public String getCardTitle() {
        return cardTitle;
    }

    public String getDetail() {
        return detail;
    }
}
