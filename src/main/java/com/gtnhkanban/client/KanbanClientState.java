package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.CardView;
import com.gtnhkanban.api.MemberSummary;
import com.gtnhkanban.api.ProjectSummary;
import com.gtnhkanban.api.RequirementView;
import com.gtnhkanban.model.CardStatus;

/** Client-thread cache of server snapshots used only for rendering GUI screens. */
public final class KanbanClientState {

    private static final List<ProjectSummary> PROJECTS = new ArrayList<ProjectSummary>();
    private static BoardSnapshot board;
    private static UUID pinnedProjectId;
    private static CardView pinnedCard;
    private static String resultMessage = "";
    private static boolean resultSuccess = true;

    private KanbanClientState() {}

    public static void setProjects(List<ProjectSummary> projects) {
        PROJECTS.clear();
        PROJECTS.addAll(projects);
        if (board != null) {
            boolean canStillAccessBoard = false;
            for (ProjectSummary project : projects) {
                if (project.getId()
                    .equals(
                        board.getProject()
                            .getId())) {
                    canStillAccessBoard = true;
                    break;
                }
            }
            if (!canStillAccessBoard) {
                UUID inaccessibleProjectId = board.getProject()
                    .getId();
                board = null;
                if (inaccessibleProjectId.equals(pinnedProjectId)) clearPinnedCard();
            }
        }
    }

    public static List<ProjectSummary> getProjects() {
        return Collections.unmodifiableList(PROJECTS);
    }

    public static void setBoard(BoardSnapshot snapshot) {
        board = snapshot;
        if (snapshot != null && pinnedCard != null
            && snapshot.getProject()
                .getId()
                .equals(pinnedProjectId)) {
            CardView updated = null;
            for (CardView card : snapshot.getCards()) {
                if (card.getId()
                    .equals(pinnedCard.getId())) {
                    updated = card;
                    break;
                }
            }
            if (updated == null || updated.getStatus() != com.gtnhkanban.model.CardStatus.IN_PROGRESS) {
                clearPinnedCard();
            } else pinnedCard = updated;
        }
    }

    public static BoardSnapshot getBoard() {
        return board;
    }

    public static void moveCardLocally(UUID projectId, UUID cardId, CardStatus status) {
        if (board == null || !board.getProject()
            .getId()
            .equals(projectId)) return;
        List<CardView> cards = new ArrayList<CardView>();
        boolean found = false;
        for (CardView card : board.getCards()) {
            if (card.getId()
                .equals(cardId)) {
                cards.add(
                    new CardView(
                        card.getId(),
                        card.getTitle(),
                        card.getDescription(),
                        status,
                        new ArrayList<RequirementView>(card.getRequirements()),
                        card.getAssigneeIds()));
                found = true;
            } else cards.add(card);
        }
        if (found) setBoard(new BoardSnapshot(board.getProject(), board.getMembers(), cards));
    }

    public static void removeRequirementLocally(UUID projectId, UUID cardId, UUID requirementId) {
        if (board == null || !board.getProject()
            .getId()
            .equals(projectId)) return;
        List<CardView> cards = new ArrayList<CardView>();
        for (CardView card : board.getCards()) {
            if (!card.getId()
                .equals(cardId)) {
                cards.add(card);
                continue;
            }
            List<RequirementView> requirements = new ArrayList<RequirementView>();
            for (RequirementView requirement : card.getRequirements()) {
                if (!requirement.getId()
                    .equals(requirementId)) requirements.add(requirement);
            }
            cards.add(
                new CardView(
                    card.getId(),
                    card.getTitle(),
                    card.getDescription(),
                    card.getStatus(),
                    requirements,
                    card.getAssigneeIds()));
        }
        setBoard(new BoardSnapshot(board.getProject(), board.getMembers(), cards));
    }

    public static CardView findCard(UUID cardId) {
        if (board == null || cardId == null) return null;
        for (CardView card : board.getCards()) if (card.getId()
            .equals(cardId)) return card;
        return null;
    }

    public static RequirementView findRequirement(UUID cardId, UUID entryId) {
        CardView card = findCard(cardId);
        if (card == null) return null;
        for (RequirementView root : card.getRequirements()) {
            RequirementView found = root.find(entryId);
            if (found != null) return found;
        }
        return null;
    }

    public static String assigneeNames(CardView card) {
        if (card.getAssigneeIds()
            .isEmpty()) return "Unassigned";
        List<String> names = new ArrayList<String>();
        for (UUID id : card.getAssigneeIds()) {
            String name = id.toString();
            if (board != null) for (MemberSummary member : board.getMembers()) if (member.getPlayerId()
                .equals(id)) name = member.getDisplayName();
            names.add(name);
        }
        return String.join(", ", names);
    }

    public static void assignLocally(UUID projectId, UUID cardId, UUID memberId, boolean assigned) {
        if (board == null || !board.getProject()
            .getId()
            .equals(projectId)) return;
        List<CardView> cards = new ArrayList<CardView>();
        for (CardView card : board.getCards()) {
            if (!card.getId()
                .equals(cardId)) {
                cards.add(card);
                continue;
            }
            Set<UUID> assignees = new LinkedHashSet<UUID>(card.getAssigneeIds());
            if (assigned) assignees.add(memberId);
            else assignees.remove(memberId);
            cards.add(
                new CardView(
                    card.getId(),
                    card.getTitle(),
                    card.getDescription(),
                    card.getStatus(),
                    card.getRequirements(),
                    assignees));
        }
        setBoard(new BoardSnapshot(board.getProject(), board.getMembers(), cards));
    }

    public static void setResult(boolean success, String code, String message) {
        resultSuccess = success;
        resultMessage = message == null ? "" : message;
    }

    public static String getResultMessage() {
        return resultMessage;
    }

    public static boolean isResultSuccess() {
        return resultSuccess;
    }

    public static void pinCard(UUID projectId, CardView card) {
        pinnedProjectId = projectId;
        pinnedCard = card;
    }

    public static void clearPinnedCard() {
        pinnedProjectId = null;
        pinnedCard = null;
    }

    public static boolean isCardPinned(UUID projectId, UUID cardId) {
        return pinnedCard != null && pinnedProjectId.equals(projectId)
            && pinnedCard.getId()
                .equals(cardId);
    }

    public static CardView getPinnedCard() {
        return pinnedCard;
    }
}
