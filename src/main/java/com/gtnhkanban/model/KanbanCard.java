package com.gtnhkanban.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class KanbanCard {

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
            if (requirement.getId()
                .equals(requirementId)) {
                return requirement;
            }
        }
        return null;
    }
}
