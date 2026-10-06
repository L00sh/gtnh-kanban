package com.gtnhkanban.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.CardView;
import com.gtnhkanban.api.ProjectSummary;
import com.gtnhkanban.api.RequirementView;
import com.gtnhkanban.model.CardStatus;
import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.KanbanProject;
import com.gtnhkanban.storage.ProjectRepository;

public class KanbanServiceTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OUTSIDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final ItemKey STONE = new ItemKey("minecraft:stone", 0);
    private static final ItemKey UNKNOWN_ITEM = new ItemKey("missingmod:missing_block", 0);

    private InMemoryProjectRepository projects;
    private KanbanService service;

    @Before
    public void setUp() {
        projects = new InMemoryProjectRepository();
        service = new KanbanService(projects, new FakeProfiles(), new FakeItems(STONE));
    }

    @Test
    public void createsProjectForItsOwnerAndListsIt() {
        OperationResult<ProjectSummary> created = service.createProject(OWNER_ID, "  Fusion Reactor  ");

        assertTrue(created.isSuccess());
        assertEquals(
            "Fusion Reactor",
            created.getValue()
                .getName());
        assertTrue(
            created.getValue()
                .isActorIsOwner());
        assertEquals(
            1,
            service.listAccessibleProjects(OWNER_ID)
                .size());
        assertEquals(
            0,
            service.listAccessibleProjects(OUTSIDER_ID)
                .size());
    }

    @Test
    public void onlyOwnerCanChangeMembership() {
        UUID projectId = createProject();

        assertTrue(
            service.addMember(OWNER_ID, projectId, "Member")
                .isSuccess());
        assertFalse(
            service.addMember(MEMBER_ID, projectId, "Outsider")
                .isSuccess());
        assertTrue(
            projects.findProject(projectId)
                .getMemberIds()
                .contains(MEMBER_ID));
        assertFalse(
            projects.findProject(projectId)
                .getMemberIds()
                .contains(OUTSIDER_ID));
        assertFalse(
            service.removeMember(OWNER_ID, projectId, OWNER_ID)
                .isSuccess());
    }

    @Test
    public void memberCanCreateUpdateAndCheckCardRequirements() {
        UUID projectId = createProject();
        service.addMember(OWNER_ID, projectId, "Member");

        OperationResult<CardView> created = service.createCard(MEMBER_ID, projectId, "Power", "Build the power setup.");
        assertTrue(created.isSuccess());

        CardView card = created.getValue();
        assertTrue(
            service.updateCard(MEMBER_ID, projectId, card.getId(), "Power grid", "Updated", CardStatus.IN_PROGRESS)
                .isSuccess());
        OperationResult<RequirementView> requirement = service
            .addRequirement(MEMBER_ID, projectId, card.getId(), STONE, 64);
        assertTrue(requirement.isSuccess());
        assertTrue(
            service.setRequirementComplete(
                MEMBER_ID,
                projectId,
                card.getId(),
                requirement.getValue()
                    .getId(),
                true)
                .isSuccess());

        BoardSnapshot board = service.getBoard(MEMBER_ID, projectId)
            .getValue();
        assertEquals(
            CardStatus.IN_PROGRESS,
            board.getCards()
                .get(0)
                .getStatus());
        assertTrue(
            board.getCards()
                .get(0)
                .getRequirements()
                .get(0)
                .isComplete());
    }

    @Test
    public void deniesNonmembersAndRejectsStaleRequestsAfterRemoval() {
        UUID projectId = createProject();
        service.addMember(OWNER_ID, projectId, "Member");
        CardView card = service.createCard(OWNER_ID, projectId, "Card", "")
            .getValue();

        assertFalse(
            service.getBoard(OUTSIDER_ID, projectId)
                .isSuccess());
        assertFalse(
            service.createCard(OUTSIDER_ID, projectId, "Nope", "")
                .isSuccess());
        assertTrue(
            service.removeMember(OWNER_ID, projectId, MEMBER_ID)
                .isSuccess());
        assertFalse(
            service.updateCard(MEMBER_ID, projectId, card.getId(), "Stale", "", CardStatus.DONE)
                .isSuccess());
        assertEquals(
            "Card",
            projects.findProject(projectId)
                .findCard(card.getId())
                .getTitle());
    }

    @Test
    public void rejectsUnknownUsersAndLeavesMembershipUnchanged() {
        UUID projectId = createProject();

        assertFalse(
            service.addMember(OWNER_ID, projectId, "Nobody")
                .isSuccess());
        assertTrue(
            projects.findProject(projectId)
                .getMemberIds()
                .isEmpty());
    }

    @Test
    public void rejectsInvalidInputAndUnknownNewItemIdentitiesWithoutChanges() {
        assertFalse(
            service.createProject(OWNER_ID, "  ")
                .isSuccess());
        assertTrue(
            projects.allProjects()
                .isEmpty());

        UUID projectId = createProject();
        assertFalse(
            service.createCard(OWNER_ID, projectId, "", "")
                .isSuccess());
        CardView card = service.createCard(OWNER_ID, projectId, "Card", "")
            .getValue();

        assertFalse(
            service.addRequirement(OWNER_ID, projectId, card.getId(), STONE, 0)
                .isSuccess());
        assertFalse(
            service.addRequirement(OWNER_ID, projectId, card.getId(), UNKNOWN_ITEM, 1)
                .isSuccess());
        assertTrue(
            projects.findProject(projectId)
                .findCard(card.getId())
                .getRequirements()
                .isEmpty());
    }

    private UUID createProject() {
        OperationResult<ProjectSummary> created = service.createProject(OWNER_ID, "Project");
        assertTrue(created.isSuccess());
        assertNotNull(created.getValue());
        return created.getValue()
            .getId();
    }

    private static final class FakeProfiles implements ProfileResolver {

        private final Map<String, UUID> byUsername = new HashMap<String, UUID>();
        private final Map<UUID, String> byId = new HashMap<UUID, String>();

        private FakeProfiles() {
            add("Owner", OWNER_ID);
            add("Member", MEMBER_ID);
            add("Outsider", OUTSIDER_ID);
        }

        @Override
        public UUID resolveUsername(String username) {
            return byUsername.get(username);
        }

        @Override
        public String usernameFor(UUID playerId) {
            String username = byId.get(playerId);
            return username == null ? playerId.toString() : username;
        }

        private void add(String username, UUID playerId) {
            byUsername.put(username, playerId);
            byId.put(playerId, username);
        }
    }

    private static final class FakeItems implements ItemResolver {

        private final Set<ItemKey> registeredItems;

        private FakeItems(ItemKey... items) {
            registeredItems = new HashSet<ItemKey>(Arrays.asList(items));
        }

        @Override
        public boolean isRegistered(ItemKey item) {
            return registeredItems.contains(item);
        }
    }

    private static final class InMemoryProjectRepository implements ProjectRepository {

        private final Map<UUID, KanbanProject> projects = new HashMap<UUID, KanbanProject>();

        @Override
        public KanbanProject findProject(UUID projectId) {
            return projects.get(projectId);
        }

        @Override
        public List<KanbanProject> allProjects() {
            return new ArrayList<KanbanProject>(projects.values());
        }

        @Override
        public void saveProject(KanbanProject project) {
            projects.put(project.getId(), project);
        }
    }
}
