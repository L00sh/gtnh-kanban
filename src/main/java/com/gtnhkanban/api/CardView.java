package com.gtnhkanban.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.gtnhkanban.model.CardStatus;

public final class CardView {

    private final UUID id;
    private final String title;
    private final String description;
    private final CardStatus status;
    private final List<RequirementView> requirements;

    public CardView(UUID id, String title, String description, CardStatus status, List<RequirementView> requirements) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = Objects.requireNonNull(title, "title");
        this.description = description == null ? "" : description;
        this.status = Objects.requireNonNull(status, "status");
        this.requirements = Collections
            .unmodifiableList(new ArrayList<RequirementView>(Objects.requireNonNull(requirements, "requirements")));
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
