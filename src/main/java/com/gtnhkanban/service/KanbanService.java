package com.gtnhkanban.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.CardView;
import com.gtnhkanban.api.MemberSummary;
import com.gtnhkanban.api.ProjectSummary;
import com.gtnhkanban.api.RequirementView;
import com.gtnhkanban.model.CardStatus;
import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.ItemRequirement;
import com.gtnhkanban.model.KanbanCard;
import com.gtnhkanban.model.KanbanProject;
import com.gtnhkanban.storage.ProjectRepository;

/** Validated project operations shared by the Forge adapter and future external adapters. */
public final class KanbanService {

    private static final int MAX_USERNAME_LENGTH = 16;

    private final ProjectRepository projects;
    private final ProfileResolver profiles;
    private final ItemResolver items;

    public KanbanService(ProjectRepository projects, ProfileResolver profiles, ItemResolver items) {
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
        return OperationResult.success(boardSnapshot(authorized.getValue(), actorId));
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
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) {
            return failure(authorized);
        }
        ValidationResult<String> validTitle = BoardValidator.validateCardTitle(title);
        if (!validTitle.isValid()) {
            return invalid(validTitle);
        }
        ValidationResult<String> validDescription = BoardValidator.validateDescription(description);
        if (!validDescription.isValid()) {
            return invalid(validDescription);
        }
        KanbanCard card = new KanbanCard(
            UUID.randomUUID(),
            validTitle.getValue(),
            validDescription.getValue(),
            CardStatus.TODO);
        authorized.getValue()
            .addCard(card);
        projects.saveProject(authorized.getValue());
        return OperationResult.success(cardView(card));
    }

    public OperationResult<CardView> updateCard(UUID actorId, UUID projectId, UUID cardId, String title,
        String description, CardStatus status) {
        OperationResult<KanbanProject> authorized = requireMember(actorId, projectId);
        if (!authorized.isSuccess()) {
            return failure(authorized);
        }
        KanbanCard card = findCard(authorized.getValue(), cardId);
        if (card == null) {
            return OperationResult.failure("CARD_NOT_FOUND", "That card does not exist.");
        }
        ValidationResult<String> validTitle = BoardValidator.validateCardTitle(title);
        if (!validTitle.isValid()) {
            return invalid(validTitle);
        }
        ValidationResult<String> validDescription = BoardValidator.validateDescription(description);
        if (!validDescription.isValid()) {
            return invalid(validDescription);
        }
        if (status == null) {
            return OperationResult.failure("INVALID_STATUS", "A card status is required.");
        }
        card.setTitle(validTitle.getValue());
        card.setDescription(validDescription.getValue());
        card.setStatus(status);
        projects.saveProject(authorized.getValue());
        return OperationResult.success(cardView(card));
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
        if (item == null || !items.isRegistered(item)) {
            return OperationResult.failure("UNKNOWN_ITEM", "That item is not registered on this server.");
        }
        ItemRequirement requirement = new ItemRequirement(
            UUID.randomUUID(),
            item,
            validQuantity.getValue()
                .intValue(),
            false);
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
        projects.saveProject(authorized.getValue());
        return OperationResult.success(requirementView(requirement));
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
        return new BoardSnapshot(projectSummary(project, actorId), members, cards);
    }

    private ProjectSummary projectSummary(KanbanProject project, UUID actorId) {
        return new ProjectSummary(
            project.getId(),
            project.getName(),
            project.getOwnerId()
                .equals(actorId));
    }

    private CardView cardView(KanbanCard card) {
        List<RequirementView> requirements = new ArrayList<RequirementView>();
        for (ItemRequirement requirement : card.getRequirements()) {
            requirements.add(requirementView(requirement));
        }
        return new CardView(card.getId(), card.getTitle(), card.getDescription(), card.getStatus(), requirements);
    }

    private RequirementView requirementView(ItemRequirement requirement) {
        return new RequirementView(
            requirement.getId(),
            requirement.getItem(),
            requirement.getQuantity(),
            requirement.isComplete());
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
