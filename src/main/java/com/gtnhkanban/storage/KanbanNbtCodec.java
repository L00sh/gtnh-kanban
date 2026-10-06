package com.gtnhkanban.storage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.gtnhkanban.model.CardStatus;
import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.ItemRequirement;
import com.gtnhkanban.model.KanbanCard;
import com.gtnhkanban.model.KanbanProject;

/** NBT codec that keeps item registry names as strings so unavailable mods do not lose checklist data. */
public final class KanbanNbtCodec {

    private static final int NBT_COMPOUND = 10;
    private static final String PROJECT_ID = "id";
    private static final String PROJECT_NAME = "name";
    private static final String OWNER_ID = "owner";
    private static final String MEMBERS = "members";
    private static final String CARDS = "cards";
    private static final String CARD_ID = "id";
    private static final String CARD_TITLE = "title";
    private static final String CARD_DESCRIPTION = "description";
    private static final String CARD_STATUS = "status";
    private static final String REQUIREMENTS = "requirements";
    private static final String REQUIREMENT_ID = "id";
    private static final String ITEM_NAME = "item";
    private static final String ITEM_METADATA = "metadata";
    private static final String QUANTITY = "quantity";
    private static final String COMPLETE = "complete";

    private KanbanNbtCodec() {}

    public static void writeProject(NBTTagCompound target, KanbanProject project) {
        target.setString(
            PROJECT_ID,
            project.getId()
                .toString());
        target.setString(PROJECT_NAME, project.getName());
        target.setString(
            OWNER_ID,
            project.getOwnerId()
                .toString());

        NBTTagList members = new NBTTagList();
        for (UUID memberId : project.getMemberIds()) {
            NBTTagCompound member = new NBTTagCompound();
            member.setString(PROJECT_ID, memberId.toString());
            members.appendTag(member);
        }
        target.setTag(MEMBERS, members);

        NBTTagList cards = new NBTTagList();
        for (KanbanCard card : project.getCards()) {
            cards.appendTag(writeCard(card));
        }
        target.setTag(CARDS, cards);
    }

    public static KanbanProject readProject(NBTTagCompound source) {
        Set<UUID> memberIds = new LinkedHashSet<UUID>();
        NBTTagList members = source.getTagList(MEMBERS, NBT_COMPOUND);
        for (int index = 0; index < members.tagCount(); index++) {
            memberIds.add(readUuid(members.getCompoundTagAt(index), PROJECT_ID));
        }

        List<KanbanCard> cards = new ArrayList<KanbanCard>();
        NBTTagList encodedCards = source.getTagList(CARDS, NBT_COMPOUND);
        for (int index = 0; index < encodedCards.tagCount(); index++) {
            cards.add(readCard(encodedCards.getCompoundTagAt(index)));
        }

        return new KanbanProject(
            readUuid(source, PROJECT_ID),
            source.getString(PROJECT_NAME),
            readUuid(source, OWNER_ID),
            memberIds,
            cards);
    }

    private static NBTTagCompound writeCard(KanbanCard card) {
        NBTTagCompound target = new NBTTagCompound();
        target.setString(
            CARD_ID,
            card.getId()
                .toString());
        target.setString(CARD_TITLE, card.getTitle());
        target.setString(CARD_DESCRIPTION, card.getDescription());
        target.setString(
            CARD_STATUS,
            card.getStatus()
                .name());

        NBTTagList requirements = new NBTTagList();
        for (ItemRequirement requirement : card.getRequirements()) {
            NBTTagCompound encodedRequirement = new NBTTagCompound();
            encodedRequirement.setString(
                REQUIREMENT_ID,
                requirement.getId()
                    .toString());
            encodedRequirement.setString(
                ITEM_NAME,
                requirement.getItem()
                    .getRegistryName());
            encodedRequirement.setInteger(
                ITEM_METADATA,
                requirement.getItem()
                    .getMetadata());
            encodedRequirement.setInteger(QUANTITY, requirement.getQuantity());
            encodedRequirement.setBoolean(COMPLETE, requirement.isComplete());
            requirements.appendTag(encodedRequirement);
        }
        target.setTag(REQUIREMENTS, requirements);
        return target;
    }

    private static KanbanCard readCard(NBTTagCompound source) {
        List<ItemRequirement> requirements = new ArrayList<ItemRequirement>();
        NBTTagList encodedRequirements = source.getTagList(REQUIREMENTS, NBT_COMPOUND);
        for (int index = 0; index < encodedRequirements.tagCount(); index++) {
            NBTTagCompound requirement = encodedRequirements.getCompoundTagAt(index);
            requirements.add(
                new ItemRequirement(
                    readUuid(requirement, REQUIREMENT_ID),
                    new ItemKey(requirement.getString(ITEM_NAME), requirement.getInteger(ITEM_METADATA)),
                    requirement.getInteger(QUANTITY),
                    requirement.getBoolean(COMPLETE)));
        }
        return new KanbanCard(
            readUuid(source, CARD_ID),
            source.getString(CARD_TITLE),
            source.getString(CARD_DESCRIPTION),
            readStatus(source.getString(CARD_STATUS)),
            requirements);
    }

    private static CardStatus readStatus(String name) {
        try {
            return CardStatus.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return CardStatus.TODO;
        }
    }

    private static UUID readUuid(NBTTagCompound source, String key) {
        return UUID.fromString(source.getString(key));
    }
}
