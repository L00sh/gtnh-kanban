package com.gtnhkanban.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

import org.junit.Before;
import org.junit.Test;

import com.gtnhkanban.api.ActivityLog;
import com.gtnhkanban.api.CardView;
import com.gtnhkanban.model.ActivityEntry;
import com.gtnhkanban.model.BoardColumn;
import com.gtnhkanban.model.BoardSettings;
import com.gtnhkanban.model.CardLink;
import com.gtnhkanban.model.CardType;
import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.KanbanProject;
import com.gtnhkanban.model.Priority;
import com.gtnhkanban.storage.ProjectRepository;

public class BoardFeaturesServiceTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OUTSIDER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final ItemKey FURNACE = new ItemKey("minecraft:furnace", 0);
    private static final long NOW = 1_780_000_000_000L;

    private final Map<UUID, KanbanProject> stored = new HashMap<UUID, KanbanProject>();
    private BoardSettings settings = BoardSettings.defaults();
    private KanbanService service;
    private UUID projectId;

    @Before
    public void setUp() {
        service = new KanbanService(new ProjectRepository() {

            @Override
            public BoardSettings getSettings() {
                return settings;
            }

            @Override
            public void saveSettings(BoardSettings value) {
                settings = value;
            }

            @Override
            public KanbanProject findProject(UUID id) {
                return stored.get(id);
            }

            @Override
            public List<KanbanProject> allProjects() {
                return new ArrayList<KanbanProject>(stored.values());
            }

            @Override
            public void saveProject(KanbanProject project) {
                stored.put(project.getId(), project);
            }

            @Override
            public boolean deleteProject(UUID id) {
                return stored.remove(id) != null;
            }
        }, new ProfileResolver() {

            @Override
            public UUID resolveUsername(String username) {
                return "Member".equals(username) ? MEMBER : null;
            }

            @Override
            public String usernameFor(UUID playerId) {
                return OWNER.equals(playerId) ? "Owner" : "Member";
            }

            @Override
            public List<String> suggestedUsernames() {
                return Arrays.asList("member", "Zed", "alex", "Owner", "ALEX", "WayTooLongUsername123");
            }
        }, new ItemResolver() {

            @Override
            public boolean isRegistered(ItemKey item) {
                return item.equals(FURNACE);
            }
        }, new LongSupplier() {

            @Override
            public long getAsLong() {
                return NOW;
            }
        });
        projectId = service.createProject(OWNER, "Steam age")
            .getValue()
            .getId();
        service.addMember(OWNER, projectId, "Member");
    }

    @Test
    public void cardsAreNumberedPerProjectAndRecordCreatorAndTime() {
        CardView first = create("Boiler");
        CardView second = service.createCard(MEMBER, projectId, "Pump", "")
            .getValue();
        UUID otherProject = service.createProject(OWNER, "Other")
            .getValue()
            .getId();
        CardView elsewhere = service.createCard(OWNER, otherProject, "Pump", "")
            .getValue();

        assertEquals(1, first.getNumber());
        assertEquals(2, second.getNumber());
        assertEquals(1, elsewhere.getNumber());
        assertEquals(MEMBER, second.getCreatorId());
        assertEquals("Member", second.getCreatorName());
        assertEquals(NOW, second.getCreatedAt());
        assertEquals(BoardSettings.BACKLOG, first.getColumnId());
        assertEquals(Priority.NONE, first.getPriority());
        assertNull(first.getTypeId());
    }

    @Test
    public void numbersAreNeverReusedAfterDeletion() {
        CardView first = create("Boiler");
        service.deleteCard(OWNER, projectId, first.getId());

        assertEquals(2, create("Pump").getNumber());
    }

    @Test
    public void cardFieldsAreSavedAndValidated() {
        UUID bug = settings.getTypes()
            .get(0)
            .getId();
        CardView card = service
            .createCard(OWNER, projectId, new CardFields("Leak", "", BoardSettings.REVIEW, bug, Priority.HIGH, FURNACE))
            .getValue();

        assertEquals(BoardSettings.REVIEW, card.getColumnId());
        assertEquals(bug, card.getTypeId());
        assertEquals(Priority.HIGH, card.getPriority());
        assertEquals(FURNACE, card.getIcon());
        assertEquals(
            "INVALID_COLUMN",
            service
                .updateCard(
                    OWNER,
                    projectId,
                    card.getId(),
                    new CardFields("Leak", "", UUID.randomUUID(), null, null, null))
                .getErrorCode());
        assertEquals(
            "INVALID_TYPE",
            service
                .updateCard(
                    OWNER,
                    projectId,
                    card.getId(),
                    new CardFields("Leak", "", null, UUID.randomUUID(), null, null))
                .getErrorCode());
        assertEquals(
            "UNKNOWN_ITEM",
            service
                .updateCard(
                    OWNER,
                    projectId,
                    card.getId(),
                    new CardFields("Leak", "", null, null, null, new ItemKey("missing:thing", 0)))
                .getErrorCode());
        CardView cleared = service
            .updateCard(OWNER, projectId, card.getId(), new CardFields("Leak", "", null, null, Priority.NONE, null))
            .getValue();
        assertEquals("Edits without a column keep the card where it is", BoardSettings.REVIEW, cleared.getColumnId());
        assertNull(cleared.getTypeId());
        assertNull(cleared.getIcon());
    }

    @Test
    public void cardsMoveOnlyToExistingColumns() {
        CardView card = create("Boiler");

        assertEquals(
            BoardSettings.DONE,
            service.moveCard(MEMBER, projectId, card.getId(), BoardSettings.DONE)
                .getValue()
                .getColumnId());
        assertEquals(
            "INVALID_COLUMN",
            service.moveCard(MEMBER, projectId, card.getId(), UUID.randomUUID())
                .getErrorCode());
    }

    @Test
    public void cardsCanBeReorderedAndMovedToAPosition() {
        CardView first = create("Boiler");
        CardView second = create("Pump");
        CardView third = create("Tank");

        assertTrue(
            service.moveCard(MEMBER, projectId, third.getId(), BoardSettings.BACKLOG, first.getId())
                .isSuccess());
        List<CardView> cards = service.getBoard(OWNER, projectId)
            .getValue()
            .getCards();
        assertEquals(
            "Tank",
            cards.get(0)
                .getTitle());
        assertEquals(
            "Boiler",
            cards.get(1)
                .getTitle());
        assertEquals(
            "Pump",
            cards.get(2)
                .getTitle());
        assertEquals(
            "INVALID_POSITION",
            service.moveCard(MEMBER, projectId, second.getId(), BoardSettings.DONE, first.getId())
                .getErrorCode());
    }

    @Test
    public void tasksDriveTheProgressBar() {
        CardView card = create("Boiler");
        service.addTask(MEMBER, projectId, card.getId(), "  Place casings  ");
        CardView withTasks = service.addTask(MEMBER, projectId, card.getId(), "Fill water")
            .getValue();
        service.addRequirement(OWNER, projectId, card.getId(), FURNACE, 2);

        assertEquals(
            "Place casings",
            withTasks.getTasks()
                .get(0)
                .getText());
        CardView done = service.setTaskDone(
            MEMBER,
            projectId,
            card.getId(),
            withTasks.getTasks()
                .get(0)
                .getId(),
            true)
            .getValue();
        assertEquals(1, done.progressDone());
        assertEquals(3, done.progressTotal());
        assertEquals(
            "INVALID_INPUT",
            service.addTask(MEMBER, projectId, card.getId(), "   ")
                .getErrorCode());
        CardView removed = service.deleteTask(
            MEMBER,
            projectId,
            card.getId(),
            withTasks.getTasks()
                .get(1)
                .getId())
            .getValue();
        assertEquals(
            1,
            removed.getTasks()
                .size());
        assertEquals(
            "FORBIDDEN",
            service.addTask(OUTSIDER, projectId, card.getId(), "Sneaky")
                .getErrorCode());
    }

    @Test
    public void commentsCanBeDeletedByTheirAuthorOrTheOwner() {
        CardView card = create("Boiler");
        CardView commented = service.addComment(MEMBER, projectId, card.getId(), "Needs more steam")
            .getValue();
        UUID memberComment = commented.getComments()
            .get(0)
            .getId();
        UUID ownerComment = service.addComment(OWNER, projectId, card.getId(), "Agreed")
            .getValue()
            .getComments()
            .get(1)
            .getId();

        assertEquals(
            "Member",
            commented.getComments()
                .get(0)
                .getAuthorName());
        assertEquals(
            NOW,
            commented.getComments()
                .get(0)
                .getCreatedAt());
        assertEquals(
            "FORBIDDEN",
            service.deleteComment(MEMBER, projectId, card.getId(), ownerComment)
                .getErrorCode());
        assertTrue(
            service.deleteComment(MEMBER, projectId, card.getId(), memberComment)
                .isSuccess());
        assertTrue(
            service.deleteComment(OWNER, projectId, card.getId(), ownerComment)
                .isSuccess());
    }

    @Test
    public void removingAColumnMovesItsCardsAndRemovingATypeOnlyClearsIt() {
        UUID bug = settings.getTypes()
            .get(0)
            .getId();
        CardView card = service
            .createCard(OWNER, projectId, new CardFields("Leak", "", BoardSettings.REVIEW, bug, Priority.LOW, null))
            .getValue();
        List<BoardColumn> columns = new ArrayList<BoardColumn>(settings.getColumns());
        columns.remove(3);
        Collections.reverse(columns);
        List<CardType> types = new ArrayList<CardType>(
            settings.getTypes()
                .subList(1, 3));

        assertTrue(
            service.saveSettings(MEMBER, columns, types)
                .isSuccess());

        CardView after = service.getBoard(OWNER, projectId)
            .getValue()
            .getCards()
            .get(0);
        assertEquals(card.getId(), after.getId());
        assertEquals("Removed column moves the card to the new first column", BoardSettings.DONE, after.getColumnId());
        assertNull(after.getTypeId());
        assertEquals(Priority.LOW, after.getPriority());
        assertEquals(
            4,
            service.getSettings()
                .getColumns()
                .size());
    }

    @Test
    public void settingsAreValidated() {
        List<CardType> types = settings.getTypes();
        assertEquals(
            "FORBIDDEN",
            service.saveSettings(OUTSIDER, settings.getColumns(), types)
                .getErrorCode());
        assertEquals(
            "INVALID_INPUT",
            service.saveSettings(OWNER, Collections.<BoardColumn>emptyList(), types)
                .getErrorCode());
        assertEquals(
            "INVALID_INPUT",
            service.saveSettings(
                OWNER,
                Arrays.asList(new BoardColumn(UUID.randomUUID(), "Todo"), new BoardColumn(UUID.randomUUID(), " todo ")),
                types)
                .getErrorCode());
        assertEquals(
            "INVALID_INPUT",
            service.saveSettings(OWNER, Arrays.asList(new BoardColumn(UUID.randomUUID(), "  ")), types)
                .getErrorCode());
        assertEquals(
            5,
            settings.getColumns()
                .size());
    }

    @Test
    public void ownersGetSortedSuggestionsWithoutCurrentMembers() {
        assertEquals(
            Arrays.asList("alex", "Zed"),
            service.memberSuggestions(OWNER, projectId)
                .getValue());
        assertEquals(
            "FORBIDDEN",
            service.memberSuggestions(MEMBER, projectId)
                .getErrorCode());
    }

    @Test
    public void membersCanSetTheProjectIcon() {
        assertTrue(
            service.setProjectIcon(MEMBER, projectId, FURNACE)
                .isSuccess());
        assertEquals(
            FURNACE,
            service.listAccessibleProjects(MEMBER)
                .get(0)
                .getIcon());
        assertFalse(
            service.setProjectIcon(OUTSIDER, projectId, null)
                .isSuccess());
        assertTrue(
            service.setProjectIcon(OWNER, projectId, null)
                .isSuccess());
        assertNull(
            service.listAccessibleProjects(OWNER)
                .get(0)
                .getIcon());
    }

    @Test
    public void aCardCannotBeDoneUntilEverythingItDependsOnIsDone() {
        CardView boiler = create("Boiler");
        CardView pump = create("Pump");
        assertTrue(
            service.setCardLink(MEMBER, projectId, boiler.getId(), pump.getId(), CardLink.DEPENDS_ON, true)
                .isSuccess());

        OperationResult<CardView> early = service.moveCard(MEMBER, projectId, boiler.getId(), BoardSettings.DONE);
        assertFalse(early.isSuccess());
        assertEquals("DEPENDENCIES_NOT_DONE", early.getErrorCode());
        assertTrue(
            early.getMessage()
                .contains("#" + pump.getNumber() + " Pump"));
        assertFalse(
            "Saving the card into Done is refused the same way",
            service
                .updateCard(
                    OWNER,
                    projectId,
                    boiler.getId(),
                    new CardFields("Boiler", "", BoardSettings.DONE, null, Priority.NONE, null))
                .isSuccess());

        assertTrue(
            service.moveCard(MEMBER, projectId, pump.getId(), BoardSettings.DONE)
                .isSuccess());
        assertTrue(
            service.moveCard(MEMBER, projectId, boiler.getId(), BoardSettings.DONE)
                .isSuccess());
    }

    @Test
    public void blockersNeverStopAMove() {
        CardView boiler = create("Boiler");
        CardView pump = create("Pump");
        service.setCardLink(MEMBER, projectId, boiler.getId(), pump.getId(), CardLink.BLOCKED_BY, true);

        assertTrue(
            service.moveCard(MEMBER, projectId, boiler.getId(), BoardSettings.DONE)
                .isSuccess());
        assertEquals(
            Collections.singletonList(pump.getId()),
            stored.get(projectId)
                .findCard(boiler.getId())
                .getLinks(CardLink.BLOCKED_BY)
                .stream()
                .collect(java.util.stream.Collectors.toList()));
    }

    @Test
    public void dependenciesCannotLoopOrPointAtTheCardItself() {
        CardView a = create("A"), b = create("B"), c = create("C");
        service.setCardLink(OWNER, projectId, a.getId(), b.getId(), CardLink.DEPENDS_ON, true);
        service.setCardLink(OWNER, projectId, b.getId(), c.getId(), CardLink.DEPENDS_ON, true);

        OperationResult<CardView> loop = service
            .setCardLink(OWNER, projectId, c.getId(), a.getId(), CardLink.DEPENDS_ON, true);
        assertEquals("DEPENDENCY_LOOP", loop.getErrorCode());
        assertEquals(
            "INVALID_LINK",
            service.setCardLink(OWNER, projectId, a.getId(), a.getId(), CardLink.BLOCKED_BY, true)
                .getErrorCode());
        assertTrue(
            "Blockers may point back; they are only a note",
            service.setCardLink(OWNER, projectId, c.getId(), a.getId(), CardLink.BLOCKED_BY, true)
                .isSuccess());
        assertFalse(
            "Outsiders cannot link cards",
            service.setCardLink(OUTSIDER, projectId, c.getId(), b.getId(), CardLink.BLOCKED_BY, true)
                .isSuccess());
    }

    @Test
    public void deletingACardRemovesLinksToIt() {
        CardView boiler = create("Boiler");
        CardView pump = create("Pump");
        service.setCardLink(OWNER, projectId, boiler.getId(), pump.getId(), CardLink.DEPENDS_ON, true);
        service.setCardLink(OWNER, projectId, boiler.getId(), pump.getId(), CardLink.BLOCKED_BY, true);

        service.deleteCard(OWNER, projectId, pump.getId());

        assertTrue(
            stored.get(projectId)
                .findCard(boiler.getId())
                .getLinks(CardLink.DEPENDS_ON)
                .isEmpty());
        assertTrue(
            stored.get(projectId)
                .findCard(boiler.getId())
                .getLinks(CardLink.BLOCKED_BY)
                .isEmpty());
        assertTrue(
            service.moveCard(OWNER, projectId, boiler.getId(), BoardSettings.DONE)
                .isSuccess());
    }

    @Test
    public void onlyTheCreatorOrOwnerCanChangeADescription() {
        CardView byOwner = create("Boiler");
        OperationResult<CardView> memberEdit = service.updateCard(
            MEMBER,
            projectId,
            byOwner.getId(),
            new CardFields("Boiler", "Changed", null, null, Priority.NONE, null));
        assertEquals("DESCRIPTION_LOCKED", memberEdit.getErrorCode());
        assertTrue(
            "Other fields can still be edited by members",
            service
                .updateCard(
                    MEMBER,
                    projectId,
                    byOwner.getId(),
                    new CardFields("Big boiler", "", null, null, Priority.HIGH, null))
                .isSuccess());

        CardView byMember = service.createCard(MEMBER, projectId, "Pump", "")
            .getValue();
        assertTrue(
            service.updateCard(MEMBER, projectId, byMember.getId(), CardFields.of("Pump", "Mine"))
                .isSuccess());
        assertTrue(
            service.updateCard(OWNER, projectId, byMember.getId(), CardFields.of("Pump", "Owner's edit"))
                .isSuccess());
    }

    @Test
    public void theActivityLogRecordsChangesNewestFirst() {
        CardView card = create("Boiler");
        service.updateCard(
            OWNER,
            projectId,
            card.getId(),
            new CardFields("Big boiler", "", null, null, Priority.HIGH, null));
        service.moveCard(MEMBER, projectId, card.getId(), BoardSettings.DONE);
        service.addTask(MEMBER, projectId, card.getId(), "Fill water");

        List<ActivityLog.Entry> entries = service.getActivity(MEMBER, projectId)
            .getValue()
            .getEntries();
        assertEquals(ActivityEntry.Kind.TASKS, entries.get(0).kind);
        assertEquals(ActivityEntry.Kind.CARD_MOVED, entries.get(1).kind);
        assertEquals(MEMBER, entries.get(1).actorId);
        assertTrue(entries.get(1).detail.endsWith("-> Done"));
        assertEquals(ActivityEntry.Kind.CARD_EDITED, entries.get(2).kind);
        assertTrue(entries.get(2).detail.contains("Renamed from \"Boiler\" to \"Big boiler\""));
        assertTrue(entries.get(2).detail.contains("Priority: None -> High"));
        assertEquals(ActivityEntry.Kind.CARD_CREATED, entries.get(3).kind);
        assertFalse(
            "Outsiders cannot read the log",
            service.getActivity(OUTSIDER, projectId)
                .isSuccess());
    }

    @Test
    public void deletedCardsCanBeRestoredByTheirCreatorOrTheOwner() {
        CardView byMember = service.createCard(MEMBER, projectId, "Pump", "")
            .getValue();
        CardView byOwner = create("Boiler");
        service.addTask(MEMBER, projectId, byMember.getId(), "Prime it");
        service.deleteCard(OWNER, projectId, byMember.getId());
        service.deleteCard(MEMBER, projectId, byOwner.getId());

        List<ActivityLog.Deleted> deleted = service.getActivity(MEMBER, projectId)
            .getValue()
            .getDeleted();
        assertEquals(2, deleted.size());
        assertEquals("Newest first", byOwner.getId(), deleted.get(0).cardId);
        assertFalse("A member cannot restore the owner's card", deleted.get(0).canRestore);
        assertTrue(deleted.get(1).canRestore);
        assertEquals(
            "RESTORE_NOT_ALLOWED",
            service.restoreCard(MEMBER, projectId, byOwner.getId())
                .getErrorCode());

        CardView restored = service.restoreCard(MEMBER, projectId, byMember.getId())
            .getValue();
        assertEquals(byMember.getNumber(), restored.getNumber());
        assertEquals(
            "Restored whole, tasks included",
            1,
            restored.getTasks()
                .size());
        assertTrue(
            service.restoreCard(OWNER, projectId, byOwner.getId())
                .isSuccess());
        assertEquals(
            "CARD_NOT_FOUND",
            service.restoreCard(OWNER, projectId, byOwner.getId())
                .getErrorCode());
    }

    @Test
    public void aRestoredCardWhoseNameWasTakenIsRenamed() {
        CardView first = create("Boiler");
        service.deleteCard(OWNER, projectId, first.getId());
        create("Boiler");

        CardView restored = service.restoreCard(OWNER, projectId, first.getId())
            .getValue();
        assertEquals("Boiler (restored)", restored.getTitle());
    }

    private CardView create(String title) {
        return service.createCard(OWNER, projectId, title, "")
            .getValue();
    }
}
