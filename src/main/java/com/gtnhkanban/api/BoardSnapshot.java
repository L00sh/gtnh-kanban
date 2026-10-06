package com.gtnhkanban.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class BoardSnapshot {

    private final ProjectSummary project;
    private final List<MemberSummary> members;
    private final List<CardView> cards;

    public BoardSnapshot(ProjectSummary project, List<MemberSummary> members, List<CardView> cards) {
        this.project = Objects.requireNonNull(project, "project");
        this.members = Collections
            .unmodifiableList(new ArrayList<MemberSummary>(Objects.requireNonNull(members, "members")));
        this.cards = Collections.unmodifiableList(new ArrayList<CardView>(Objects.requireNonNull(cards, "cards")));
    }

    public ProjectSummary getProject() {
        return project;
    }

    public List<MemberSummary> getMembers() {
        return members;
    }

    public List<CardView> getCards() {
        return cards;
    }
}
