package com.gtnhkanban.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.gtnhkanban.model.ActivityEntry;

/** A board's activity, newest first, and the deleted cards it still keeps, as one player sees them. */
public final class ActivityLog {

    /** One entry, with its actor's name filled in. */
    public static final class Entry {

        public final long time;
        public final UUID actorId;
        public final String actorName;
        public final ActivityEntry.Kind kind;
        public final UUID cardId;
        public final int cardNumber;
        public final String cardTitle;
        public final String detail;

        public Entry(long time, UUID actorId, String actorName, ActivityEntry.Kind kind, UUID cardId, int cardNumber,
            String cardTitle, String detail) {
            this.time = time;
            this.actorId = actorId;
            this.actorName = actorName == null ? "" : actorName;
            this.kind = kind;
            this.cardId = cardId;
            this.cardNumber = cardNumber;
            this.cardTitle = cardTitle == null ? "" : cardTitle;
            this.detail = detail == null ? "" : detail;
        }
    }

    /** A deleted card that can still be restored, by this player or not. */
    public static final class Deleted {

        public final UUID cardId;
        public final int number;
        public final String title;
        public final long deletedAt;
        public final String deletedByName;
        public final boolean canRestore;

        public Deleted(UUID cardId, int number, String title, long deletedAt, String deletedByName,
            boolean canRestore) {
            this.cardId = cardId;
            this.number = number;
            this.title = title == null ? "" : title;
            this.deletedAt = deletedAt;
            this.deletedByName = deletedByName == null ? "" : deletedByName;
            this.canRestore = canRestore;
        }
    }

    private final UUID projectId;
    private final List<Entry> entries;
    private final List<Deleted> deleted;

    public ActivityLog(UUID projectId, List<Entry> entries, List<Deleted> deleted) {
        this.projectId = projectId;
        this.entries = Collections.unmodifiableList(new ArrayList<Entry>(entries));
        this.deleted = Collections.unmodifiableList(new ArrayList<Deleted>(deleted));
    }

    public UUID getProjectId() {
        return projectId;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public List<Deleted> getDeleted() {
        return deleted;
    }
}
