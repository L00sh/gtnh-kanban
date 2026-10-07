package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.CardView;
import com.gtnhkanban.api.MemberSummary;
import com.gtnhkanban.api.ProjectSummary;
import com.gtnhkanban.api.RequirementView;
import com.gtnhkanban.api.TaskView;
import com.gtnhkanban.model.BoardSettings;
import com.gtnhkanban.model.CardOrder;

/**
 * Client-thread cache of server snapshots used only for rendering GUI screens.
 *
 * <p>
 * Boards are kept per project. The server sends a project's board to every member whenever it changes, so a member
 * of several projects receives boards for projects they are not looking at; those must not replace the board on
 * screen. Each project screen marks its project as active, and {@link #getBoard()} returns that project's board.
 */
public final class KanbanClientState {

    private static final List<ProjectSummary> PROJECTS = new ArrayList<ProjectSummary>();
    private static final Map<UUID, BoardSnapshot> BOARDS = new HashMap<UUID, BoardSnapshot>();
    private static UUID activeProjectId;
    /** The newest board received, for screens not tied to one project (server-wide settings are on every board). */
    private static BoardSnapshot latest;
    private static UUID pinnedProjectId;
    private static CardView pinnedCard;
    private static String resultMessage = "";
    private static boolean resultSuccess = true;

    private KanbanClientState() {}

    public static void setProjects(List<ProjectSummary> projects) {
        PROJECTS.clear();
        PROJECTS.addAll(projects);
        Set<UUID> accessible = new HashSet<UUID>();
        for (ProjectSummary project : projects) accessible.add(project.getId());
        for (UUID projectId : new ArrayList<UUID>(BOARDS.keySet())) {
            if (accessible.contains(projectId)) continue;
            BOARDS.remove(projectId);
            if (projectId.equals(pinnedProjectId)) clearPinnedCard();
        }
        if (latest != null && !accessible.contains(
            latest.getProject()
                .getId()))
            latest = null;
    }

    /** Called by the screen showing {@code projectId}, so {@link #getBoard()} returns that project's board. */
    public static void setActiveProject(UUID projectId) {
        if (projectId != null) activeProjectId = projectId;
    }

    /** Forgets every board, e.g. when leaving a world or server. */
    public static void clearBoards() {
        BOARDS.clear();
        activeProjectId = null;
        latest = null;
    }

    public static List<ProjectSummary> getProjects() {
        return Collections.unmodifiableList(PROJECTS);
    }

    public static void setBoard(BoardSnapshot snapshot) {
        if (snapshot == null) return;
        BOARDS.put(
            snapshot.getProject()
                .getId(),
            snapshot);
        latest = snapshot;
        if (pinnedCard != null && snapshot.getProject()
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

    /**
     * The board of the project on screen, or null until it has arrived. With no project screen open yet, the newest
     * board received.
     */
    public static BoardSnapshot getBoard() {
        return activeProjectId == null ? latest : BOARDS.get(activeProjectId);
    }

    public static BoardSnapshot getBoard(UUID projectId) {
        return projectId == null ? null : BOARDS.get(projectId);
    }

    /** Server-wide columns and card types from any board received, or null before the first board arrives. */
    public static BoardSettings getSettings() {
        return latest == null ? null : latest.getSettings();
    }

    /** Shows a move immediately, placed exactly where the server will put it. */
    public static void moveCardLocally(UUID projectId, UUID cardId, UUID columnId, UUID beforeCardId) {
        BoardSnapshot board = getBoard(projectId);
        if (board == null) return;
        CardView card = findCard(cardId);
        if (card == null) return;
        List<CardView> cards = new ArrayList<CardView>(board.getCards());
        cards.remove(card);
        CardView before = beforeCardId == null ? null : findCard(beforeCardId);
        cards.add(CardOrder.insertionIndex(cards, new CardOrder.Columns<CardView>() {

            @Override
            public UUID columnOf(CardView value) {
                return value.getColumnId();
            }
        }, columnId, before), card.withColumn(columnId));
        setBoard(board.withCards(cards));
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
        BoardSnapshot board = getBoard(projectId);
        if (board == null) return;
        List<CardView> cards = new ArrayList<CardView>();
        for (CardView card : board.getCards()) cards.add(
            card.getId()
                .equals(replacement.getId()) ? replacement : card);
        setBoard(board.withCards(cards));
    }

    /** Card ids are unique across projects, so this finds a card whichever project's board it is on. */
    public static CardView findCard(UUID cardId) {
        if (cardId == null) return null;
        BoardSnapshot active = getBoard();
        if (active != null) for (CardView card : active.getCards()) if (card.getId()
            .equals(cardId)) return card;
        for (BoardSnapshot board : BOARDS.values()) for (CardView card : board.getCards()) if (card.getId()
            .equals(cardId)) return card;
        return null;
    }

    private static BoardSnapshot boardOf(UUID cardId) {
        for (BoardSnapshot board : BOARDS.values()) for (CardView card : board.getCards()) if (card.getId()
            .equals(cardId)) return board;
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
            BoardSnapshot board = boardOf(card.getId());
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

    private static UUID playerNamesProject;
    private static List<String> playerNames = Collections.emptyList();

    /** Usernames the owner could add to {@code projectId}, from the server. */
    public static void setPlayerNames(UUID projectId, List<String> names) {
        playerNamesProject = projectId;
        playerNames = new ArrayList<String>(names);
    }

    public static List<String> getPlayerNames(UUID projectId) {
        return projectId != null && projectId.equals(playerNamesProject) ? Collections.unmodifiableList(playerNames)
            : Collections.<String>emptyList();
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
