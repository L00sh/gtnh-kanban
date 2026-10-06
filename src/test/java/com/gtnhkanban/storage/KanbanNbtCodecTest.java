package com.gtnhkanban.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

import com.gtnhkanban.model.BoardSettings;
import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.ItemRequirement;
import com.gtnhkanban.model.KanbanCard;
import com.gtnhkanban.model.KanbanProject;

public class KanbanNbtCodecTest {

    @Test
    public void roundTripsProjectMembersCardsAndRequirements() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        KanbanProject project = new KanbanProject(UUID.randomUUID(), "Fusion Reactor", ownerId);
        project.addMember(memberId);
        project
            .addCard(card("Plan setup", "Decide the room layout.", BoardSettings.BACKLOG, "minecraft:stone", 2, false));
        project.addCard(
            card("Build", "Place the multiblock.", BoardSettings.IN_PROGRESS, "minecraft:iron_block", 3, true));
        project.addCard(card("Commission", "Start production.", BoardSettings.DONE, "minecraft:diamond", 1, true));

        NBTTagCompound encoded = new NBTTagCompound();
        KanbanNbtCodec.writeProject(encoded, project);
        KanbanProject decoded = KanbanNbtCodec.readProject(encoded);

        assertEquals(project.getId(), decoded.getId());
        assertEquals("Fusion Reactor", decoded.getName());
        assertEquals(ownerId, decoded.getOwnerId());
        assertTrue(
            decoded.getMemberIds()
                .contains(memberId));
        assertEquals(
            3,
            decoded.getCards()
                .size());

        KanbanCard inProgress = decoded.getCards()
            .get(1);
        assertEquals(BoardSettings.IN_PROGRESS, inProgress.getColumnId());
        assertEquals("Place the multiblock.", inProgress.getDescription());
        ItemRequirement requirement = inProgress.getRequirements()
            .get(0);
        assertEquals(new ItemKey("minecraft:iron_block", 7), requirement.getItem());
        assertEquals(3, requirement.getQuantity());
        assertTrue(requirement.isComplete());
    }

    @Test
    public void keepsUnknownRegistryNamesWithoutResolvingThem() {
        KanbanProject project = new KanbanProject(UUID.randomUUID(), "Unknown item", UUID.randomUUID());
        project.addCard(card("Find it", "", BoardSettings.BACKLOG, "missingmod:missing_block", 4, false));

        NBTTagCompound encoded = new NBTTagCompound();
        KanbanNbtCodec.writeProject(encoded, project);
        KanbanProject decoded = KanbanNbtCodec.readProject(encoded);

        ItemRequirement requirement = decoded.getCards()
            .get(0)
            .getRequirements()
            .get(0);
        assertNotNull(requirement);
        assertEquals(
            "missingmod:missing_block",
            requirement.getItem()
                .getRegistryName());
        assertEquals(
            7,
            requirement.getItem()
                .getMetadata());
        assertFalse(requirement.isComplete());
    }

    private static KanbanCard card(String title, String description, UUID status, String registryName, int quantity,
        boolean complete) {
        KanbanCard card = new KanbanCard(UUID.randomUUID(), title, description, status);
        card.addRequirement(new ItemRequirement(UUID.randomUUID(), new ItemKey(registryName, 7), quantity, complete));
        return card;
    }
}
