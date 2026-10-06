package com.gtnhkanban.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.gtnhkanban.model.CardStatus;

public final class CardView {

    private final Set<UUID> assigneeIds;
    private final UUID id;
    private final String title;
    private final String description;
    private final CardStatus status;
    private final List<RequirementView> requirements;

    public CardView(UUID id, String title, String description, CardStatus status, List<RequirementView> requirements) {
        this(id, title, description, status, requirements, Collections.<UUID>emptySet());
    }

    public CardView(UUID id, String title, String description, CardStatus status, List<RequirementView> requirements,
        Set<UUID> assigneeIds) {
        this.assigneeIds = Collections.unmodifiableSet(new LinkedHashSet<UUID>(assigneeIds));
        this.id = Objects.requireNonNull(id, "id");
        this.title = Objects.requireNonNull(title, "title");
        this.description = description == null ? "" : description;
        this.status = Objects.requireNonNull(status, "status");
        this.requirements = Collections
            .unmodifiableList(new ArrayList<RequirementView>(Objects.requireNonNull(requirements, "requirements")));
    }

    public Set<UUID> getAssigneeIds() {
        return assigneeIds;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public CardStatus getStatus() {
        return status;
    }

    public List<RequirementView> getRequirements() {
        return requirements;
    }
}
