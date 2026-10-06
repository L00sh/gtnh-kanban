package com.gtnhkanban.network.message;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.gtnhkanban.api.RequirementView;
import com.gtnhkanban.model.ItemKey;

import io.netty.buffer.ByteBuf;

final class RequirementWireCodec {

    private RequirementWireCodec() {}

    static void write(ByteBuf buffer, RequirementView row) {
        PacketData.writeUuid(buffer, row.getId());
        PacketData.writeMaterial(buffer, row.getItem());
        buffer.writeInt(row.getQuantity());
        buffer.writeBoolean(row.isComplete());
        buffer.writeInt(row.getAmountPerBatch());
        buffer.writeBoolean(row.isReusable());
        PacketData.writeString(buffer, row.getRecipeName());
        buffer.writeInt(row.getRecipeOutput());
        buffer.writeLong(row.getRevision());
        buffer.writeInt(
            row.getChildren()
                .size());
        for (RequirementView child : row.getChildren()) write(buffer, child);
    }

    static RequirementView read(ByteBuf buffer, int depth, int[] remaining) {
        if (depth > 16 || --remaining[0] < 0) throw new IllegalArgumentException("Material tree exceeds limits.");
        UUID id = PacketData.readUuid(buffer);
        ItemKey material = PacketData.readMaterial(buffer);
        int quantity = buffer.readInt();
        boolean complete = buffer.readBoolean();
        int perBatch = buffer.readInt();
        boolean reusable = buffer.readBoolean();
        String name = PacketData.readString(buffer, 512);
        int output = buffer.readInt();
        long revision = buffer.readLong();
        int count = PacketData.readCount(buffer, Math.max(0, remaining[0]));
        List<RequirementView> children = new ArrayList<RequirementView>();
        for (int index = 0; index < count; index++) children.add(read(buffer, depth + 1, remaining));
        return new RequirementView(
            id,
            material,
            quantity,
            complete,
            perBatch,
            reusable,
            name,
            output,
            revision,
            children);
    }
}
