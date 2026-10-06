package com.gtnhkanban.network.message;

import java.util.UUID;

import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.Priority;

import io.netty.buffer.ByteBuf;

/** A request carrying a card's column, type, priority and icon alongside the shared request fields. */
abstract class CardFieldsRequest extends KanbanRequest {

    private UUID columnId;
    private UUID typeId;
    private Priority priority = Priority.NONE;
    private ItemKey icon;

    CardFieldsRequest() {}

    CardFieldsRequest(String title, String description, UUID projectId, UUID cardId, UUID columnId, UUID typeId,
        Priority priority, ItemKey icon) {
        super(title, description, projectId, cardId, null, null, 0, false, null, null);
        this.columnId = columnId;
        this.typeId = typeId;
        this.priority = priority == null ? Priority.NONE : priority;
        this.icon = icon;
    }

    @Override
    public UUID getColumnId() {
        return columnId;
    }

    @Override
    public UUID getTypeId() {
        return typeId;
    }

    @Override
    public Priority getPriority() {
        return priority;
    }

    @Override
    public ItemKey getIcon() {
        return icon;
    }

    @Override
    protected void writeExtra(ByteBuf buffer) {
        PacketData.writeUuid(buffer, columnId);
        PacketData.writeUuid(buffer, typeId);
        buffer.writeInt(priority.ordinal());
        buffer.writeBoolean(icon != null);
        if (icon != null) PacketData.writeMaterial(buffer, icon);
    }

    @Override
    protected void readExtra(ByteBuf buffer) {
        columnId = PacketData.readUuid(buffer);
        typeId = PacketData.readUuid(buffer);
        priority = Priority.fromOrdinal(buffer.readInt());
        icon = buffer.readBoolean() ? PacketData.readMaterial(buffer) : null;
    }
}
