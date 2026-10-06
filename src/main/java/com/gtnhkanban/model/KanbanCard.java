package com.gtnhkanban.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class KanbanCard {

    private final Set<UUID> assigneeIds = new LinkedHashSet<UUID>();
    private final UUID id;
    private String title;
    private String description;
    private UUID columnId;
    private final List<ItemRequirement> requirements;
    private final List<CardTask> tasks = new ArrayList<CardTask>();
    private final List<CardComment> comments = new ArrayList<CardComment>();
    private int number;
    private UUID typeId;
    private Priority priority = Priority.NONE;
    private UUID creatorId;
    private long createdAt;
    private ItemKey icon;

    public KanbanCard(UUID id, String title, String description, UUID columnId) {
        this(id, title, description, columnId, Collections.<ItemRequirement>emptyList());
    }

    public KanbanCard(UUID id, String title, String description, UUID columnId, List<ItemRequirement> requirements) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = Objects.requireNonNull(title, "title");
        this.description = description == null ? "" : description;
        this.columnId = Objects.requireNonNull(columnId, "columnId");
        this.requirements = new ArrayList<ItemRequirement>(Objects.requireNonNull(requirements, "requirements"));
    }

    /** Per-project card number shown as #n; 0 until assigned. */
    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    /** Null when the card has no type. */
    public UUID getTypeId() {
        return typeId;
    }

    public void setTypeId(UUID typeId) {
        this.typeId = typeId;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority == null ? Priority.NONE : priority;
    }

    /** Null for cards created before creators were recorded. */
    public UUID getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(UUID creatorId) {
        this.creatorId = creatorId;
    }

    /** Epoch milliseconds; 0 for cards created before this was recorded. */
    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    /** Null when the card has no icon. */
    public ItemKey getIcon() {
        return icon;
    }

    public void setIcon(ItemKey icon) {
        this.icon = icon;
    }

    public List<CardTask> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

    public void addTask(CardTask task) {
        tasks.add(Objects.requireNonNull(task, "task"));
    }

    public CardTask findTask(UUID taskId) {
        for (CardTask task : tasks) if (task.getId()
            .equals(taskId)) return task;
        return null;
    }

    public boolean removeTask(UUID taskId) {
        CardTask task = findTask(taskId);
        return task != null && tasks.remove(task);
    }

    public List<CardComment> getComments() {
        return Collections.unmodifiableList(comments);
    }

    public void addComment(CardComment comment) {
        comments.add(Objects.requireNonNull(comment, "comment"));
    }

    public CardComment findComment(UUID commentId) {
        for (CardComment comment : comments) if (comment.getId()
            .equals(commentId)) return comment;
        return null;
    }

    public boolean removeComment(UUID commentId) {
        CardComment comment = findComment(commentId);
        return comment != null && comments.remove(comment);
    }

    public Set<UUID> getAssigneeIds() {
        return Collections.unmodifiableSet(assigneeIds);
    }

    public void setAssigned(UUID memberId, boolean assigned) {
        if (assigned) assigneeIds.add(Objects.requireNonNull(memberId, "memberId"));
        else assigneeIds.remove(memberId);
    }

    public int requirementCount() {
        int count = 0;
        for (ItemRequirement requirement : requirements) count += requirement.nodeCount();
        return count;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = Objects.requireNonNull(title, "title");
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public UUID getColumnId() {
        return columnId;
    }

    public void setColumnId(UUID columnId) {
        this.columnId = Objects.requireNonNull(columnId, "columnId");
    }

    public List<ItemRequirement> getRequirements() {
        return Collections.unmodifiableList(requirements);
    }

    public void addRequirement(ItemRequirement requirement) {
        requirements.add(Objects.requireNonNull(requirement, "requirement"));
    }

    public ItemRequirement findRequirement(UUID requirementId) {
        for (ItemRequirement requirement : requirements) {
            ItemRequirement found = requirement.find(requirementId);
            if (found != null) return found;
        }
        return null;
    }

    public boolean isRootRequirement(UUID requirementId) {
        for (ItemRequirement requirement : requirements) if (requirement.getId()
            .equals(requirementId)) return true;
        return false;
    }

    public List<ItemRequirement> requirementPath(UUID requirementId) {
        List<ItemRequirement> path = new ArrayList<ItemRequirement>();
        for (ItemRequirement root : requirements) if (findPath(root, requirementId, path)) return path;
        return path;
    }

    private boolean findPath(ItemRequirement row, UUID id, List<ItemRequirement> path) {
        path.add(row);
        if (row.getId()
            .equals(id)) return true;
        for (ItemRequirement child : row.getChildren()) if (findPath(child, id, path)) return true;
        path.remove(path.size() - 1);
        return false;
    }

    public boolean removeRequirement(UUID requirementId) {
        for (int i = 0; i < requirements.size(); i++) {
            if (requirements.get(i)
                .getId()
                .equals(requirementId)) {
                requirements.remove(i);
                return true;
            }
            if (requirements.get(i)
                .removeDescendant(requirementId)) return true;
        }
        return false;
    }
}
