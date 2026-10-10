package com.gtnhkanban.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

import com.gtnhkanban.api.ActivityLog;
import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.CardView;
import com.gtnhkanban.api.CommentView;
import com.gtnhkanban.api.MemberSummary;
import com.gtnhkanban.api.ProjectSummary;
import com.gtnhkanban.api.RequirementView;
import com.gtnhkanban.api.TaskView;
import com.gtnhkanban.model.ActivityEntry;
import com.gtnhkanban.model.BoardColumn;
import com.gtnhkanban.model.BoardSettings;
import com.gtnhkanban.model.CardComment;
import com.gtnhkanban.model.CardLink;
import com.gtnhkanban.model.CardTask;
import com.gtnhkanban.model.CardType;
import com.gtnhkanban.model.DeletedCard;
import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.ItemRequirement;
import com.gtnhkanban.model.KanbanCard;
import com.gtnhkanban.model.KanbanProject;
import com.gtnhkanban.model.RecipeIngredient;
import com.gtnhkanban.model.RecipePlan;
import com.gtnhkanban.model.RecipeTree;
import com.gtnhkanban.storage.ProjectRepository;

/** Validated project operations shared by the Forge adapter and future external adapters. */
public final class KanbanService {

    private static final int MAX_USERNAME_LENGTH = 16;
    /** Deepest checklist level; root rows are level 1. Matches the save and wire decoders. */
    public static final int MAX_TREE_LEVEL = 16;
    public static final int MAX_CARD_ROWS = 4096;
    /** Most cards one card may depend on, and most it may list as blockers. */
    public static final int MAX_CARD_LINKS = 64;

    private final ProjectRepository projects;
    private final ProfileResolver profiles;
    private final ItemResolver items;
    private final LongSupplier clock;

    public KanbanService(ProjectRepository projects, ProfileResolver profiles, ItemResolver items) {
        this(projects, profiles, items, new LongSupplier() {

            @Override
            public long getAsLong() {
                return System.currentTimeMillis();
            }
        });
    }

    /** @param clock epoch milliseconds, for card and comment timestamps */
    public KanbanService(ProjectRepository projects, ProfileResolver profiles, ItemResolver items, LongSupplier clock) {
        this.clock = clock;
        this.projects = projects;
        this.profiles = profiles;
        this.items = items;
    }

    public List<ProjectSummary> listAccessibleProjects(UUID actorId) {
        List<ProjectSummary> accessibleProjects = new ArrayList<ProjectSummary>();
        for (KanbanProject project : projects.allProjects()) {
            if (project.isOwnerOrMember(actorId)) {
                accessibleProjects.add(projectSummary(project, actorId));
            }
        }
        return accessibleProjects;
    }

