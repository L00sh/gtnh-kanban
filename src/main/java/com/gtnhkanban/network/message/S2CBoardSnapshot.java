package com.gtnhkanban.network.message;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.CardView;
import com.gtnhkanban.api.CommentView;
import com.gtnhkanban.api.MemberSummary;
import com.gtnhkanban.api.ProjectSummary;
import com.gtnhkanban.api.RequirementView;
import com.gtnhkanban.api.TaskView;
import com.gtnhkanban.model.BoardSettings;
import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.Priority;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public final class S2CBoardSnapshot implements IMessage {

    private BoardSnapshot snapshot;

    public S2CBoardSnapshot() {}

    public S2CBoardSnapshot(BoardSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public BoardSnapshot getSnapshot() {
        return snapshot;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        PacketData.writeProject(buffer, snapshot.getProject());
        PacketData.writeSettings(buffer, snapshot.getSettings());
        buffer.writeInt(
            snapshot.getMembers()
                .size());
        for (MemberSummary member : snapshot.getMembers()) {
            PacketData.writeUuid(buffer, member.getPlayerId());
            PacketData.writeString(buffer, member.getDisplayName());
            buffer.writeBoolean(member.isOwner());
        }
        buffer.writeInt(
            snapshot.getCards()
                .size());
        for (CardView card : snapshot.getCards()) {
            PacketData.writeUuid(buffer, card.getId());
            buffer.writeInt(card.getNumber());
            PacketData.writeString(buffer, card.getTitle());
            PacketData.writeString(buffer, card.getDescription());
            PacketData.writeUuid(buffer, card.getColumnId());
            PacketData.writeUuid(buffer, card.getTypeId());
            buffer.writeInt(
                card.getPriority()
                    .ordinal());
            PacketData.writeUuid(buffer, card.getCreatorId());
            PacketData.writeString(buffer, card.getCreatorName());
            buffer.writeLong(card.getCreatedAt());
            PacketData.writeOptionalMaterial(buffer, card.getIcon());
            buffer.writeInt(
                card.getTasks()
                    .size());
            for (TaskView task : card.getTasks()) {
                PacketData.writeUuid(buffer, task.getId());
                PacketData.writeString(buffer, task.getText());
                buffer.writeBoolean(task.isDone());
            }
            buffer.writeInt(
                card.getComments()
                    .size());
            for (CommentView comment : card.getComments()) {
                PacketData.writeUuid(buffer, comment.getId());
                PacketData.writeUuid(buffer, comment.getAuthorId());
                PacketData.writeString(buffer, comment.getAuthorName());
                buffer.writeLong(comment.getCreatedAt());
                PacketData.writeString(buffer, comment.getText());
            }
            buffer.writeInt(
                card.getAssigneeIds()
                    .size());
            for (UUID id : card.getAssigneeIds()) PacketData.writeUuid(buffer, id);
            buffer.writeInt(
                card.getRequirements()
                    .size());
            for (RequirementView requirement : card.getRequirements()) RequirementWireCodec.write(buffer, requirement);
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        ProjectSummary project = PacketData.readProject(buffer);
        BoardSettings settings = PacketData.readSettings(buffer);
        int memberCount = readCount(buffer, 1024);
        List<MemberSummary> members = new ArrayList<MemberSummary>(memberCount);
        for (int index = 0; index < memberCount; index++) {
            members.add(
                new MemberSummary(
                    PacketData.readUuid(buffer),
                    PacketData.readString(buffer, 128),
                    buffer.readBoolean()));
        }
        int cardCount = readCount(buffer, 4096);
        List<CardView> cards = new ArrayList<CardView>(cardCount);
        for (int index = 0; index < cardCount; index++) {
            UUID id = PacketData.readUuid(buffer);
            int number = buffer.readInt();
            String title = PacketData.readString(buffer, 256);
            String description = PacketData.readString(buffer, 2048);
            UUID column = PacketData.readUuid(buffer);
            UUID type = PacketData.readUuid(buffer);
            Priority priority = Priority.fromOrdinal(buffer.readInt());
            UUID creator = PacketData.readUuid(buffer);
            String creatorName = PacketData.readString(buffer, 128);
            long created = buffer.readLong();
            ItemKey icon = PacketData.readOptionalMaterial(buffer);
            int taskCount = readCount(buffer, 1024);
            List<TaskView> tasks = new ArrayList<TaskView>(taskCount);
            for (int taskIndex = 0; taskIndex < taskCount; taskIndex++) tasks.add(
                new TaskView(PacketData.readUuid(buffer), PacketData.readString(buffer, 1024), buffer.readBoolean()));
            int commentCount = readCount(buffer, 1024);
            List<CommentView> comments = new ArrayList<CommentView>(commentCount);
            for (int commentIndex = 0; commentIndex < commentCount; commentIndex++) comments.add(
                new CommentView(
                    PacketData.readUuid(buffer),
                    PacketData.readUuid(buffer),
                    PacketData.readString(buffer, 128),
                    buffer.readLong(),
                    PacketData.readString(buffer, 4096)));
            int assignedCount = readCount(buffer, 1024);
            Set<UUID> assignees = new LinkedHashSet<UUID>();
            for (int assignedIndex = 0; assignedIndex < assignedCount; assignedIndex++)
                assignees.add(PacketData.readUuid(buffer));
            int requirementCount = readCount(buffer, 4096);
            int[] remaining = { 4096 };
            List<RequirementView> requirements = new ArrayList<RequirementView>(requirementCount);
            for (int requirementIndex = 0; requirementIndex < requirementCount; requirementIndex++) {
                requirements.add(RequirementWireCodec.read(buffer, 1, remaining));
            }
            if (column == null) column = settings.firstColumn();
            cards.add(
                new CardView(
                    id,
                    number,
                    title,
                    description,
                    column,
                    type,
                    priority,
                    creator,
                    creatorName,
                    created,
                    icon,
                    requirements,
                    assignees,
                    tasks,
                    comments));
        }
        snapshot = new BoardSnapshot(project, members, cards, settings);
    }

    private static int readCount(ByteBuf buffer, int maximum) {
        int count = buffer.readInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid collection count: " + count);
        return count;
    }
}
