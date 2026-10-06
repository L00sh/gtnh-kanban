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
    private CardStatus status;
    private final List<ItemRequirement> requirements;

    public KanbanCard(UUID id, String title, String description, CardStatus status) {
        this(id, title, description, status, Collections.<ItemRequirement>emptyList());
    }

    public KanbanCard(UUID id, String title, String description, CardStatus status,
        List<ItemRequirement> requirements) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = Objects.requireNonNull(title, "title");
        this.description = description == null ? "" : description;
        this.status = Objects.requireNonNull(status, "status");
        this.requirements = new ArrayList<ItemRequirement>(Objects.requireNonNull(requirements, "requirements"));
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

    public CardStatus getStatus() {
        return status;
    }

    public void setStatus(CardStatus status) {
        this.status = Objects.requireNonNull(status, "status");
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
