package com.gtnhkanban.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnhkanban.model.BoardSettings;
import com.gtnhkanban.model.KanbanProject;

/**
 * Project store kept in its own file in the world's data directory, independent of vanilla's WorldSavedData.
 *
 * <p>
 * Safety rules: a save writes and verifies a temporary file, then atomically replaces the live file, keeping the
 * previous save as a backup, so a crash mid-save never leaves a truncated file. A file that loads with damage is
 * copied aside before anything can overwrite it. If no copy of the data can be read or preserved, the store becomes
 * read-only and never touches the files on disk.
 */
public final class KanbanStore implements ProjectRepository {

    /** Same name and layout as the original WorldSavedData file, so existing worlds keep their projects. */
    public static final String DATA_NAME = "gtnhkanban_projects";

    private static final Logger LOG = LogManager.getLogger("GTNH Kanban");
    private static final String DATA = "data";
    private static final String PROJECTS = "projects";
    private static final String SETTINGS = "settings";
    private static final int NBT_COMPOUND = 10;

    private final File file;
    private final File backupFile;
    private final File tempFile;
    private final Map<UUID, KanbanProject> projects = new LinkedHashMap<UUID, KanbanProject>();
    private BoardSettings settings = BoardSettings.defaults();
    private boolean dirty;
    private boolean writable = true;
    /** False while the live file is unreadable, so a save never copies it over a good backup. */
    private boolean liveFileReadable = true;

    private KanbanStore(File file) {
        this.file = file;
        this.backupFile = new File(file.getPath() + "_old");
        this.tempFile = new File(file.getPath() + ".tmp");
    }

    /** Loads the store from {@code file}, falling back to its backup. Never throws and never modifies the files. */
    public static KanbanStore load(File file) {
        KanbanStore store = new KanbanStore(file);
        store.loadFromDisk();
        return store;
    }

    private void loadFromDisk() {
        if (!file.exists() && !backupFile.exists()) return;

        List<String> problems = new ArrayList<String>();
        if (file.exists()) {
            try {
                readFile(file, problems);
                if (problems.isEmpty()) return;
                LOG.warn("Kanban data in {} needed repairs:", file);
                for (String problem : problems) LOG.warn("  {}", problem);
                // Keep the damaged original so nothing is lost when the repaired data is saved over it.
                if (!preserveDamagedFile()) writable = false;
                else dirty = true;
                return;
            } catch (Throwable exception) {
                rethrowFatal(exception);
                LOG.error("Could not read kanban data from {}", file, exception);
                projects.clear();
                settings = BoardSettings.defaults();
                liveFileReadable = false;
                if (!preserveDamagedFile()) {
                    writable = false;
                    return;
                }
            }
        }

        if (backupFile.exists()) {
            problems.clear();
            try {
                readFile(backupFile, problems);
                for (String problem : problems) LOG.warn("  {}", problem);
                LOG.warn("Recovered kanban data from backup {}", backupFile);
                dirty = true;
                return;
            } catch (Throwable exception) {
                rethrowFatal(exception);
                LOG.error("Could not read kanban backup from {}", backupFile, exception);
                projects.clear();
                settings = BoardSettings.defaults();
            }
        }

        LOG.error(
            "No readable kanban data found. Kanban editing is disabled for this world and {} is left untouched;"
                + " repair or remove it (and {}) to re-enable editing.",
            file,
            backupFile);
        writable = false;
    }

    /**
     * Vanilla NBT reports some malformed data through its crash-report machinery rather than an IOException, so loading
     * treats any failure as unreadable data. Only failures of the JVM itself propagate.
     */
    private static void rethrowFatal(Throwable throwable) {
        if (throwable instanceof VirtualMachineError) throw (VirtualMachineError) throwable;
    }

