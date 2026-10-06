package com.gtnhkanban.storage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.gtnhkanban.model.BoardColumn;
import com.gtnhkanban.model.BoardSettings;
import com.gtnhkanban.model.CardComment;
import com.gtnhkanban.model.CardStatus;
import com.gtnhkanban.model.CardTask;
import com.gtnhkanban.model.CardType;
import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.ItemRequirement;
import com.gtnhkanban.model.KanbanCard;
import com.gtnhkanban.model.KanbanProject;
import com.gtnhkanban.model.Priority;

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
        target.setInteger("nextCardNumber", project.getNextCardNumber());
        if (project.getIcon() != null) target.setTag("icon", writeMaterial(project.getIcon()));
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

        KanbanProject project = new KanbanProject(projectId, source.getString(PROJECT_NAME), ownerId, memberIds, cards);
        project.setIcon(readOptionalMaterial(source, "icon", problems));
        numberCards(project, source.getInteger("nextCardNumber"), problems);
        return project;
    }

    /** Gives cards saved before numbering (or with clashing numbers) the next free numbers, in board order. */
    private static void numberCards(KanbanProject project, int storedNext, List<String> problems) {
        Set<Integer> used = new HashSet<Integer>();
        int highest = 0;
        for (KanbanCard card : project.getCards()) {
            if (card.getNumber() > 0 && used.add(card.getNumber())) highest = Math.max(highest, card.getNumber());
        }
        int next = Math.max(storedNext, highest + 1);
        used.clear();
        for (KanbanCard card : project.getCards()) {
            if (card.getNumber() > 0 && used.add(card.getNumber())) continue;
            if (card.getNumber() > 0) problems.add("Renumbered card with duplicate number #" + card.getNumber());
            card.setNumber(next++);
            used.add(card.getNumber());
        }
        project.setNextCardNumber(next);
    }

    /** Server-wide columns and types; null when the save predates them. */
    public static BoardSettings readSettings(NBTTagCompound source, List<String> problems) {
        List<BoardColumn> columns = new ArrayList<BoardColumn>();
        Set<UUID> seen = new HashSet<UUID>();
        NBTTagList encodedColumns = source.getTagList("columns", NBT_COMPOUND);
        for (int index = 0; index < encodedColumns.tagCount(); index++) {
            NBTTagCompound column = encodedColumns.getCompoundTagAt(index);
            UUID id = readUuidOrReplace(column, "id", "column", problems);
            if (seen.add(id)) columns.add(new BoardColumn(id, column.getString("name")));
        }
        List<CardType> types = new ArrayList<CardType>();
        NBTTagList encodedTypes = source.getTagList("types", NBT_COMPOUND);
        for (int index = 0; index < encodedTypes.tagCount(); index++) {
            NBTTagCompound type = encodedTypes.getCompoundTagAt(index);
            UUID id = readUuidOrReplace(type, "id", "card type", problems);
            if (seen.add(id)) types.add(new CardType(id, type.getString("name"), type.getInteger("color")));
        }
        if (columns.isEmpty()) {
            problems.add("Saved board settings had no columns; using the default columns.");
            return new BoardSettings(
                BoardSettings.defaults()
                    .getColumns(),
                types);
        }
        return new BoardSettings(columns, types);
    }

    public static NBTTagCompound writeSettings(BoardSettings settings) {
        NBTTagCompound target = new NBTTagCompound();
        NBTTagList columns = new NBTTagList();
        for (BoardColumn column : settings.getColumns()) {
            NBTTagCompound encoded = new NBTTagCompound();
            encoded.setString(
                "id",
                column.getId()
                    .toString());
            encoded.setString("name", column.getName());
            columns.appendTag(encoded);
        }
        target.setTag("columns", columns);
        NBTTagList types = new NBTTagList();
        for (CardType type : settings.getTypes()) {
            NBTTagCompound encoded = new NBTTagCompound();
            encoded.setString(
                "id",
                type.getId()
                    .toString());
            encoded.setString("name", type.getName());
            encoded.setInteger("color", type.getColor());
            types.appendTag(encoded);
        }
        target.setTag("types", types);
        return target;
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
            "column",
            card.getColumnId()
                .toString());
        target.setInteger("number", card.getNumber());
        if (card.getTypeId() != null) target.setString(
            "type",
            card.getTypeId()
                .toString());
        target.setString(
            "priority",
            card.getPriority()
                .name());
        if (card.getCreatorId() != null) target.setString(
            "creator",
            card.getCreatorId()
                .toString());
        target.setLong("created", card.getCreatedAt());
        if (card.getIcon() != null) target.setTag("icon", writeMaterial(card.getIcon()));
        NBTTagList tasks = new NBTTagList();
        for (CardTask task : card.getTasks()) {
            NBTTagCompound encoded = new NBTTagCompound();
            encoded.setString(
                "id",
                task.getId()
                    .toString());
            encoded.setString("text", task.getText());
            encoded.setBoolean("done", task.isDone());
            tasks.appendTag(encoded);
        }
        target.setTag("tasks", tasks);
        NBTTagList comments = new NBTTagList();
        for (CardComment comment : card.getComments()) {
            NBTTagCompound encoded = new NBTTagCompound();
            encoded.setString(
                "id",
                comment.getId()
                    .toString());
            if (comment.getAuthorId() != null) encoded.setString(
                "author",
                comment.getAuthorId()
                    .toString());
            encoded.setLong("created", comment.getCreatedAt());
            encoded.setString("text", comment.getText());
            comments.appendTag(encoded);
        }
        target.setTag("comments", comments);

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
            readColumn(source, problems),
            requirements);
        card.setNumber(source.getInteger("number"));
        card.setTypeId(readOptionalUuid(source, "type", problems));
        card.setPriority(Priority.fromName(source.getString("priority")));
        card.setCreatorId(readOptionalUuid(source, "creator", problems));
        card.setCreatedAt(source.getLong("created"));
        card.setIcon(readOptionalMaterial(source, "icon", problems));
        NBTTagList tasks = source.getTagList("tasks", NBT_COMPOUND);
        for (int index = 0; index < tasks.tagCount(); index++) {
            NBTTagCompound task = tasks.getCompoundTagAt(index);
            card.addTask(
                new CardTask(
                    readUuidOrReplace(task, "id", "task", problems),
                    task.getString("text"),
                    task.getBoolean("done")));
        }
        NBTTagList comments = source.getTagList("comments", NBT_COMPOUND);
        for (int index = 0; index < comments.tagCount(); index++) {
            NBTTagCompound comment = comments.getCompoundTagAt(index);
            card.addComment(
                new CardComment(
                    readUuidOrReplace(comment, "id", "comment", problems),
                    readOptionalUuid(comment, "author", problems),
                    comment.getLong("created"),
                    comment.getString("text")));
        }
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

    /** Cards saved before columns existed carry a status; it maps to the matching default column. */
    private static UUID readColumn(NBTTagCompound source, List<String> problems) {
        if (source.hasKey("column")) {
            UUID column = readOptionalUuid(source, "column", problems);
            if (column != null) return column;
        }
        return BoardSettings.defaults()
            .legacyColumn(readStatus(source.getString(CARD_STATUS)));
    }

    private static UUID readOptionalUuid(NBTTagCompound source, String key, List<String> problems) {
        if (!source.hasKey(key)) return null;
        try {
            return readUuid(source, key);
        } catch (IllegalArgumentException exception) {
            problems.add("Dropped unreadable " + key + " id '" + source.getString(key) + "'");
            return null;
        }
    }

    private static NBTTagCompound writeMaterial(ItemKey material) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(ITEM_NAME, material.getRegistryName());
        tag.setInteger(ITEM_METADATA, material.getMetadata());
        tag.setBoolean("fluid", material.isFluid());
        tag.setString("itemNbt", material.getNbt());
        return tag;
    }

    private static ItemKey readOptionalMaterial(NBTTagCompound source, String key, List<String> problems) {
        if (!source.hasKey(key, NBT_COMPOUND)) return null;
        NBTTagCompound tag = source.getCompoundTag(key);
        if (tag.getString(ITEM_NAME)
            .isEmpty()) {
            problems.add("Dropped " + key + " with no item name");
            return null;
        }
        return new ItemKey(
            tag.getString(ITEM_NAME),
            tag.getInteger(ITEM_METADATA),
            tag.getBoolean("fluid"),
            tag.getString("itemNbt"));
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
