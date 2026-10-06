package com.gtnhkanban.storage;

import java.util.List;
import java.util.UUID;

import com.gtnhkanban.model.KanbanProject;

/** Storage boundary for project operations that does not depend on a live Minecraft world. */
public interface ProjectRepository {

    KanbanProject findProject(UUID projectId);

    List<KanbanProject> allProjects();

    void saveProject(KanbanProject project);
}