    public OperationResult<BoardSnapshot> getBoard(UUID actorId, UUID projectId) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) {
            return failure(authorized);
        }
        KanbanProject project = authorized.getValue();
        if (!BoardSizeBudget.fits(project, 0) || project.getCards()
            .size() > 4096
            || project.getMemberIds()
                .size() >= 1024)
            return boardTooLarge();
        for (KanbanCard card : project.getCards()) if (card.requirementCount() > 4096) return boardTooLarge();
        return OperationResult.success(boardSnapshot(project, actorId));
    }

    public OperationResult<ProjectSummary> createProject(UUID actorId, String name) {
        if (actorId == null) {
            return OperationResult.failure("INVALID_ACTOR", "A player is required to create a project.");
        }
        ValidationResult<String> validName = BoardValidator.validateProjectName(name);
        if (!validName.isValid()) {
            return invalid(validName);
        }
        KanbanProject project = new KanbanProject(UUID.randomUUID(), validName.getValue(), actorId);
        log(project, actorId, ActivityEntry.Kind.PROJECT, null, "Created the project");
        projects.saveProject(project);
        return OperationResult.success(projectSummary(project, actorId));
    }

    public OperationResult<Void> deleteProject(UUID actorId, UUID projectId) {
        OperationResult<KanbanProject> authorized = requireOwner(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        projects.deleteProject(projectId);
        return OperationResult.success(null);
    }

    public OperationResult<Void> addMember(UUID actorId, UUID projectId, String username) {
        OperationResult<KanbanProject> authorized = requireOwner(actorId, projectId);
        if (!authorized.isSuccess()) {
            return failure(authorized);
        }
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null) {
            return OperationResult.failure("INVALID_USERNAME", "Username must be between 1 and 16 characters.");
        }
        UUID memberId = profiles.resolveUsername(normalizedUsername);
        if (memberId == null) {
            return OperationResult.failure("UNKNOWN_USER", "That player is not known to this server.");
        }
        if (!authorized.getValue()
            .isOwnerOrMember(memberId)
            && (authorized.getValue()
                .getMemberIds()
                .size() >= 1023 || !BoardSizeBudget.fits(authorized.getValue(), 150)))
            return boardTooLarge();
        if (authorized.getValue()
            .addMember(memberId))
            log(authorized.getValue(), actorId, ActivityEntry.Kind.MEMBERS, null, "Added " + nameOf(memberId));
        projects.saveProject(authorized.getValue());
        return OperationResult.success(null);
    }

    /** Most names offered when adding members; plenty for a server's whitelist. */
    static final int MAX_SUGGESTIONS = 2000;

    /**
     * Usernames the project owner can add: whitelisted (or, without a whitelist, known) players who are not already
     * the owner or a member, sorted. Only the owner manages membership, so only the owner gets the list.
     */
    public OperationResult<List<String>> memberSuggestions(UUID actorId, UUID projectId) {
        OperationResult<KanbanProject> authorized = requireOwner(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        Set<String> taken = new HashSet<String>();
        KanbanProject project = authorized.getValue();
        taken.add(
            profiles.usernameFor(project.getOwnerId())
                .toLowerCase(Locale.ROOT));
        for (UUID memberId : project.getMemberIds()) taken.add(
            profiles.usernameFor(memberId)
                .toLowerCase(Locale.ROOT));
        java.util.TreeMap<String, String> names = new java.util.TreeMap<String, String>();
        for (String name : profiles.suggestedUsernames()) {
            if (name == null || name.isEmpty() || name.length() > MAX_USERNAME_LENGTH) continue;
            String key = name.toLowerCase(Locale.ROOT);
            // The same player listed twice in different case: keep the first spelling.
            if (!taken.contains(key) && !names.containsKey(key)) names.put(key, name);
            if (names.size() >= MAX_SUGGESTIONS) break;
        }
        return OperationResult.success((List<String>) new ArrayList<String>(names.values()));
    }

    public OperationResult<Void> removeMember(UUID actorId, UUID projectId, UUID memberId) {
        OperationResult<KanbanProject> authorized = requireOwner(actorId, projectId);
        if (!authorized.isSuccess()) {
            return failure(authorized);
        }
        if (memberId == null) {
            return OperationResult.failure("INVALID_MEMBER", "A member is required.");
        }
        if (authorized.getValue()
            .getOwnerId()
            .equals(memberId)) {
            return OperationResult.failure("OWNER_REQUIRED", "The project owner cannot be removed.");
        }
        if (!authorized.getValue()
            .removeMember(memberId)) {
            return OperationResult.failure("MEMBER_NOT_FOUND", "That player is not a project member.");
        }
        log(authorized.getValue(), actorId, ActivityEntry.Kind.MEMBERS, null, "Removed " + nameOf(memberId));
        projects.saveProject(authorized.getValue());
        return OperationResult.success(null);
    }

    public OperationResult<CardView> createCard(UUID actorId, UUID projectId, String title, String description) {
        return createCard(actorId, projectId, CardFields.of(title, description));
    }

    public OperationResult<CardView> createCard(UUID actorId, UUID projectId, CardFields fields) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        KanbanProject project = authorized.getValue();
        OperationResult<CardFields> valid = validateFields(project, null, fields);
        if (!valid.isSuccess()) return failure(valid);
        CardFields checked = valid.getValue();
        KanbanCard card = new KanbanCard(
            UUID.randomUUID(),
            checked.title,
            checked.description,
            checked.columnId == null ? projects.getSettings()
                .firstColumn() : checked.columnId);
        card.setTypeId(checked.typeId);
        card.setPriority(checked.priority);
        card.setIcon(checked.icon);
        card.setCreatorId(actorId);
        card.setCreatedAt(clock.getAsLong());
        if (project.getCards()
            .size() >= 4096 || !BoardSizeBudget.fits(project, BoardSizeBudget.card(card))) return boardTooLarge();
        card.setNumber(project.takeCardNumber());
        project.addCard(card);
        log(project, actorId, ActivityEntry.Kind.CARD_CREATED, card, "In " + columnName(card.getColumnId()));
        projects.saveProject(project);
        return OperationResult.success(cardView(card));
    }

    public OperationResult<CardView> updateCard(UUID actorId, UUID projectId, UUID cardId, CardFields fields) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        KanbanProject project = authorized.getValue();
        KanbanCard card = findCard(project, cardId);
        if (card == null) return OperationResult.failure("CARD_NOT_FOUND", "That card does not exist.");
        OperationResult<CardFields> valid = validateFields(project, cardId, fields);
        if (!valid.isSuccess()) return failure(valid);
        CardFields checked = valid.getValue();
        if (!checked.description.equals(card.getDescription()) && !canEditDescription(project, card, actorId))
            return OperationResult.failure(
                "DESCRIPTION_LOCKED",
                "Only the card's creator or the board owner can change its description.");
        if (checked.columnId != null) {
            OperationResult<Void> movable = checkMovable(project, card, checked.columnId);
            if (!movable.isSuccess()) return failure(movable);
        }
        long textGrowth = BoardSizeBudget.text(checked.title) + BoardSizeBudget.text(checked.description)
            - BoardSizeBudget.text(card.getTitle())
            - BoardSizeBudget.text(card.getDescription());
        if (textGrowth > 0 && !BoardSizeBudget.fits(project, textGrowth)) return boardTooLarge();
        List<String> changes = new ArrayList<String>();
        if (!checked.title.equals(card.getTitle()))
            changes.add("Renamed from " + quoted(card.getTitle()) + " to " + quoted(checked.title));
        if (!checked.description.equals(card.getDescription())) changes.add("Edited the description");
        boolean moved = checked.columnId != null && !checked.columnId.equals(card.getColumnId());
        if (!java.util.Objects.equals(checked.typeId, card.getTypeId()))
            changes.add("Type: " + typeName(card.getTypeId()) + " -> " + typeName(checked.typeId));
        if (checked.priority != card.getPriority()) changes.add(
            "Priority: " + card.getPriority()
                .getLabel() + " -> " + checked.priority.getLabel());
        if (!java.util.Objects.equals(checked.icon, card.getIcon()))
            changes.add(checked.icon == null ? "Removed the icon" : "Icon: " + itemName(checked.icon));
        String movedFrom = columnName(card.getColumnId());
        card.setTitle(checked.title);
        card.setDescription(checked.description);
        if (checked.columnId != null) card.setColumnId(checked.columnId);
        card.setTypeId(checked.typeId);
        card.setPriority(checked.priority);
        card.setIcon(checked.icon);
        if (!changes.isEmpty()) log(project, actorId, ActivityEntry.Kind.CARD_EDITED, card, String.join("; ", changes));
        if (moved) log(
            project,
            actorId,
            ActivityEntry.Kind.CARD_MOVED,
            card,
            movedFrom + " -> " + columnName(checked.columnId));
        projects.saveProject(project);
        return OperationResult.success(cardView(card));
    }

    /** Validates and normalizes card fields; {@code cardId} is the card being edited, or null for a new card. */
    private OperationResult<CardFields> validateFields(KanbanProject project, UUID cardId, CardFields fields) {
        ValidationResult<String> validTitle = BoardValidator.validateCardTitle(fields.title);
        if (!validTitle.isValid()) return invalid(validTitle);
        if (hasCardTitle(project, validTitle.getValue(), cardId)) {
            return OperationResult
                .failure("DUPLICATE_CARD_TITLE", "A card with that name already exists in this project.");
        }
        ValidationResult<String> validDescription = BoardValidator.validateDescription(fields.description);
        if (!validDescription.isValid()) return invalid(validDescription);
        BoardSettings settings = projects.getSettings();
        if (fields.columnId != null && !settings.hasColumn(fields.columnId))
            return OperationResult.failure("INVALID_COLUMN", "That column no longer exists.");
        if (fields.typeId != null && !settings.hasType(fields.typeId))
            return OperationResult.failure("INVALID_TYPE", "That card type no longer exists.");
        if (fields.icon != null && !isAcceptableMaterial(fields.icon))
            return OperationResult.failure("UNKNOWN_ITEM", "That icon item is not registered on this server.");
        return OperationResult.success(
            new CardFields(
                validTitle.getValue(),
                validDescription.getValue(),
                fields.columnId,
                fields.typeId,
                fields.priority,
                fields.icon));
    }

    public OperationResult<Void> deleteCard(UUID actorId, UUID projectId, UUID cardId) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        KanbanProject project = authorized.getValue();
        KanbanCard card = findCard(project, cardId);
        if (card == null || !project.removeCard(cardId)) {
            return OperationResult.failure("CARD_NOT_FOUND", "That card does not exist.");
        }
        // Kept whole so its creator or the owner can bring it back from the activity log.
        project.keepDeleted(new DeletedCard(card, clock.getAsLong(), actorId));
        log(project, actorId, ActivityEntry.Kind.CARD_DELETED, card, "From " + columnName(card.getColumnId()));
        projects.saveProject(project);
        return OperationResult.success(null);
    }

    /** The board's activity, newest first, and its kept deleted cards, for any member. */
    public OperationResult<ActivityLog> getActivity(UUID actorId, UUID projectId) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        KanbanProject project = authorized.getValue();
        List<ActivityLog.Entry> entries = new ArrayList<ActivityLog.Entry>();
        List<ActivityEntry> activity = project.getActivity();
        for (int i = activity.size() - 1; i >= 0; i--) {
            ActivityEntry entry = activity.get(i);
            entries.add(
                new ActivityLog.Entry(
                    entry.getTime(),
                    entry.getActorId(),
                    nameOf(entry.getActorId()),
                    entry.getKind(),
                    entry.getCardId(),
                    entry.getCardNumber(),
                    entry.getCardTitle(),
                    entry.getDetail()));
        }
        List<ActivityLog.Deleted> deleted = new ArrayList<ActivityLog.Deleted>();
        List<DeletedCard> kept = project.getDeleted();
        for (int i = kept.size() - 1; i >= 0; i--) {
            DeletedCard gone = kept.get(i);
            KanbanCard card = gone.getCard();
            deleted.add(
                new ActivityLog.Deleted(
                    card.getId(),
                    card.getNumber(),
                    card.getTitle(),
                    gone.getDeletedAt(),
                    nameOf(gone.getDeletedBy()),
                    canRestore(project, card, actorId)));
        }
        return OperationResult.success(new ActivityLog(projectId, entries, deleted));
    }

    /** Only the board owner and the card's creator may bring a deleted card back. */
    public static boolean canRestore(KanbanProject project, KanbanCard card, UUID actorId) {
        return canEditDescription(project, card, actorId);
    }

    /**
     * Puts a deleted card back on the board, in its old column (or the first, if that column is gone). Links other
     * cards had to it were removed when it was deleted and stay removed; its own links to cards still on the board come
     * back.
     */
    public OperationResult<CardView> restoreCard(UUID actorId, UUID projectId, UUID cardId) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        KanbanProject project = authorized.getValue();
        DeletedCard kept = null;
        for (DeletedCard candidate : project.getDeleted()) if (candidate.getCard()
            .getId()
            .equals(cardId)) kept = candidate;
        if (kept == null) return OperationResult.failure("CARD_NOT_FOUND", "That deleted card is no longer kept.");
        KanbanCard card = kept.getCard();
        if (!canRestore(project, card, actorId)) return OperationResult
            .failure("RESTORE_NOT_ALLOWED", "Only the card's creator or the board owner can restore it.");
        if (project.getCards()
            .size() >= 4096 || !BoardSizeBudget.fits(project, BoardSizeBudget.card(card))) return boardTooLarge();
        if (hasCardTitle(project, card.getTitle(), null)) {
            String suffix = " (restored)";
            String title = card.getTitle();
            if (title.length() + suffix.length() > BoardValidator.MAX_CARD_TITLE_LENGTH)
                title = title.substring(0, BoardValidator.MAX_CARD_TITLE_LENGTH - suffix.length());
            card.setTitle(title + suffix);
        }
        if (!projects.getSettings()
            .hasColumn(card.getColumnId()))
            card.setColumnId(
                projects.getSettings()
                    .firstColumn());
        for (KanbanCard other : project.getCards()) if (other.getNumber() == card.getNumber()) {
            card.setNumber(project.takeCardNumber());
            break;
        }
        project.takeDeleted(cardId);
        project.addCard(card);
        log(project, actorId, ActivityEntry.Kind.CARD_RESTORED, card, "Back in " + columnName(card.getColumnId()));
        projects.saveProject(project);
        return OperationResult.success(cardView(project, card));
    }

    public OperationResult<CardView> moveCard(UUID actorId, UUID projectId, UUID cardId, UUID columnId) {
        return moveCard(actorId, projectId, cardId, columnId, null);
    }

    /**
     * Moves a card to another column or position. With {@code beforeCardId} it lands just above that card (which must
     * be in the target column); without, at the bottom of the column.
     */
    public OperationResult<CardView> moveCard(UUID actorId, UUID projectId, UUID cardId, UUID columnId,
        UUID beforeCardId) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        KanbanCard card = findCard(authorized.getValue(), cardId);
        UUID movedFrom = card == null ? null : card.getColumnId();
        if (card == null) return OperationResult.failure("CARD_NOT_FOUND", "That card does not exist.");
        if (columnId == null || !projects.getSettings()
            .hasColumn(columnId)) return OperationResult.failure("INVALID_COLUMN", "That column no longer exists.");
        OperationResult<Void> movable = checkMovable(authorized.getValue(), card, columnId);
        if (!movable.isSuccess()) return failure(movable);
        if (!authorized.getValue()
            .moveCard(cardId, columnId, beforeCardId))
            return OperationResult.failure("INVALID_POSITION", "That card moved meanwhile. Try again.");
        // Reordering within a column is not worth a log entry.
        if (!columnId.equals(movedFrom)) log(
            authorized.getValue(),
            actorId,
            ActivityEntry.Kind.CARD_MOVED,
            card,
            columnName(movedFrom) + " -> " + columnName(columnId));
        projects.saveProject(authorized.getValue());
        return OperationResult.success(cardView(card));
    }

    /** True when the actor created the card or owns the board; cards without a known creator are the owner's. */
    public static boolean canEditDescription(KanbanProject project, KanbanCard card, UUID actorId) {
        return actorId != null && (project.getOwnerId()
            .equals(actorId) || actorId.equals(card.getCreatorId()));
    }

    /** A card may enter the done column only once every card it depends on is done. */
    private OperationResult<Void> checkMovable(KanbanProject project, KanbanCard card, UUID columnId) {
        UUID done = projects.getSettings()
            .doneColumn();
        if (!done.equals(columnId) || done.equals(card.getColumnId())) return OperationResult.success(null);
        List<String> waiting = new ArrayList<String>();
        for (UUID dependencyId : card.getLinks(CardLink.DEPENDS_ON)) {
            KanbanCard dependency = project.findCard(dependencyId);
            if (dependency != null && !done.equals(dependency.getColumnId()))
                waiting.add("#" + dependency.getNumber() + " " + dependency.getTitle());
        }
        if (waiting.isEmpty()) return OperationResult.success(null);
        String list = waiting.size() <= 3 ? String.join(", ", waiting)
            : String.join(", ", waiting.subList(0, 3)) + " and " + (waiting.size() - 3) + " more";
        return OperationResult.failure("DEPENDENCIES_NOT_DONE", "Finish " + list + " first.");
    }

    /**
     * Links or unlinks {@code otherCardId} from a card. Depends-on links may not form a loop, since no card in it could
     * ever be finished.
     */
    public OperationResult<CardView> setCardLink(UUID actorId, UUID projectId, UUID cardId, UUID otherCardId,
        CardLink link, boolean linked) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        KanbanProject project = authorized.getValue();
        KanbanCard card = findCard(project, cardId);
        KanbanCard other = findCard(project, otherCardId);
        if (card == null || other == null || link == null)
            return OperationResult.failure("CARD_NOT_FOUND", "That card does not exist.");
        if (linked) {
            if (card == other) return OperationResult.failure("INVALID_LINK", "A card cannot be linked to itself.");
            if (card.getLinks(link)
                .contains(otherCardId)) return OperationResult.success(cardView(project, card));
            if (card.getLinks(link)
                .size() >= MAX_CARD_LINKS)
                return OperationResult.failure("TOO_MANY_LINKS", "A card can list at most " + MAX_CARD_LINKS + ".");
            if (link == CardLink.DEPENDS_ON && dependsOn(project, other, cardId, new java.util.HashSet<UUID>()))
                return OperationResult.failure(
                    "DEPENDENCY_LOOP",
                    "#" + other.getNumber() + " already depends on this card, so neither could be finished.");
            if (!BoardSizeBudget.fits(project, 17)) return boardTooLarge();
        }
        if (card.getLinks(link)
            .contains(otherCardId) != linked) {
            String target = "#" + other.getNumber() + " " + quoted(other.getTitle());
            String what = link == CardLink.DEPENDS_ON ? (linked ? "Now depends on " : "No longer depends on ")
                : (linked ? "Blocked by " : "No longer blocked by ");
            log(project, actorId, ActivityEntry.Kind.LINKS, card, what + target);
        }
        card.setLinked(link, otherCardId, linked);
        projects.saveProject(project);
        return OperationResult.success(cardView(project, card));
    }

    /** True when {@code card} depends on {@code target}, directly or through other cards. */
    private boolean dependsOn(KanbanProject project, KanbanCard card, UUID target, java.util.Set<UUID> seen) {
        if (!seen.add(card.getId())) return false;
        for (UUID next : card.getLinks(CardLink.DEPENDS_ON)) {
            if (next.equals(target)) return true;
            KanbanCard nextCard = project.findCard(next);
            if (nextCard != null && dependsOn(project, nextCard, target, seen)) return true;
        }
        return false;
    }

    public OperationResult<Void> setProjectIcon(UUID actorId, UUID projectId, ItemKey icon) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        if (icon != null && !isAcceptableMaterial(icon))
            return OperationResult.failure("UNKNOWN_ITEM", "That icon item is not registered on this server.");
        authorized.getValue()
            .setIcon(icon);
        log(
            authorized.getValue(),
            actorId,
            ActivityEntry.Kind.PROJECT,
            null,
            icon == null ? "Removed the project icon" : "Set the project icon to " + itemName(icon));
        projects.saveProject(authorized.getValue());
        return OperationResult.success(null);
    }

    public OperationResult<CardView> addTask(UUID actorId, UUID projectId, UUID cardId, String text) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        KanbanCard card = findCard(authorized.getValue(), cardId);
        if (card == null) return OperationResult.failure("CARD_NOT_FOUND", "That card does not exist.");
        ValidationResult<String> validText = BoardValidator.validateTask(text);
        if (!validText.isValid()) return invalid(validText);
        if (card.getTasks()
            .size() >= BoardValidator.MAX_TASKS)
            return OperationResult.failure("TOO_MANY_TASKS", "This card has too many tasks.");
        if (!BoardSizeBudget.fits(authorized.getValue(), 40 + BoardSizeBudget.text(validText.getValue())))
            return boardTooLarge();
        card.addTask(new CardTask(UUID.randomUUID(), validText.getValue(), false));
        log(
            authorized.getValue(),
            actorId,
            ActivityEntry.Kind.TASKS,
            card,
            "Added task " + quoted(validText.getValue()));
        projects.saveProject(authorized.getValue());
        return OperationResult.success(cardView(card));
    }

    public OperationResult<CardView> setTaskDone(UUID actorId, UUID projectId, UUID cardId, UUID taskId, boolean done) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        KanbanCard card = findCard(authorized.getValue(), cardId);
        if (card == null) return OperationResult.failure("CARD_NOT_FOUND", "That card does not exist.");
        CardTask task = taskId == null ? null : card.findTask(taskId);
        if (task == null) return OperationResult.failure("TASK_NOT_FOUND", "That task does not exist.");
        if (task.isDone() != done) log(
            authorized.getValue(),
            actorId,
            ActivityEntry.Kind.TASKS,
            card,
            (done ? "Finished task " : "Reopened task ") + quoted(task.getText()));
        task.setDone(done);
        projects.saveProject(authorized.getValue());
        return OperationResult.success(cardView(card));
    }

    public OperationResult<CardView> deleteTask(UUID actorId, UUID projectId, UUID cardId, UUID taskId) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        KanbanCard card = findCard(authorized.getValue(), cardId);
        if (card == null) return OperationResult.failure("CARD_NOT_FOUND", "That card does not exist.");
        CardTask removed = taskId == null ? null : card.findTask(taskId);
        if (taskId == null || !card.removeTask(taskId))
            return OperationResult.failure("TASK_NOT_FOUND", "That task does not exist.");
        log(
            authorized.getValue(),
            actorId,
            ActivityEntry.Kind.TASKS,
            card,
            "Removed task " + quoted(removed.getText()));
        projects.saveProject(authorized.getValue());
        return OperationResult.success(cardView(card));
    }

    public OperationResult<CardView> addComment(UUID actorId, UUID projectId, UUID cardId, String text) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        KanbanCard card = findCard(authorized.getValue(), cardId);
        if (card == null) return OperationResult.failure("CARD_NOT_FOUND", "That card does not exist.");
        ValidationResult<String> validText = BoardValidator.validateComment(text);
        if (!validText.isValid()) return invalid(validText);
        if (card.getComments()
            .size() >= BoardValidator.MAX_COMMENTS)
            return OperationResult.failure("TOO_MANY_COMMENTS", "This card has too many comments.");
        if (!BoardSizeBudget.fits(authorized.getValue(), 60 + BoardSizeBudget.text(validText.getValue())))
            return boardTooLarge();
        card.addComment(new CardComment(UUID.randomUUID(), actorId, clock.getAsLong(), validText.getValue()));
        log(
            authorized.getValue(),
            actorId,
            ActivityEntry.Kind.COMMENTS,
            card,
            "Commented " + quoted(validText.getValue()));
        projects.saveProject(authorized.getValue());
        return OperationResult.success(cardView(card));
    }

    /** Comment authors can delete their own comments; the project owner can delete any. */
    public OperationResult<CardView> deleteComment(UUID actorId, UUID projectId, UUID cardId, UUID commentId) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        KanbanCard card = findCard(authorized.getValue(), cardId);
        if (card == null) return OperationResult.failure("CARD_NOT_FOUND", "That card does not exist.");
        CardComment comment = commentId == null ? null : card.findComment(commentId);
        if (comment == null) return OperationResult.failure("COMMENT_NOT_FOUND", "That comment does not exist.");
        if (!actorId.equals(comment.getAuthorId()) && !authorized.getValue()
            .getOwnerId()
            .equals(actorId))
            return OperationResult.failure("FORBIDDEN", "Only the author or the project owner can delete that.");
        card.removeComment(commentId);
        projects.saveProject(authorized.getValue());
        return OperationResult.success(cardView(card));
    }

    public BoardSettings getSettings() {
        return projects.getSettings();
    }

    /**
     * Replaces the server-wide columns (in display order) and card types. Any project member may do this. Cards in a
     * removed column move to the first column; cards of a removed type lose their type. No card is ever removed.
     */
    public OperationResult<BoardSettings> saveSettings(UUID actorId, List<BoardColumn> columns, List<CardType> types) {
        boolean member = false;
        for (KanbanProject project : projects.allProjects()) member |= project.isOwnerOrMember(actorId);
        if (!member) return OperationResult.failure("FORBIDDEN", "Join or create a project to change board settings.");
        if (columns == null || columns.isEmpty() || columns.size() > BoardValidator.MAX_COLUMNS) {
            return OperationResult
                .failure("INVALID_INPUT", "A board needs 1 to " + BoardValidator.MAX_COLUMNS + " columns.");
        }
        if (types == null || types.size() > BoardValidator.MAX_TYPES)
            return OperationResult.failure("INVALID_INPUT", "At most " + BoardValidator.MAX_TYPES + " card types.");
        Set<UUID> ids = new HashSet<UUID>();
        Set<String> names = new HashSet<String>();
        List<BoardColumn> cleanColumns = new ArrayList<BoardColumn>();
        for (BoardColumn column : columns) {
            ValidationResult<String> name = BoardValidator.validateSettingName(column.getName(), "Column name");
            if (!name.isValid()) return invalid(name);
            if (!ids.add(column.getId()) || !names.add(
                name.getValue()
                    .toLowerCase(Locale.ROOT)))
                return OperationResult.failure("INVALID_INPUT", "Column names must be different.");
            cleanColumns.add(new BoardColumn(column.getId(), name.getValue()));
        }
        names.clear();
        List<CardType> cleanTypes = new ArrayList<CardType>();
        for (CardType type : types) {
            ValidationResult<String> name = BoardValidator.validateSettingName(type.getName(), "Type name");
            if (!name.isValid()) return invalid(name);
            if (!ids.add(type.getId()) || !names.add(
                name.getValue()
                    .toLowerCase(Locale.ROOT)))
                return OperationResult.failure("INVALID_INPUT", "Card type names must be different.");
            cleanTypes.add(new CardType(type.getId(), name.getValue(), type.getColor()));
        }
        BoardSettings settings = new BoardSettings(cleanColumns, cleanTypes);
        projects.saveSettings(settings);
        for (KanbanProject project : projects.allProjects()) {
            if (project.fitToSettings(settings)) projects.saveProject(project);
        }
        return OperationResult.success(settings);
    }

    public OperationResult<RequirementView> addRequirement(UUID actorId, UUID projectId, UUID cardId, ItemKey item,
        int quantity) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) {
            return failure(authorized);
        }
        KanbanCard card = findCard(authorized.getValue(), cardId);
        if (card == null) {
            return OperationResult.failure("CARD_NOT_FOUND", "That card does not exist.");
        }
        ValidationResult<Integer> validQuantity = BoardValidator.validateQuantity(quantity);
        if (!validQuantity.isValid()) {
            return invalid(validQuantity);
        }
        if (item == null || item.getRegistryName()
            .length() > 256
            || item.getNbt()
                .length() > 4096
            || !items.isRegistered(item)) {
            return OperationResult.failure("UNKNOWN_ITEM", "That item is not registered on this server.");
        }
        ItemRequirement requirement = new ItemRequirement(
            UUID.randomUUID(),
            item,
            validQuantity.getValue()
                .intValue(),
            false);
        if (card.requirementCount() >= 4096)
            return OperationResult.failure("TOO_MANY_REQUIREMENTS", "This card has too many materials.");
        if (!BoardSizeBudget.fits(authorized.getValue(), BoardSizeBudget.requirement(requirement)))
            return boardTooLarge();
        card.addRequirement(requirement);
        log(
            authorized.getValue(),
            actorId,
            ActivityEntry.Kind.ITEMS,
            card,
            "Added " + requirement.getQuantity() + " x " + itemName(item));
        projects.saveProject(authorized.getValue());
        return OperationResult.success(requirementView(requirement));
    }

    public OperationResult<RequirementView> setRequirementComplete(UUID actorId, UUID projectId, UUID cardId,
        UUID requirementId, boolean complete) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) {
            return failure(authorized);
        }
        KanbanCard card = findCard(authorized.getValue(), cardId);
        if (card == null) {
            return OperationResult.failure("CARD_NOT_FOUND", "That card does not exist.");
        }
        ItemRequirement requirement = requirementId == null ? null : card.findRequirement(requirementId);
        if (requirement == null) {
            return OperationResult.failure("REQUIREMENT_NOT_FOUND", "That checklist entry does not exist.");
        }
        if (requirement.isComplete() != complete) log(
            authorized.getValue(),
            actorId,
            ActivityEntry.Kind.ITEMS,
            card,
            (complete ? "Ticked " : "Unticked ") + itemName(requirement.getItem()));
        requirement.setComplete(complete);
        touchAncestors(card, requirementId);
        projects.saveProject(authorized.getValue());
        return OperationResult.success(requirementView(requirement));
    }

    public OperationResult<RequirementView> setRequirementQuantity(UUID actorId, UUID projectId, UUID cardId,
        UUID requirementId, int quantity) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        KanbanCard card = findCard(authorized.getValue(), cardId);
        if (card == null) return OperationResult.failure("CARD_NOT_FOUND", "That card does not exist.");
        ItemRequirement requirement = requirementId == null ? null : card.findRequirement(requirementId);
        if (requirement == null) {
            return OperationResult.failure("REQUIREMENT_NOT_FOUND", "That checklist entry does not exist.");
        }
        ValidationResult<Integer> validQuantity = BoardValidator.validateQuantity(quantity);
        if (!validQuantity.isValid()) return invalid(validQuantity);
        if (!card.isRootRequirement(requirementId))
            return OperationResult.failure("DERIVED_QUANTITY", "Edit the parent quantity to change recipe materials.");
        try {
            requirement.setQuantity(
                validQuantity.getValue()
                    .intValue());
        } catch (IllegalArgumentException exception) {
            return OperationResult.failure("INVALID_QUANTITY", exception.getMessage());
        }
        log(
            authorized.getValue(),
            actorId,
            ActivityEntry.Kind.ITEMS,
            card,
            "Needs " + requirement.getQuantity() + " x " + itemName(requirement.getItem()));
        projects.saveProject(authorized.getValue());
        return OperationResult.success(requirementView(requirement));
    }

    public OperationResult<Void> deleteRequirement(UUID actorId, UUID projectId, UUID cardId, UUID requirementId) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        KanbanCard card = findCard(authorized.getValue(), cardId);
        if (card == null) return OperationResult.failure("CARD_NOT_FOUND", "That card does not exist.");
        ItemRequirement removed = requirementId == null ? null : card.findRequirement(requirementId);
        if (!card.removeRequirement(requirementId)) {
            return OperationResult.failure("REQUIREMENT_NOT_FOUND", "That checklist entry does not exist.");
        }
        log(authorized.getValue(), actorId, ActivityEntry.Kind.ITEMS, card, "Removed " + itemName(removed.getItem()));
        projects.saveProject(authorized.getValue());
        return OperationResult.success(null);
    }

    public OperationResult<CardView> setCardAssigned(UUID actorId, UUID projectId, UUID cardId, UUID memberId,
        boolean assigned) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        KanbanProject project = authorized.getValue();
        KanbanCard card = findCard(project, cardId);
        if (card == null) return OperationResult.failure("CARD_NOT_FOUND", "That card does not exist.");
        if (memberId == null || !project.isOwnerOrMember(memberId))
            return OperationResult.failure("MEMBER_NOT_FOUND", "Assign only current project members.");
        if (assigned && !card.getAssigneeIds()
            .contains(memberId) && !BoardSizeBudget.fits(project, 17)) return boardTooLarge();
        if (card.getAssigneeIds()
            .contains(memberId) != assigned)
            log(
                project,
                actorId,
                ActivityEntry.Kind.ASSIGNEES,
                card,
                (assigned ? "Assigned " : "Unassigned ") + nameOf(memberId));
        card.setAssigned(memberId, assigned);
        projects.saveProject(project);
        return OperationResult.success(cardView(card));
    }

    public OperationResult<RequirementView> expandRequirement(UUID actorId, UUID projectId, UUID cardId, UUID entryId,
        int expectedQuantity, long expectedRevision, RecipePlan plan) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        KanbanCard card = findCard(authorized.getValue(), cardId);
        if (card == null) return OperationResult.failure("CARD_NOT_FOUND", "That card does not exist.");
        ItemRequirement parent = card.findRequirement(entryId);
        if (parent == null)
            return OperationResult.failure("REQUIREMENT_NOT_FOUND", "That checklist entry does not exist.");
        if (parent.getQuantity() != expectedQuantity || parent.getRevision() != expectedRevision) {
            return OperationResult.failure("STALE_RECIPE", "This material changed. Reopen its recipe preview.");
        }
        if (plan == null) {
            parent.clearExpansion();
        } else {
            List<ItemRequirement> path = card.requirementPath(entryId);
            if (path.size() >= 16 || card.requirementCount() - parent.nodeCount()
                + 1
                + plan.getIngredients()
                    .size()
                > 4096) {
                return OperationResult.failure("TREE_LIMIT", "The material tree is too large or too deep.");
            }
            if (plan.getName()
                .length() > 128) return OperationResult.failure("INVALID_RECIPE", "Recipe label is too long.");
            for (RecipeIngredient ingredient : plan.getIngredients()) {
                ItemKey material = ingredient.getMaterial();
                if (material.getRegistryName()
                    .length() > 256
                    || material.getNbt()
                        .length() > 4096
                    || !items.isRegistered(material)) {
                    return OperationResult.failure("UNKNOWN_ITEM", "A recipe material is unavailable on this server.");
                }
                for (ItemRequirement ancestor : path) {
                    if (ancestor.getItem()
                        .equals(material))
                        return OperationResult.failure(
                            "RECIPE_LOOP",
                            "This recipe leads back to a parent material. Choose another recipe.");
                }
            }
            try {
                ItemRequirement preview = new ItemRequirement(
                    parent.getId(),
                    parent.getItem(),
                    parent.getQuantity(),
                    parent.isComplete());
                preview.expand(plan);
                long growth = BoardSizeBudget.requirement(preview) - BoardSizeBudget.requirement(parent);
                if (growth > 0 && !BoardSizeBudget.fits(authorized.getValue(), growth)) return boardTooLarge();
                parent.expand(plan);
            } catch (IllegalArgumentException exception) {
                return OperationResult.failure("INVALID_RECIPE", exception.getMessage());
            }
        }
        touchAncestors(card, entryId);
        log(
            authorized.getValue(),
            actorId,
            ActivityEntry.Kind.ITEMS,
            card,
            plan == null ? "Cleared the materials of " + itemName(parent.getItem())
                : "Chose " + plan.getName() + " for " + itemName(parent.getItem()));
        projects.saveProject(authorized.getValue());
        return OperationResult.success(requirementView(parent));
    }

    /**
     * Applies a full nested breakdown in one step. With a null {@code entryId} this adds {@code item} as a new
     * checklist row with the breakdown beneath it (a null {@code tree} adds it unexpanded). Otherwise it replaces the
     * material branch of an existing row, which must still have {@code quantity} and {@code expectedRevision}.
     */
    public OperationResult<RequirementView> applyBreakdown(UUID actorId, UUID projectId, UUID cardId, UUID entryId,
        ItemKey item, int quantity, long expectedRevision, RecipeTree tree) {
        if (entryId == null && tree == null) return addRequirement(actorId, projectId, cardId, item, quantity);
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        KanbanCard card = findCard(authorized.getValue(), cardId);
        if (card == null) return OperationResult.failure("CARD_NOT_FOUND", "That card does not exist.");
        if (tree == null) return OperationResult.failure("INVALID_RECIPE", "A breakdown is required.");

        ItemRequirement target;
        List<ItemKey> ancestors = new ArrayList<ItemKey>();
        int existingRows;
        if (entryId == null) {
            ValidationResult<Integer> validQuantity = BoardValidator.validateQuantity(quantity);
            if (!validQuantity.isValid()) return invalid(validQuantity);
            if (!isAcceptableMaterial(item))
                return OperationResult.failure("UNKNOWN_ITEM", "That item is not registered on this server.");
            target = new ItemRequirement(UUID.randomUUID(), item, quantity, false);
            existingRows = 0;
        } else {
            target = card.findRequirement(entryId);
            if (target == null)
                return OperationResult.failure("REQUIREMENT_NOT_FOUND", "That checklist entry does not exist.");
            if (target.getQuantity() != quantity || target.getRevision() != expectedRevision) {
                return OperationResult.failure("STALE_RECIPE", "This material changed. Try the breakdown again.");
            }
            for (ItemRequirement ancestor : card.requirementPath(entryId)) ancestors.add(ancestor.getItem());
            ancestors.remove(ancestors.size() - 1);
            existingRows = target.nodeCount();
        }
        ancestors.add(target.getItem());

        if (ancestors.size() + tree.depth() > MAX_TREE_LEVEL
            || card.requirementCount() - existingRows + 1 + tree.nodeCount() > MAX_CARD_ROWS) {
            return OperationResult.failure("TREE_LIMIT", "The material tree is too large or too deep.");
        }
        OperationResult<Void> valid = validateTree(tree, ancestors);
        if (!valid.isSuccess()) return failure(valid);

        ItemRequirement preview = new ItemRequirement(
            target.getId(),
            target.getItem(),
            target.getQuantity(),
            target.isComplete());
        try {
            preview.expandTree(tree);
        } catch (IllegalArgumentException exception) {
            return OperationResult.failure("INVALID_RECIPE", exception.getMessage());
        }
        long growth = BoardSizeBudget.requirement(preview)
            - (entryId == null ? 0 : BoardSizeBudget.requirement(target));
        if (growth > 0 && !BoardSizeBudget.fits(authorized.getValue(), growth)) return boardTooLarge();

        if (entryId == null) {
            card.addRequirement(preview);
            target = preview;
        } else {
            target.expandTree(tree);
            touchAncestors(card, entryId);
        }
        log(
            authorized.getValue(),
            actorId,
            ActivityEntry.Kind.ITEMS,
            card,
            (entryId == null ? "Added " + target.getQuantity() + " x " : "Broke down ") + itemName(target.getItem()));
        projects.saveProject(authorized.getValue());
        return OperationResult.success(requirementView(target));
    }

    private OperationResult<Void> validateTree(RecipeTree tree, List<ItemKey> ancestors) {
        RecipePlan plan = tree.getPlan();
        if (plan.getName()
            .length() > 128) return OperationResult.failure("INVALID_RECIPE", "Recipe label is too long.");
        for (int i = 0; i < plan.getIngredients()
            .size(); i++) {
            ItemKey material = plan.getIngredients()
                .get(i)
                .getMaterial();
            if (!isAcceptableMaterial(material))
                return OperationResult.failure("UNKNOWN_ITEM", "A recipe material is unavailable on this server.");
            if (ancestors.contains(material)) return OperationResult
                .failure("RECIPE_LOOP", "This recipe leads back to a parent material. Choose another recipe.");
            RecipeTree child = tree.getChildren()
                .get(i);
            if (child == null) continue;
            ancestors.add(material);
            OperationResult<Void> valid = validateTree(child, ancestors);
            ancestors.remove(ancestors.size() - 1);
            if (!valid.isSuccess()) return valid;
        }
        return OperationResult.success(null);
    }

    private boolean isAcceptableMaterial(ItemKey material) {
        return material != null && material.getRegistryName()
            .length() <= 256
            && material.getNbt()
                .length() <= 4096
            && items.isRegistered(material);
    }

    private <T> OperationResult<T> boardTooLarge() {
        return OperationResult.failure(
            "BOARD_TOO_LARGE",
            "This board is too large to synchronize. Remove unused cards or material branches.");
    }

    private void log(KanbanProject project, UUID actorId, ActivityEntry.Kind kind, KanbanCard card, String detail) {
        project.log(
            new ActivityEntry(
                clock.getAsLong(),
                actorId,
                kind,
                card == null ? null : card.getId(),
                card == null ? 0 : card.getNumber(),
                card == null ? "" : card.getTitle(),
                detail));
    }

    private String itemName(ItemKey item) {
        return items.displayName(item);
    }

    private String columnName(UUID columnId) {
        for (BoardColumn column : projects.getSettings()
            .getColumns())
            if (column.getId()
                .equals(columnId)) return column.getName();
        return "a removed column";
    }

    private String typeName(UUID typeId) {
        if (typeId == null) return "None";
        for (CardType type : projects.getSettings()
            .getTypes())
            if (type.getId()
                .equals(typeId)) return type.getName();
        return "a removed type";
    }

    private static String quoted(String text) {
        return "\"" + (text.length() > 60 ? text.substring(0, 57) + "..." : text) + "\"";
    }

    private void touchAncestors(KanbanCard card, UUID entryId) {
        List<ItemRequirement> path = card.requirementPath(entryId);
        for (int i = 0; i + 1 < path.size(); i++) path.get(i)
            .touch();
    }

    private boolean hasCardTitle(KanbanProject project, String title, UUID excludedCardId) {
        for (KanbanCard existing : project.getCards()) {
            if (!existing.getId()
                .equals(excludedCardId) && existing.getTitle()
                    .trim()
                    .equalsIgnoreCase(title)) {
                return true;
            }
        }
        return false;
    }

    private OperationResult<KanbanProject> requireOwner(UUID actorId, UUID projectId) {
        KanbanProject project = projects.findProject(projectId);
        if (project == null) {
            return OperationResult.failure("PROJECT_NOT_FOUND", "That project does not exist.");
        }
        if (!project.getOwnerId()
            .equals(actorId)) {
            return OperationResult.failure("FORBIDDEN", "Only the project owner can do that.");
        }
        return OperationResult.success(project);
    }

    private OperationResult<KanbanProject> requireMember(UUID actorId, UUID projectId) {
        KanbanProject project = projects.findProject(projectId);
        if (project == null) {
            return OperationResult.failure("PROJECT_NOT_FOUND", "That project does not exist.");
        }
        if (!project.isOwnerOrMember(actorId)) {
            return OperationResult.failure("FORBIDDEN", "You do not have access to that project.");
        }
        return OperationResult.success(project);
    }

    private KanbanCard findCard(KanbanProject project, UUID cardId) {
        return cardId == null ? null : project.findCard(cardId);
    }

    private BoardSnapshot boardSnapshot(KanbanProject project, UUID actorId) {
        List<MemberSummary> members = new ArrayList<MemberSummary>();
        members.add(new MemberSummary(project.getOwnerId(), profiles.usernameFor(project.getOwnerId()), true));
        for (UUID memberId : project.getMemberIds()) {
            members.add(new MemberSummary(memberId, profiles.usernameFor(memberId), false));
        }
        List<CardView> cards = new ArrayList<CardView>();
        for (KanbanCard card : project.getCards()) {
            cards.add(cardView(project, card));
        }
        return new BoardSnapshot(projectSummary(project, actorId), members, cards, projects.getSettings());
    }

    private ProjectSummary projectSummary(KanbanProject project, UUID actorId) {
        UUID done = projects.getSettings()
            .doneColumn();
        int finished = 0;
        for (KanbanCard card : project.getCards()) if (done.equals(card.getColumnId())) finished++;
        return new ProjectSummary(
            project.getId(),
            project.getName(),
            project.getOwnerId()
                .equals(actorId),
            project.getIcon(),
            project.getCards()
                .size(),
            finished);
    }

    private CardView cardView(KanbanCard card) {
        return cardView(null, card);
    }

    /** With a project, links to cards no longer in it are left out. */
    private CardView cardView(KanbanProject project, KanbanCard card) {
        List<List<UUID>> links = new ArrayList<List<UUID>>();
        for (CardLink link : CardLink.values()) {
            List<UUID> linked = new ArrayList<UUID>();
            for (UUID id : card.getLinks(link)) if (project == null || project.findCard(id) != null) linked.add(id);
            links.add(linked);
        }
        List<RequirementView> requirements = new ArrayList<RequirementView>();
        for (ItemRequirement requirement : card.getRequirements()) {
            requirements.add(requirementView(requirement));
        }
        List<TaskView> tasks = new ArrayList<TaskView>();
        for (CardTask task : card.getTasks()) tasks.add(new TaskView(task.getId(), task.getText(), task.isDone()));
        List<CommentView> comments = new ArrayList<CommentView>();
        for (CardComment comment : card.getComments()) {
            comments.add(
                new CommentView(
                    comment.getId(),
                    comment.getAuthorId(),
                    nameOf(comment.getAuthorId()),
                    comment.getCreatedAt(),
                    comment.getText()));
        }
        return new CardView(
            card.getId(),
            card.getNumber(),
            card.getTitle(),
            card.getDescription(),
            card.getColumnId(),
            card.getTypeId(),
            card.getPriority(),
            card.getCreatorId(),
            nameOf(card.getCreatorId()),
            card.getCreatedAt(),
            card.getIcon(),
            requirements,
            card.getAssigneeIds(),
            tasks,
            comments,
            links.get(CardLink.DEPENDS_ON.ordinal()),
            links.get(CardLink.BLOCKED_BY.ordinal()));
    }

    private String nameOf(UUID playerId) {
        return playerId == null ? "" : profiles.usernameFor(playerId);
    }

    private RequirementView requirementView(ItemRequirement requirement) {
        List<RequirementView> children = new ArrayList<RequirementView>();
        for (ItemRequirement child : requirement.getChildren()) children.add(requirementView(child));
        return new RequirementView(
            requirement.getId(),
            requirement.getItem(),
            requirement.getQuantity(),
            requirement.isComplete(),
            requirement.getAmountPerBatch(),
            requirement.isReusable(),
            requirement.getRecipeName(),
            requirement.getRecipeOutput(),
            requirement.getRevision(),
            children);
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String normalizedUsername = username.trim();
        if (normalizedUsername.length() == 0 || normalizedUsername.length() > MAX_USERNAME_LENGTH) {
            return null;
        }
        return normalizedUsername;
    }

    private <T> OperationResult<T> invalid(ValidationResult<?> validation) {
        return OperationResult.failure("INVALID_INPUT", validation.getErrorMessage());
    }

    private <T> OperationResult<T> failure(OperationResult<?> failure) {
        return OperationResult.failure(failure.getErrorCode(), failure.getMessage());
    }
}
