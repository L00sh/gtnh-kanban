package com.gtnhkanban.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;

import com.gtnhkanban.model.KanbanProject;

/** World-scoped project store shared by every dimension on a server. */
public final class KanbanWorldData extends WorldSavedData implements ProjectRepository {

    private static final String DATA_NAME = "gtnhkanban_projects";
    private static final String PROJECTS = "projects";
    private static final int NBT_COMPOUND = 10;

    private NBTTagCompound unreadableSource;
    private final Map<UUID, KanbanProject> projects = new LinkedHashMap<UUID, KanbanProject>();

    public KanbanWorldData() {
        this(DATA_NAME);
    }

    public KanbanWorldData(String name) {
        super(name);
    }

    public static KanbanWorldData get(World world) {
        World overworld = world.provider.dimensionId == 0 ? world
            : MinecraftServer.getServer()
                .worldServerForDimension(0);
        MapStorage storage = overworld.perWorldStorage;
        KanbanWorldData data = (KanbanWorldData) storage.loadData(KanbanWorldData.class, DATA_NAME);
        if (data == null) {
            data = new KanbanWorldData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound source) {
        Map<UUID, KanbanProject> loaded = new LinkedHashMap<UUID, KanbanProject>();
        unreadableSource = null;
        try {
            NBTTagList encodedProjects = source.getTagList(PROJECTS, NBT_COMPOUND);
            for (int index = 0; index < encodedProjects.tagCount(); index++) {
                KanbanProject project = KanbanNbtCodec.readProject(encodedProjects.getCompoundTagAt(index));
                loaded.put(project.getId(), project);
            }
            projects.clear();
            projects.putAll(loaded);
        } catch (RuntimeException exception) {
            unreadableSource = (NBTTagCompound) source.copy();
            cpw.mods.fml.common.FMLLog.severe(
                "Kanban world data could not be loaded; edits are disabled to preserve the original save: %s",
                exception.toString());
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound target) {
        if (unreadableSource != null) {
            for (String key : unreadableSource.func_150296_c()) target.setTag(
                key,
                unreadableSource.getTag(key)
                    .copy());
            return;
        }
        NBTTagList encodedProjects = new NBTTagList();
        for (KanbanProject project : projects.values()) {
            NBTTagCompound encodedProject = new NBTTagCompound();
            KanbanNbtCodec.writeProject(encodedProject, project);
            encodedProjects.appendTag(encodedProject);
        }
        target.setTag(PROJECTS, encodedProjects);
    }

    @Override
    public KanbanProject findProject(UUID projectId) {
        requireReadable();
        return projects.get(projectId);
    }

    @Override
    public List<KanbanProject> allProjects() {
        requireReadable();
        return Collections.unmodifiableList(new ArrayList<KanbanProject>(projects.values()));
    }

    @Override
    public void saveProject(KanbanProject project) {
        requireReadable();
        projects.put(project.getId(), project);
        markDirty();
    }

    private void requireReadable() {
        if (unreadableSource != null) throw new IllegalStateException(
            "Kanban save could not be loaded. Original data is preserved; inspect the server log.");
    }

    @Override
    public boolean deleteProject(UUID projectId) {
        requireReadable();
        boolean removed = projects.remove(projectId) != null;
        if (removed) markDirty();
        return removed;
    }
}
