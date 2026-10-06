package com.gtnhkanban.storage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.gtnhkanban.model.CardStatus;
import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.ItemRequirement;
import com.gtnhkanban.model.KanbanCard;
import com.gtnhkanban.model.KanbanProject;

public class KanbanStoreTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File file;
    private File backup;

    @Before
    public void setUp() {
        file = new File(folder.getRoot(), KanbanStore.DATA_NAME + ".dat");
        backup = new File(file.getPath() + "_old");
    }

    @Test
    public void missingFileStartsEmptyAndWritesNothingUntilChanged() {
        KanbanStore store = KanbanStore.load(file);

        assertTrue(store.isWritable());
        assertTrue(
            store.allProjects()
                .isEmpty());
        assertTrue(store.save());
        assertFalse(file.exists());
    }

    @Test
    public void savedProjectsLoadBack() {
        KanbanStore store = KanbanStore.load(file);
        KanbanProject project = project("Fusion Reactor");
        store.saveProject(project);

        assertTrue(store.save());
        KanbanStore reloaded = KanbanStore.load(file);

        assertEquals(
            "Fusion Reactor",
            reloaded.findProject(project.getId())
                .getName());
        assertFalse(reloaded.isDirty());
    }

    @Test
    public void readsFilesWrittenByTheOriginalWorldSavedDataLayout() throws IOException {
        KanbanProject project = project("Legacy");
        NBTTagCompound encoded = new NBTTagCompound();
        KanbanNbtCodec.writeProject(encoded, project);
        writeRoot(file, encoded);

        KanbanStore store = KanbanStore.load(file);

        assertNotNull(store.findProject(project.getId()));
        assertFalse(store.isDirty());
    }

    @Test
    public void secondSaveKeepsPreviousSaveAsBackup() {
        KanbanStore store = KanbanStore.load(file);
        KanbanProject first = project("First");
        store.saveProject(first);
        store.save();
        store.saveProject(project("Second"));
        store.save();

        KanbanStore fromBackup = KanbanStore.load(backup);

        assertEquals(
            1,
            fromBackup.allProjects()
                .size());
        assertNotNull(fromBackup.findProject(first.getId()));
    }

    @Test
    public void truncatedFileRecoversFromBackupAndKeepsDamagedCopy() throws IOException {
        KanbanStore store = KanbanStore.load(file);
        KanbanProject project = project("Survivor");
        store.saveProject(project);
        store.save();
        store.saveProject(project("Newer"));
        store.save();
        byte[] whole = Files.readAllBytes(file.toPath());
        byte[] truncated = new byte[whole.length / 2];
        System.arraycopy(whole, 0, truncated, 0, truncated.length);
        Files.write(file.toPath(), truncated);

        KanbanStore recovered = KanbanStore.load(file);

        assertTrue(recovered.isWritable());
        assertNotNull(recovered.findProject(project.getId()));
        assertArrayEquals(truncated, Files.readAllBytes(damagedCopy().toPath()));
        // Recovering must not copy the unreadable live file over the good backup.
        byte[] goodBackup = Files.readAllBytes(backup.toPath());
        assertTrue(recovered.save());
        assertArrayEquals(goodBackup, Files.readAllBytes(backup.toPath()));
        assertNotNull(
            KanbanStore.load(file)
                .findProject(project.getId()));
    }

    @Test
    public void unreadableFileWithoutBackupIsNeverOverwritten() throws IOException {
        byte[] garbage = { 1, 2, 3, 4, 5 };
        Files.write(file.toPath(), garbage);

        KanbanStore store = KanbanStore.load(file);

        assertFalse(store.isWritable());
        assertFalse(store.save());
        assertArrayEquals(garbage, Files.readAllBytes(file.toPath()));
        try {
            store.saveProject(project("Lost"));
            throw new AssertionError("Expected read-only store to refuse changes");
        } catch (IllegalStateException expected) {}
        assertArrayEquals(garbage, Files.readAllBytes(file.toPath()));
    }

    @Test
    public void damagedEntriesAreRepairedWithoutLosingOtherProjects() throws IOException {
        KanbanProject before = project("Before");
        KanbanProject after = project("After");
        NBTTagCompound broken = new NBTTagCompound();
        KanbanNbtCodec.writeProject(broken, project("Broken"));
        NBTTagCompound card = broken.getTagList("cards", 10)
            .getCompoundTagAt(0);
        card.setString("id", "not-a-uuid");
        card.getTagList("requirements", 10)
            .getCompoundTagAt(0)
            .setInteger("quantity", 0);
        NBTTagCompound ownerless = new NBTTagCompound();
        KanbanNbtCodec.writeProject(ownerless, project("Ownerless"));
        ownerless.setString("owner", "");
        writeRoot(file, encode(before), broken, ownerless, encode(after));
        byte[] original = Files.readAllBytes(file.toPath());

        KanbanStore store = KanbanStore.load(file);

        assertTrue(store.isWritable());
        assertTrue(store.isDirty());
        assertEquals(
            3,
            store.allProjects()
                .size());
        assertNotNull(store.findProject(before.getId()));
        assertNotNull(store.findProject(after.getId()));
        KanbanProject repaired = store.allProjects()
            .get(1);
        assertEquals(
            1,
            repaired.getCards()
                .get(0)
                .getRequirements()
                .get(0)
                .getQuantity());
        assertArrayEquals(original, Files.readAllBytes(damagedCopy().toPath()));
    }

    @Test
    public void failedSaveLeavesPreviousFileIntactAndRetries() throws IOException {
        KanbanStore store = KanbanStore.load(file);
        KanbanProject project = project("Kept");
        store.saveProject(project);
        store.save();
        byte[] saved = Files.readAllBytes(file.toPath());
        File temp = new File(file.getPath() + ".tmp");
        assertTrue(temp.mkdir());
        store.saveProject(project("Pending"));

        assertFalse(store.save());
        assertArrayEquals(saved, Files.readAllBytes(file.toPath()));
        assertTrue(store.isDirty());

        assertTrue(temp.delete());
        assertTrue(store.save());
        assertEquals(
            2,
            KanbanStore.load(file)
                .allProjects()
                .size());
    }

    private File damagedCopy() {
        File[] copies = folder.getRoot()
            .listFiles((dir, name) -> name.startsWith(KanbanStore.DATA_NAME + ".dat.damaged-"));
        assertNotNull(copies);
        assertEquals(1, copies.length);
        return copies[0];
    }

    private static KanbanProject project(String name) {
        KanbanProject project = new KanbanProject(UUID.randomUUID(), name, UUID.randomUUID());
        KanbanCard card = new KanbanCard(UUID.randomUUID(), "Card", "", CardStatus.TODO);
        card.addRequirement(new ItemRequirement(UUID.randomUUID(), new ItemKey("minecraft:stone", 0), 4, false));
        project.addCard(card);
        return project;
    }

    private static NBTTagCompound encode(KanbanProject project) {
        NBTTagCompound encoded = new NBTTagCompound();
        KanbanNbtCodec.writeProject(encoded, project);
        return encoded;
    }

    private static void writeRoot(File target, NBTTagCompound... encodedProjects) throws IOException {
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound encoded : encodedProjects) list.appendTag(encoded);
        NBTTagCompound data = new NBTTagCompound();
        data.setTag("projects", list);
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("data", data);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(root, bytes);
        FileOutputStream output = new FileOutputStream(target);
        try {
            output.write(bytes.toByteArray());
        } finally {
            output.close();
        }
    }
}
