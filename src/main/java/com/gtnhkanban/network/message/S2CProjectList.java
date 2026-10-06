package com.gtnhkanban.network.message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.gtnhkanban.api.ProjectSummary;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public final class S2CProjectList implements IMessage {

    private List<ProjectSummary> projects = Collections.emptyList();

    public S2CProjectList() {}

    public S2CProjectList(List<ProjectSummary> projects) {
        this.projects = new ArrayList<ProjectSummary>(projects);
    }

    public List<ProjectSummary> getProjects() {
        return Collections.unmodifiableList(projects);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(projects.size());
        for (ProjectSummary project : projects) {
            PacketData.writeUuid(buffer, project.getId());
            PacketData.writeString(buffer, project.getName());
            buffer.writeBoolean(project.isActorIsOwner());
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        int count = buffer.readInt();
        if (count < 0 || count > 4096) throw new IllegalArgumentException("Invalid project count: " + count);
        List<ProjectSummary> decoded = new ArrayList<ProjectSummary>(count);
        for (int index = 0; index < count; index++) {
            decoded.add(
                new ProjectSummary(
                    PacketData.readUuid(buffer),
                    PacketData.readString(buffer, 256),
                    buffer.readBoolean()));
        }
        projects = decoded;
    }
}
