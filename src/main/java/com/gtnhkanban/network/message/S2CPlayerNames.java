package com.gtnhkanban.network.message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/** Usernames to suggest when adding members to a project. */
public final class S2CPlayerNames implements IMessage {

    private static final int MAX_NAMES = 2000;

    private UUID projectId;
    private List<String> names = Collections.emptyList();

    public S2CPlayerNames() {}

    public S2CPlayerNames(UUID projectId, List<String> names) {
        this.projectId = projectId;
        this.names = new ArrayList<String>(names.subList(0, Math.min(MAX_NAMES, names.size())));
    }

    public UUID getProjectId() {
        return projectId;
    }

    public List<String> getNames() {
        return Collections.unmodifiableList(names);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        PacketData.writeUuid(buffer, projectId);
        buffer.writeInt(names.size());
        for (String name : names) PacketData.writeString(buffer, name);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        projectId = PacketData.readUuid(buffer);
        int count = PacketData.readCount(buffer, MAX_NAMES);
        List<String> read = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) read.add(PacketData.readString(buffer, 64));
        names = read;
    }
}
