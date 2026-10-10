package com.gtnhkanban.network.message;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.gtnhkanban.api.ActivityLog;
import com.gtnhkanban.model.ActivityEntry;
import com.gtnhkanban.model.KanbanProject;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/** A board's activity log and kept deleted cards, sent when a player opens or refreshes the Activity tab. */
public final class S2CActivity implements IMessage {

    private ActivityLog log;

    public S2CActivity() {}

    public S2CActivity(ActivityLog log) {
        this.log = log;
    }

    public ActivityLog getLog() {
        return log;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        PacketData.writeUuid(buffer, log.getProjectId());
        List<ActivityLog.Entry> entries = log.getEntries();
        int entryCount = Math.min(entries.size(), KanbanProject.MAX_ACTIVITY);
        buffer.writeInt(entryCount);
        for (int i = 0; i < entryCount; i++) {
            ActivityLog.Entry entry = entries.get(i);
            buffer.writeLong(entry.time);
            PacketData.writeUuid(buffer, entry.actorId);
            PacketData.writeString(buffer, entry.actorName);
            PacketData.writeString(buffer, entry.kind.name());
            PacketData.writeUuid(buffer, entry.cardId);
            buffer.writeInt(entry.cardNumber);
            PacketData.writeString(buffer, entry.cardTitle);
            PacketData.writeString(buffer, entry.detail);
        }
        List<ActivityLog.Deleted> deleted = log.getDeleted();
        int deletedCount = Math.min(deleted.size(), KanbanProject.MAX_DELETED);
        buffer.writeInt(deletedCount);
        for (int i = 0; i < deletedCount; i++) {
            ActivityLog.Deleted card = deleted.get(i);
            PacketData.writeUuid(buffer, card.cardId);
            buffer.writeInt(card.number);
            PacketData.writeString(buffer, card.title);
            buffer.writeLong(card.deletedAt);
            PacketData.writeString(buffer, card.deletedByName);
            buffer.writeBoolean(card.canRestore);
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        UUID projectId = PacketData.readUuid(buffer);
        int entryCount = PacketData.readCount(buffer, KanbanProject.MAX_ACTIVITY);
        List<ActivityLog.Entry> entries = new ArrayList<ActivityLog.Entry>(entryCount);
        for (int i = 0; i < entryCount; i++) entries.add(
            new ActivityLog.Entry(
                buffer.readLong(),
                PacketData.readUuid(buffer),
                PacketData.readString(buffer, 128),
                ActivityEntry.Kind.fromName(PacketData.readString(buffer, 64)),
                PacketData.readUuid(buffer),
                buffer.readInt(),
                // Card titles are up to 255 characters, details up to 200, at most 3 UTF-8 bytes each.
                PacketData.readString(buffer, 1024),
                PacketData.readString(buffer, 1024)));
        int deletedCount = PacketData.readCount(buffer, KanbanProject.MAX_DELETED);
        List<ActivityLog.Deleted> deleted = new ArrayList<ActivityLog.Deleted>(deletedCount);
        for (int i = 0; i < deletedCount; i++) deleted.add(
            new ActivityLog.Deleted(
                PacketData.readUuid(buffer),
                buffer.readInt(),
                PacketData.readString(buffer, 1024),
                buffer.readLong(),
                PacketData.readString(buffer, 128),
                buffer.readBoolean()));
        log = new ActivityLog(projectId, entries, deleted);
    }
}
