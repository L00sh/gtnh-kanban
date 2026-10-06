package com.gtnhkanban.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.CardView;
import com.gtnhkanban.api.CommentView;
import com.gtnhkanban.api.MemberSummary;
import com.gtnhkanban.api.ProjectSummary;
import com.gtnhkanban.api.RequirementView;
import com.gtnhkanban.api.TaskView;
import com.gtnhkanban.model.BoardColumn;
import com.gtnhkanban.model.BoardSettings;
import com.gtnhkanban.model.CardComment;
import com.gtnhkanban.model.CardTask;
import com.gtnhkanban.model.CardType;
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
        authorized.getValue()
            .addMember(memberId);
        projects.saveProject(authorized.getValue());
        return OperationResult.success(null);
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
        long textGrowth = BoardSizeBudget.text(checked.title) + BoardSizeBudget.text(checked.description)
            - BoardSizeBudget.text(card.getTitle())
            - BoardSizeBudget.text(card.getDescription());
        if (textGrowth > 0 && !BoardSizeBudget.fits(project, textGrowth)) return boardTooLarge();
        card.setTitle(checked.title);
        card.setDescription(checked.description);
        if (checked.columnId != null) card.setColumnId(checked.columnId);
        card.setTypeId(checked.typeId);
        card.setPriority(checked.priority);
        card.setIcon(checked.icon);
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
        if (!authorized.getValue()
            .removeCard(cardId)) {
            return OperationResult.failure("CARD_NOT_FOUND", "That card does not exist.");
        }
        projects.saveProject(authorized.getValue());
        return OperationResult.success(null);
    }

    public OperationResult<CardView> moveCard(UUID actorId, UUID projectId, UUID cardId, UUID columnId) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        KanbanCard card = findCard(authorized.getValue(), cardId);
        if (card == null) return OperationResult.failure("CARD_NOT_FOUND", "That card does not exist.");
        if (columnId == null || !projects.getSettings()
            .hasColumn(columnId)) return OperationResult.failure("INVALID_COLUMN", "That column no longer exists.");
        card.setColumnId(columnId);
        projects.saveProject(authorized.getValue());
        return OperationResult.success(cardView(card));
    }

    public OperationResult<Void> setProjectIcon(UUID actorId, UUID projectId, ItemKey icon) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        if (icon != null && !isAcceptableMaterial(icon))
            return OperationResult.failure("UNKNOWN_ITEM", "That icon item is not registered on this server.");
        authorized.getValue()
            .setIcon(icon);
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
        task.setDone(done);
        projects.saveProject(authorized.getValue());
        return OperationResult.success(cardView(card));
    }

    public OperationResult<CardView> deleteTask(UUID actorId, UUID projectId, UUID cardId, UUID taskId) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        KanbanCard card = findCard(authorized.getValue(), cardId);
        if (card == null) return OperationResult.failure("CARD_NOT_FOUND", "That card does not exist.");
        if (taskId == null || !card.removeTask(taskId))
            return OperationResult.failure("TASK_NOT_FOUND", "That task does not exist.");
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
        projects.saveProject(authorized.getValue());
        return OperationResult.success(requirementView(requirement));
    }

    public OperationResult<Void> deleteRequirement(UUID actorId, UUID projectId, UUID cardId, UUID requirementId) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) return failure(authorized);
        KanbanCard card = findCard(authorized.getValue(), cardId);
        if (card == null) return OperationResult.failure("CARD_NOT_FOUND", "That card does not exist.");
        if (!card.removeRequirement(requirementId)) {
            return OperationResult.failure("REQUIREMENT_NOT_FOUND", "That checklist entry does not exist.");
        }
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
            cards.add(cardView(card));
        }
        return new BoardSnapshot(projectSummary(project, actorId), members, cards, projects.getSettings());
    }

    private ProjectSummary projectSummary(KanbanProject project, UUID actorId) {
        return new ProjectSummary(
            project.getId(),
            project.getName(),
            project.getOwnerId()
                .equals(actorId),
            project.getIcon());
    }

    private CardView cardView(KanbanCard card) {
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
            comments);
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
