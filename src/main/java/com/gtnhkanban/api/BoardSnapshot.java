package com.gtnhkanban.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.gtnhkanban.model.BoardSettings;

public final class BoardSnapshot {

    private final ProjectSummary project;
    private final List<MemberSummary> members;
    private final List<CardView> cards;
    private final BoardSettings settings;

    public BoardSnapshot(ProjectSummary project, List<MemberSummary> members, List<CardView> cards) {
        this(project, members, cards, BoardSettings.defaults());
    }

    public BoardSnapshot(ProjectSummary project, List<MemberSummary> members, List<CardView> cards,
        BoardSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
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

    /** Server-wide columns and card types. */
    public BoardSettings getSettings() {
        return settings;
    }

    /** A copy with different cards, for local edits shown before the server confirms them. */
    public BoardSnapshot withCards(List<CardView> replacement) {
        return new BoardSnapshot(project, members, replacement, settings);
    }
}
