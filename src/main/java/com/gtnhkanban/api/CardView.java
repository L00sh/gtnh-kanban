package com.gtnhkanban.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.gtnhkanban.model.CardLink;
import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.Priority;

/** Immutable card snapshot. Use the {@code with...} methods to derive a locally edited copy. */
public final class CardView {

    private final UUID id;
    private final int number;
    private final String title;
    private final String description;
    private final UUID columnId;
    private final UUID typeId;
    private final Priority priority;
    private final UUID creatorId;
    private final String creatorName;
    private final long createdAt;
    private final ItemKey icon;
    private final List<RequirementView> requirements;
    private final Set<UUID> assigneeIds;
    private final List<TaskView> tasks;
    private final List<CommentView> comments;
    private final List<UUID> dependencyIds;
    private final List<UUID> blockerIds;

    /** A minimal card, mainly for tests: no number, type, creator, icon, tasks or comments. */
    public CardView(UUID id, String title, String description, UUID columnId, List<RequirementView> requirements) {
        this(
            id,
            0,
            title,
            description,
            columnId,
            null,
            Priority.NONE,
            null,
            "",
            0,
            null,
            requirements,
            Collections.<UUID>emptySet(),
            Collections.<TaskView>emptyList(),
            Collections.<CommentView>emptyList());
    }

    public CardView(UUID id, int number, String title, String description, UUID columnId, UUID typeId,
        Priority priority, UUID creatorId, String creatorName, long createdAt, ItemKey icon,
        List<RequirementView> requirements, Set<UUID> assigneeIds, List<TaskView> tasks, List<CommentView> comments) {
        this(
            id,
            number,
            title,
            description,
            columnId,
            typeId,
            priority,
            creatorId,
            creatorName,
            createdAt,
            icon,
            requirements,
            assigneeIds,
            tasks,
            comments,
            Collections.<UUID>emptyList(),
            Collections.<UUID>emptyList());
    }

    /**
     * @param dependencyIds cards that must be done before this one can be
     * @param blockerIds    cards holding this one up
     */
    public CardView(UUID id, int number, String title, String description, UUID columnId, UUID typeId,
        Priority priority, UUID creatorId, String creatorName, long createdAt, ItemKey icon,
        List<RequirementView> requirements, Set<UUID> assigneeIds, List<TaskView> tasks, List<CommentView> comments,
        List<UUID> dependencyIds, List<UUID> blockerIds) {
        this.id = Objects.requireNonNull(id, "id");
        this.number = number;
        this.title = Objects.requireNonNull(title, "title");
        this.description = description == null ? "" : description;
        this.columnId = Objects.requireNonNull(columnId, "columnId");
        this.typeId = typeId;
        this.priority = priority == null ? Priority.NONE : priority;
        this.creatorId = creatorId;
        this.creatorName = creatorName == null ? "" : creatorName;
        this.createdAt = createdAt;
        this.icon = icon;
        this.requirements = Collections
            .unmodifiableList(new ArrayList<RequirementView>(Objects.requireNonNull(requirements, "requirements")));
        this.assigneeIds = Collections.unmodifiableSet(new LinkedHashSet<UUID>(assigneeIds));
        this.tasks = Collections.unmodifiableList(new ArrayList<TaskView>(tasks));
        this.comments = Collections.unmodifiableList(new ArrayList<CommentView>(comments));
        this.dependencyIds = Collections.unmodifiableList(new ArrayList<UUID>(dependencyIds));
        this.blockerIds = Collections.unmodifiableList(new ArrayList<UUID>(blockerIds));
    }

    public CardView withColumn(UUID column) {
        return new CardView(
            id,
            number,
            title,
            description,
            column,
            typeId,
            priority,
            creatorId,
            creatorName,
            createdAt,
            icon,
            requirements,
            assigneeIds,
            tasks,
            comments,
            dependencyIds,
            blockerIds);
    }

    public CardView withRequirements(List<RequirementView> rows) {
        return new CardView(
            id,
            number,
            title,
            description,
            columnId,
            typeId,
            priority,
            creatorId,
            creatorName,
            createdAt,
            icon,
            rows,
            assigneeIds,
            tasks,
            comments,
            dependencyIds,
            blockerIds);
    }

    public CardView withAssignees(Set<UUID> assignees) {
        return new CardView(
            id,
            number,
            title,
            description,
            columnId,
            typeId,
            priority,
            creatorId,
            creatorName,
            createdAt,
            icon,
            requirements,
            assignees,
            tasks,
            comments,
            dependencyIds,
            blockerIds);
    }

    public CardView withTasks(List<TaskView> taskViews) {
        return new CardView(
            id,
            number,
            title,
            description,
            columnId,
            typeId,
            priority,
            creatorId,
            creatorName,
            createdAt,
            icon,
            requirements,
            assigneeIds,
            taskViews,
            comments,
            dependencyIds,
            blockerIds);
    }

    /** The cards linked from this one in the given way, in the order they were added. */
    public List<UUID> getLinks(CardLink link) {
        return link == CardLink.DEPENDS_ON ? dependencyIds : blockerIds;
    }

    public UUID getId() {
        return id;
    }

    /** Per-project number, shown as #n; 0 if not yet numbered. */
    public int getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public UUID getColumnId() {
        return columnId;
    }

    /** Null when the card has no type. */
    public UUID getTypeId() {
        return typeId;
    }

    public Priority getPriority() {
        return priority;
    }

    /** Null when unknown (cards created before creators were recorded). */
    public UUID getCreatorId() {
        return creatorId;
    }

    /** Empty when unknown. */
    public String getCreatorName() {
        return creatorName;
    }

    /** Epoch milliseconds, or 0 when unknown. */
    public long getCreatedAt() {
        return createdAt;
    }

    /** Null when the card has no icon. */
    public ItemKey getIcon() {
        return icon;
    }

    public List<RequirementView> getRequirements() {
        return requirements;
    }

    public Set<UUID> getAssigneeIds() {
        return assigneeIds;
    }

    public List<TaskView> getTasks() {
        return tasks;
    }

    public List<CommentView> getComments() {
        return comments;
    }

    /** Checklist progress: finished tasks and finished top-level item rows, out of all of them. */
    public int progressDone() {
        int done = 0;
        for (TaskView task : tasks) if (task.isDone()) done++;
        for (RequirementView row : requirements) if (row.isComplete()) done++;
        return done;
    }

    public int progressTotal() {
        return tasks.size() + requirements.size();
    }
}