    private void readFile(File source, List<String> problems) throws IOException {
        NBTTagCompound root;
        InputStream input = new FileInputStream(source);
        try {
            root = CompressedStreamTools.readCompressed(input);
        } finally {
            input.close();
        }
        if (!root.hasKey(DATA, NBT_COMPOUND)) throw new IOException("Missing '" + DATA + "' compound");
        NBTTagCompound data = root.getCompoundTag(DATA);
        settings = data.hasKey(SETTINGS, NBT_COMPOUND)
            ? KanbanNbtCodec.readSettings(data.getCompoundTag(SETTINGS), problems)
            : BoardSettings.defaults();
        NBTTagList encodedProjects = data.getTagList(PROJECTS, NBT_COMPOUND);
        for (int index = 0; index < encodedProjects.tagCount(); index++) {
            KanbanProject project;
            try {
                project = KanbanNbtCodec.readProject(encodedProjects.getCompoundTagAt(index), problems);
            } catch (RuntimeException exception) {
                problems.add("Skipped unreadable project #" + index + ": " + exception);
                continue;
            }
            if (projects.containsKey(project.getId())) {
                problems.add("Skipped project with duplicate id " + project.getId());
                continue;
            }
            projects.put(project.getId(), project);
        }
        for (KanbanProject project : projects.values()) {
            if (project.fitToSettings(settings))
                problems.add("Moved cards in project " + project.getId() + " off a removed column or type");
        }
    }

    private boolean preserveDamagedFile() {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File target = new File(file.getPath() + ".damaged-" + stamp);
        for (int suffix = 1; target.exists(); suffix++) {
            target = new File(file.getPath() + ".damaged-" + stamp + "-" + suffix);
        }
        try {
            Files.copy(file.toPath(), target.toPath());
            LOG.warn("Kept a copy of the damaged kanban data at {}", target);
            return true;
        } catch (IOException exception) {
            LOG.error("Could not copy damaged kanban data to {}", target, exception);
            return false;
        }
    }

    /**
     * Writes pending changes to disk if there are any. Never throws: a failed save logs the error, leaves the files on
     * disk as they were, and stays pending so the next save retries.
     *
     * @return whether the data on disk now matches memory
     */
    public boolean save() {
        if (!writable) return false;
        if (!dirty) return true;
        try {
            byte[] encoded = encode();
            verify(encoded);
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Could not create " + parent);
            }
            FileOutputStream output = new FileOutputStream(tempFile);
            try {
                output.write(encoded);
                output.getFD()
                    .sync();
            } finally {
                output.close();
            }
            if (file.exists() && liveFileReadable) {
                Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(
                    tempFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            liveFileReadable = true;
            return true;
        } catch (Exception exception) {
            LOG.error("Could not save kanban data to {}; the previous save is unchanged", file, exception);
            return false;
        }
    }

    private byte[] encode() throws IOException {
        NBTTagList encodedProjects = new NBTTagList();
        for (KanbanProject project : projects.values()) {
            NBTTagCompound encodedProject = new NBTTagCompound();
            KanbanNbtCodec.writeProject(encodedProject, project);
            encodedProjects.appendTag(encodedProject);
        }
        NBTTagCompound data = new NBTTagCompound();
        data.setTag(PROJECTS, encodedProjects);
        data.setTag(SETTINGS, KanbanNbtCodec.writeSettings(settings));
        NBTTagCompound root = new NBTTagCompound();
        root.setTag(DATA, data);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(root, bytes);
        return bytes.toByteArray();
    }

    /** Re-reads freshly encoded bytes so a save that would not load back is never written. */
    private void verify(byte[] encoded) throws IOException {
        NBTTagCompound root = CompressedStreamTools.readCompressed(new ByteArrayInputStream(encoded));
        int count = root.getCompoundTag(DATA)
            .getTagList(PROJECTS, NBT_COMPOUND)
            .tagCount();
        if (count != projects.size()) {
            throw new IOException("Encoded " + count + " projects but expected " + projects.size());
        }
    }

    /** False when no copy of the data could be read or preserved; mutations must then be refused. */
    public boolean isWritable() {
        return writable;
    }

    public boolean isDirty() {
        return dirty;
    }

    @Override
    public BoardSettings getSettings() {
        return settings;
    }

    @Override
    public void saveSettings(BoardSettings settings) {
        requireWritable();
        this.settings = settings;
        dirty = true;
    }

    @Override
    public KanbanProject findProject(UUID projectId) {
        return projects.get(projectId);
    }

    @Override
    public List<KanbanProject> allProjects() {
        return Collections.unmodifiableList(new ArrayList<KanbanProject>(projects.values()));
    }

    @Override
    public void saveProject(KanbanProject project) {
        requireWritable();
        projects.put(project.getId(), project);
        dirty = true;
    }

    @Override
    public boolean deleteProject(UUID projectId) {
        requireWritable();
        boolean removed = projects.remove(projectId) != null;
        if (removed) dirty = true;
        return removed;
    }

    private void requireWritable() {
        if (!writable) throw new IllegalStateException("Kanban data could not be loaded; editing is disabled.");
    }
}
