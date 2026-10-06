package com.gtnhkanban.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.gtnhkanban.api.CardView;
import com.gtnhkanban.api.RequirementView;
import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.KanbanProject;
import com.gtnhkanban.model.RecipeIngredient;
import com.gtnhkanban.model.RecipePlan;
import com.gtnhkanban.model.RecipeTree;
import com.gtnhkanban.storage.ProjectRepository;

public class BreakdownServiceTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OUTSIDER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final ItemKey MACHINE = new ItemKey("gregtech:machine", 0);
    private static final ItemKey PLATE = new ItemKey("gregtech:plate", 0);
    private static final ItemKey INGOT = new ItemKey("gregtech:ingot", 0);
    private static final ItemKey HAMMER = new ItemKey("gregtech:hammer", 0);
    private static final ItemKey UNKNOWN = new ItemKey("missingmod:thing", 0);

    private final Map<UUID, KanbanProject> projects = new HashMap<UUID, KanbanProject>();
    private KanbanService service;
    private UUID projectId, cardId;

    @Before
    public void setUp() {
        final Set<ItemKey> registered = new HashSet<ItemKey>(Arrays.asList(MACHINE, PLATE, INGOT, HAMMER));
        service = new KanbanService(new ProjectRepository() {

            @Override
            public KanbanProject findProject(UUID id) {
                return projects.get(id);
            }

            @Override
            public List<KanbanProject> allProjects() {
                return new ArrayList<KanbanProject>(projects.values());
            }

            @Override
            public void saveProject(KanbanProject project) {
                projects.put(project.getId(), project);
            }

            @Override
            public boolean deleteProject(UUID id) {
                return projects.remove(id) != null;
            }
        }, new ProfileResolver() {

            @Override
            public UUID resolveUsername(String username) {
                return null;
            }

            @Override
            public String usernameFor(UUID playerId) {
                return playerId.toString();
            }
        }, new ItemResolver() {

            @Override
            public boolean isRegistered(ItemKey item) {
                return registered.contains(item);
            }
        });
        projectId = service.createProject(OWNER, "Steam age")
            .getValue()
            .getId();
        cardId = service.createCard(OWNER, projectId, "Build purifier", "")
            .getValue()
            .getId();
    }

    @Test
    public void addsANewItemWithItsWholeBreakdownAtOnce() {
        OperationResult<RequirementView> added = service
            .applyBreakdown(OWNER, projectId, cardId, null, MACHINE, 3, 0, machineTree());

        assertTrue(added.getMessage(), added.isSuccess());
        RequirementView root = onlyRow();
        assertEquals(MACHINE, root.getItem());
        assertEquals(3, root.getQuantity());
        RequirementView plates = root.getChildren()
            .get(0);
        assertEquals(6, plates.getQuantity());
        assertEquals(
            12,
            plates.getChildren()
                .get(0)
                .getQuantity());
        assertTrue(
            plates.getChildren()
                .get(1)
                .isReusable());
    }

    @Test
    public void laterQuantityChangesRescaleTheWholeTree() {
        service.applyBreakdown(OWNER, projectId, cardId, null, MACHINE, 1, 0, machineTree());

        assertTrue(
            service.setRequirementQuantity(OWNER, projectId, cardId, onlyRow().getId(), 5)
                .isSuccess());

        assertEquals(
            20,
            onlyRow().getChildren()
                .get(0)
                .getChildren()
                .get(0)
                .getQuantity());
    }

    @Test
    public void replacesAnExistingRowsBranch() {
        service.addRequirement(OWNER, projectId, cardId, MACHINE, 1);
        RequirementView row = onlyRow();

        OperationResult<RequirementView> applied = service
            .applyBreakdown(OWNER, projectId, cardId, row.getId(), null, 1, row.getRevision(), machineTree());

        assertTrue(applied.getMessage(), applied.isSuccess());
        assertEquals(
            1,
            onlyRow().getChildren()
                .size());
    }

    @Test
    public void refusesABreakdownForARowThatChangedMeanwhile() {
        service.addRequirement(OWNER, projectId, cardId, MACHINE, 1);
        RequirementView row = onlyRow();

        OperationResult<RequirementView> stale = service
            .applyBreakdown(OWNER, projectId, cardId, row.getId(), null, 2, row.getRevision(), machineTree());

        assertEquals("STALE_RECIPE", stale.getErrorCode());
        assertTrue(
            onlyRow().getChildren()
                .isEmpty());
    }

    @Test
    public void refusesLoopsUnknownItemsAndOutsiders() {
        RecipeTree loop = tree("Loop", ingredient(MACHINE, 1));
        assertEquals(
            "RECIPE_LOOP",
            service.applyBreakdown(OWNER, projectId, cardId, null, MACHINE, 1, 0, loop)
                .getErrorCode());
        assertEquals(
            "UNKNOWN_ITEM",
            service.applyBreakdown(OWNER, projectId, cardId, null, MACHINE, 1, 0, tree("Bad", ingredient(UNKNOWN, 1)))
                .getErrorCode());
        assertEquals(
            "FORBIDDEN",
            service.applyBreakdown(OUTSIDER, projectId, cardId, null, MACHINE, 1, 0, machineTree())
                .getErrorCode());
        assertTrue(
            card().getRequirements()
                .isEmpty());
    }

    @Test
    public void refusesTreesDeeperThanTheChecklistAllows() {
        RecipeTree deep = null;
        for (int level = 0; level < 16; level++) {
            ItemKey material = level % 2 == 0 ? PLATE : INGOT;
            deep = new RecipeTree(
                new RecipePlan("Step", 1, Collections.singletonList(ingredient(material, 1))),
                Collections.singletonList(deep));
        }

        // Alternating plate/ingot would also loop, but the depth check comes first and must hold on its own.
        assertEquals(
            "TREE_LIMIT",
            service.applyBreakdown(OWNER, projectId, cardId, null, MACHINE, 1, 0, deep)
                .getErrorCode());
    }

    @Test
    public void withoutATreeANewItemIsAddedPlainly() {
        assertTrue(
            service.applyBreakdown(OWNER, projectId, cardId, null, INGOT, 4, 0, null)
                .isSuccess());
        assertEquals(4, onlyRow().getQuantity());
    }

    /** Machine = 2 plates; plate = 2 ingots + a reusable hammer. */
    private static RecipeTree machineTree() {
        RecipeTree plate = tree("Shaped Crafting", ingredient(INGOT, 2), new RecipeIngredient(HAMMER, 1, true));
        return new RecipeTree(
            new RecipePlan("Shaped Crafting", 1, Collections.singletonList(ingredient(PLATE, 2))),
            Collections.singletonList(plate));
    }

    private static RecipeTree tree(String name, RecipeIngredient... ingredients) {
        return new RecipeTree(
            new RecipePlan(name, 1, Arrays.asList(ingredients)),
            Arrays.asList(new RecipeTree[ingredients.length]));
    }

    private static RecipeIngredient ingredient(ItemKey material, int amount) {
        return new RecipeIngredient(material, amount, false);
    }

    private CardView card() {
        for (CardView card : service.getBoard(OWNER, projectId)
            .getValue()
            .getCards())
            if (card.getId()
                .equals(cardId)) return card;
        throw new AssertionError("card missing");
    }

    private RequirementView onlyRow() {
        List<RequirementView> rows = card().getRequirements();
        assertEquals(1, rows.size());
        assertFalse(rows.isEmpty());
        return rows.get(0);
    }
}
