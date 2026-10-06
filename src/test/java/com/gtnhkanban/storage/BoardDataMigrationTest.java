package com.gtnhkanban.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.gtnhkanban.model.BoardColumn;
import com.gtnhkanban.model.BoardSettings;
import com.gtnhkanban.model.CardComment;
import com.gtnhkanban.model.CardTask;
import com.gtnhkanban.model.CardType;
import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.KanbanCard;
import com.gtnhkanban.model.KanbanProject;
import com.gtnhkanban.model.Priority;

public class BoardDataMigrationTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void cardsFromBeforeColumnsMapToTheMatchingDefaultColumnAndGetNumbers() {
        NBTTagCompound project = new NBTTagCompound();
        project.setString(
            "id",
            UUID.randomUUID()
                .toString());
        project.setString("name", "Old");
        project.setString(
            "owner",
            UUID.randomUUID()
                .toString());
        NBTTagList cards = new NBTTagList();
        cards.appendTag(legacyCard("Plan", "TODO"));
        cards.appendTag(legacyCard("Build", "IN_PROGRESS"));
        cards.appendTag(legacyCard("Ship", "DONE"));
        project.setTag("cards", cards);
        List<String> problems = new java.util.ArrayList<String>();

        KanbanProject decoded = KanbanNbtCodec.readProject(project, problems);

        List<KanbanCard> read = decoded.getCards();
        assertEquals(
            BoardSettings.BACKLOG,
            read.get(0)
                .getColumnId());
        assertEquals(
            BoardSettings.IN_PROGRESS,
            read.get(1)
                .getColumnId());
        assertEquals(
            BoardSettings.DONE,
            read.get(2)
                .getColumnId());
        assertEquals(
            1,
            read.get(0)
                .getNumber());
        assertEquals(
            3,
            read.get(2)
                .getNumber());
        assertEquals(4, decoded.getNextCardNumber());
        assertEquals(
            Priority.NONE,
            read.get(0)
                .getPriority());
        assertNull(
            read.get(0)
                .getCreatorId());
        assertEquals(
            0,
            read.get(0)
                .getCreatedAt());
        assertTrue("Migration is not damage: " + problems, problems.isEmpty());
    }

    @Test
    public void newCardFieldsAndSettingsSurviveSaveAndLoad() {
        File file = new File(folder.getRoot(), KanbanStore.DATA_NAME + ".dat");
        KanbanStore store = KanbanStore.load(file);
        UUID creator = UUID.randomUUID();
        CardType rush = new CardType(UUID.randomUUID(), "Rush", 0x123456);
        BoardColumn only = new BoardColumn(UUID.randomUUID(), "Doing");
        store.saveSettings(new BoardSettings(Collections.singletonList(only), Collections.singletonList(rush)));
        KanbanProject project = new KanbanProject(UUID.randomUUID(), "New", creator);
        project.setIcon(new ItemKey("minecraft:furnace", 0));
        KanbanCard card = new KanbanCard(UUID.randomUUID(), "Leak", "Fix it", only.getId());
        card.setNumber(project.takeCardNumber());
        card.setTypeId(rush.getId());
        card.setPriority(Priority.MEDIUM);
        card.setCreatorId(creator);
        card.setCreatedAt(1234L);
        card.setIcon(new ItemKey("gregtech:gt.metaitem.01", 17809, false, "{a:1}"));
        card.addTask(new CardTask(UUID.randomUUID(), "Seal pipe", true));
        card.addComment(new CardComment(UUID.randomUUID(), creator, 99L, "On it"));
        project.addCard(card);
        store.saveProject(project);
        assertTrue(store.save());

        KanbanStore reloaded = KanbanStore.load(file);

        assertFalse(reloaded.isDirty());
        assertEquals(
            "Doing",
            reloaded.getSettings()
                .getColumns()
                .get(0)
                .getName());
        assertEquals(
            0x123456,
            reloaded.getSettings()
                .getTypes()
                .get(0)
                .getColor());
        KanbanProject loadedProject = reloaded.findProject(project.getId());
        assertEquals(new ItemKey("minecraft:furnace", 0), loadedProject.getIcon());
        assertEquals(2, loadedProject.getNextCardNumber());
        KanbanCard loaded = loadedProject.getCards()
            .get(0);
        assertEquals(1, loaded.getNumber());
        assertEquals(only.getId(), loaded.getColumnId());
        assertEquals(rush.getId(), loaded.getTypeId());
        assertEquals(Priority.MEDIUM, loaded.getPriority());
        assertEquals(creator, loaded.getCreatorId());
        assertEquals(1234L, loaded.getCreatedAt());
        assertEquals(card.getIcon(), loaded.getIcon());
        assertTrue(
            loaded.getTasks()
                .get(0)
                .isDone());
        assertEquals(
            "On it",
            loaded.getComments()
                .get(0)
                .getText());
        assertEquals(
            99L,
            loaded.getComments()
                .get(0)
                .getCreatedAt());
    }

    @Test
    public void cardsOnAMissingColumnOrTypeAreKeptAndMoved() {
        KanbanProject project = new KanbanProject(UUID.randomUUID(), "P", UUID.randomUUID());
        KanbanCard card = new KanbanCard(UUID.randomUUID(), "Orphan", "", UUID.randomUUID());
        card.setTypeId(UUID.randomUUID());
        project.addCard(card);
        BoardSettings settings = new BoardSettings(
            Arrays.asList(new BoardColumn(UUID.randomUUID(), "A"), new BoardColumn(UUID.randomUUID(), "B")),
            Collections.<CardType>emptyList());

        assertTrue(project.fitToSettings(settings));

        assertEquals(
            1,
            project.getCards()
                .size());
        assertEquals(settings.firstColumn(), card.getColumnId());
        assertNull(card.getTypeId());
        assertFalse(project.fitToSettings(settings));
    }

    @Test
    public void duplicateCardNumbersAreRepaired() {
        KanbanProject project = new KanbanProject(UUID.randomUUID(), "P", UUID.randomUUID());
        for (int i = 0; i < 2; i++) {
            KanbanCard card = new KanbanCard(UUID.randomUUID(), "Card " + i, "", BoardSettings.BACKLOG);
            card.setNumber(5);
            project.addCard(card);
        }
        NBTTagCompound encoded = new NBTTagCompound();
        KanbanNbtCodec.writeProject(encoded, project);
        List<String> problems = new java.util.ArrayList<String>();

        KanbanProject decoded = KanbanNbtCodec.readProject(encoded, problems);

        assertEquals(
            5,
            decoded.getCards()
                .get(0)
                .getNumber());
        assertEquals(
            6,
            decoded.getCards()
                .get(1)
                .getNumber());
        assertEquals(7, decoded.getNextCardNumber());
        assertEquals(1, problems.size());
    }

    private static NBTTagCompound legacyCard(String title, String status) {
        NBTTagCompound card = new NBTTagCompound();
        card.setString(
            "id",
            UUID.randomUUID()
                .toString());
        card.setString("title", title);
        card.setString("description", "");
        card.setString("status", status);
        return card;
    }
}
