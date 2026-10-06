package com.gtnhkanban.network.message;

import java.util.ArrayList;
import java.util.List;

import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.CardView;
import com.gtnhkanban.api.MemberSummary;
import com.gtnhkanban.api.ProjectSummary;
import com.gtnhkanban.api.RequirementView;
import com.gtnhkanban.model.CardStatus;
import com.gtnhkanban.model.ItemKey;

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
        ProjectSummary project = snapshot.getProject();
        PacketData.writeUuid(buffer, project.getId());
        PacketData.writeString(buffer, project.getName());
        buffer.writeBoolean(project.isActorIsOwner());
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
            PacketData.writeString(buffer, card.getTitle());
            PacketData.writeString(buffer, card.getDescription());
            PacketData.writeStatus(buffer, card.getStatus());
            buffer.writeInt(
                card.getRequirements()
                    .size());
            for (RequirementView requirement : card.getRequirements()) {
                PacketData.writeUuid(buffer, requirement.getId());
                PacketData.writeString(
                    buffer,
                    requirement.getItem()
                        .getRegistryName());
                buffer.writeInt(
                    requirement.getItem()
                        .getMetadata());
                buffer.writeInt(requirement.getQuantity());
                buffer.writeBoolean(requirement.isComplete());
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        ProjectSummary project = new ProjectSummary(
            PacketData.readUuid(buffer),
            PacketData.readString(buffer, 256),
            buffer.readBoolean());
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
            java.util.UUID id = PacketData.readUuid(buffer);
            String title = PacketData.readString(buffer, 256);
            String description = PacketData.readString(buffer, 2048);
            CardStatus status = PacketData.readStatus(buffer);
            int requirementCount = readCount(buffer, 4096);
            List<RequirementView> requirements = new ArrayList<RequirementView>(requirementCount);
            for (int requirementIndex = 0; requirementIndex < requirementCount; requirementIndex++) {
                requirements.add(
                    new RequirementView(
                        PacketData.readUuid(buffer),
                        new ItemKey(PacketData.readString(buffer, 512), buffer.readInt()),
                        buffer.readInt(),
                        buffer.readBoolean()));
            }
            cards.add(new CardView(id, title, description, status, requirements));
        }
        snapshot = new BoardSnapshot(project, members, cards);
    }

    private static int readCount(ByteBuf buffer, int maximum) {
        int count = buffer.readInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid collection count: " + count);
        return count;
    }
}
