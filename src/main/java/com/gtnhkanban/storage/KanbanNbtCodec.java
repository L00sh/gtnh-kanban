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
        return readProject(source, new ArrayList<String>());
    }

    /**
     * Decodes a project, repairing or skipping damaged entries instead of failing the whole project. Each repair is
     * described in {@code problems}. Throws only when the owner is unreadable, since access cannot be reconstructed.
     */
    public static KanbanProject readProject(NBTTagCompound source, List<String> problems) {
        UUID ownerId = readUuid(source, OWNER_ID);
        UUID projectId = readUuidOrReplace(source, PROJECT_ID, "project", problems);

        Set<UUID> memberIds = new LinkedHashSet<UUID>();
        NBTTagList members = source.getTagList(MEMBERS, NBT_COMPOUND);
        for (int index = 0; index < members.tagCount(); index++) {
            try {
                memberIds.add(readUuid(members.getCompoundTagAt(index), PROJECT_ID));
            } catch (RuntimeException exception) {
                problems.add("Skipped unreadable member in project " + projectId + ": " + exception);
            }
        }

        List<KanbanCard> cards = new ArrayList<KanbanCard>();
        NBTTagList encodedCards = source.getTagList(CARDS, NBT_COMPOUND);
        for (int index = 0; index < encodedCards.tagCount(); index++) {
            try {
                cards.add(readCard(encodedCards.getCompoundTagAt(index), problems));
            } catch (RuntimeException exception) {
                problems.add("Skipped unreadable card in project " + projectId + ": " + exception);
            }
        }

        return new KanbanProject(projectId, source.getString(PROJECT_NAME), ownerId, memberIds, cards);
    }

    private static NBTTagCompound writeCard(KanbanCard card) {
        NBTTagCompound target = new NBTTagCompound();
        target.setString(
            CARD_ID,
            card.getId()
                .toString());
        NBTTagList assigned = new NBTTagList();
        for (UUID memberId : card.getAssigneeIds()) {
            NBTTagCompound member = new NBTTagCompound();
            member.setString("id", memberId.toString());
            assigned.appendTag(member);
        }
        target.setTag("assignees", assigned);
        target.setString(CARD_TITLE, card.getTitle());
        target.setString(CARD_DESCRIPTION, card.getDescription());
        target.setString(
            CARD_STATUS,
            card.getStatus()
                .name());

        NBTTagList requirements = new NBTTagList();
        for (ItemRequirement requirement : card.getRequirements())
            requirements.appendTag(writeRequirement(requirement));
        target.setTag(REQUIREMENTS, requirements);
        return target;
    }

    private static KanbanCard readCard(NBTTagCompound source, List<String> problems) {
        UUID cardId = readUuidOrReplace(source, CARD_ID, "card", problems);
        List<ItemRequirement> requirements = new ArrayList<ItemRequirement>();
        NBTTagList encodedRequirements = source.getTagList(REQUIREMENTS, NBT_COMPOUND);
        // Legacy versions did not cap flat roots. Preserve them so a later save cannot discard projects.
        int[] remaining = { Math.max(4096, encodedRequirements.tagCount()) };
        readRequirements(encodedRequirements, 1, remaining, cardId, problems, requirements);
        KanbanCard card = new KanbanCard(
            cardId,
            source.getString(CARD_TITLE),
            source.getString(CARD_DESCRIPTION),
            readStatus(source.getString(CARD_STATUS)),
            requirements);
        NBTTagList assigned = source.getTagList("assignees", NBT_COMPOUND);
        for (int index = 0; index < assigned.tagCount(); index++) {
            try {
                card.setAssigned(readUuid(assigned.getCompoundTagAt(index), "id"), true);
            } catch (RuntimeException exception) {
                problems.add("Skipped unreadable assignee on card " + cardId + ": " + exception);
            }
        }
        return card;
    }

    private static NBTTagCompound writeRequirement(ItemRequirement requirement) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(
            REQUIREMENT_ID,
            requirement.getId()
                .toString());
        tag.setString(
            ITEM_NAME,
            requirement.getItem()
                .getRegistryName());
        tag.setInteger(
            ITEM_METADATA,
            requirement.getItem()
                .getMetadata());
        tag.setBoolean(
            "fluid",
            requirement.getItem()
                .isFluid());
        tag.setString(
            "itemNbt",
            requirement.getItem()
                .getNbt());
        tag.setInteger(QUANTITY, requirement.getQuantity());
        tag.setBoolean(COMPLETE, requirement.isComplete());
        tag.setInteger("perBatch", requirement.getAmountPerBatch());
        tag.setBoolean("reusable", requirement.isReusable());
        tag.setString("recipeName", requirement.getRecipeName());
        tag.setInteger("recipeOutput", requirement.getRecipeOutput());
        tag.setLong("revision", requirement.getRevision());
        NBTTagList children = new NBTTagList();
        for (ItemRequirement child : requirement.getChildren()) children.appendTag(writeRequirement(child));
        tag.setTag("children", children);
        return tag;
    }

    private static void readRequirements(NBTTagList encoded, int depth, int[] remaining, UUID cardId,
        List<String> problems, List<ItemRequirement> target) {
        for (int index = 0; index < encoded.tagCount(); index++) {
            try {
                target.add(readRequirement(encoded.getCompoundTagAt(index), depth, remaining, cardId, problems));
            } catch (RuntimeException exception) {
                problems.add("Skipped unreadable checklist entry on card " + cardId + ": " + exception);
            }
        }
    }

    private static ItemRequirement readRequirement(NBTTagCompound tag, int depth, int[] remaining, UUID cardId,
        List<String> problems) {
        if (depth > 16 || --remaining[0] < 0) throw new IllegalArgumentException("Saved material tree exceeds limits.");
        List<ItemRequirement> children = new ArrayList<ItemRequirement>();
        readRequirements(tag.getTagList("children", NBT_COMPOUND), depth + 1, remaining, cardId, problems, children);
        int quantity = tag.getInteger(QUANTITY);
        if (quantity < 1) {
            problems.add("Raised checklist quantity " + quantity + " to 1 on card " + cardId);
            quantity = 1;
        }
        return new ItemRequirement(
            readUuidOrReplace(tag, REQUIREMENT_ID, "checklist entry", problems),
            new ItemKey(
                tag.getString(ITEM_NAME),
                tag.getInteger(ITEM_METADATA),
                tag.getBoolean("fluid"),
                tag.getString("itemNbt")),
            quantity,
            tag.getBoolean(COMPLETE),
            tag.getInteger("perBatch"),
            tag.getBoolean("reusable"),
            tag.getString("recipeName"),
            tag.getInteger("recipeOutput"),
            tag.getLong("revision"),
            children);
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

    private static UUID readUuidOrReplace(NBTTagCompound source, String key, String label, List<String> problems) {
        try {
            return readUuid(source, key);
        } catch (IllegalArgumentException exception) {
            UUID replacement = UUID.randomUUID();
            problems.add("Gave " + label + " with unreadable id '" + source.getString(key) + "' new id " + replacement);
            return replacement;
        }
    }
}
