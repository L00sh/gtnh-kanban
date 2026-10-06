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

import com.gtnhkanban.api.CardView;
import com.gtnhkanban.model.BoardColumn;
import com.gtnhkanban.model.BoardSettings;
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

    private CardView create(String title) {
        return service.createCard(OWNER, projectId, title, "")
            .getValue();
    }
}
