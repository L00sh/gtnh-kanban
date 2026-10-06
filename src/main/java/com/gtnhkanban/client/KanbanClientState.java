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
import com.gtnhkanban.api.TaskView;

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
            if (updated == null) clearPinnedCard();
            else pinnedCard = updated;
        }
    }

    public static BoardSnapshot getBoard() {
        return board;
    }

    public static void moveCardLocally(UUID projectId, UUID cardId, UUID columnId) {
        CardView card = findCard(cardId);
        if (card != null) replaceLocally(projectId, card.withColumn(columnId));
    }

    public static void removeRequirementLocally(UUID projectId, UUID cardId, UUID requirementId) {
        CardView card = findCard(cardId);
        if (card == null) return;
        List<RequirementView> requirements = new ArrayList<RequirementView>();
        for (RequirementView requirement : card.getRequirements()) {
            if (!requirement.getId()
                .equals(requirementId)) requirements.add(requirement);
        }
        replaceLocally(projectId, card.withRequirements(requirements));
    }

    public static void setTaskDoneLocally(UUID projectId, UUID cardId, UUID taskId, boolean done) {
        CardView card = findCard(cardId);
        if (card == null) return;
        List<TaskView> tasks = new ArrayList<TaskView>();
        for (TaskView task : card.getTasks()) tasks.add(
            task.getId()
                .equals(taskId) ? task.withDone(done) : task);
        replaceLocally(projectId, card.withTasks(tasks));
    }

    public static void removeTaskLocally(UUID projectId, UUID cardId, UUID taskId) {
        CardView card = findCard(cardId);
        if (card == null) return;
        List<TaskView> tasks = new ArrayList<TaskView>();
        for (TaskView task : card.getTasks()) if (!task.getId()
            .equals(taskId)) tasks.add(task);
        replaceLocally(projectId, card.withTasks(tasks));
    }

    /** Shows an edit immediately; the next server snapshot replaces it with the confirmed state. */
    private static void replaceLocally(UUID projectId, CardView replacement) {
        if (board == null || !board.getProject()
            .getId()
            .equals(projectId)) return;
        List<CardView> cards = new ArrayList<CardView>();
        for (CardView card : board.getCards()) cards.add(
            card.getId()
                .equals(replacement.getId()) ? replacement : card);
        setBoard(board.withCards(cards));
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
        CardView card = findCard(cardId);
        if (card == null) return;
        Set<UUID> assignees = new LinkedHashSet<UUID>(card.getAssigneeIds());
        if (assigned) assignees.add(memberId);
        else assignees.remove(memberId);
        replaceLocally(projectId, card.withAssignees(assignees));
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
