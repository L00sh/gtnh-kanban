package com.gtnhkanban.network.message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.gtnhkanban.model.BoardColumn;
import com.gtnhkanban.model.CardType;

import io.netty.buffer.ByteBuf;

/** Replaces the server-wide columns (in order) and card types. */
public final class C2SSaveSettings extends KanbanRequest {

    private List<BoardColumn> columns = Collections.emptyList();
    private List<CardType> types = Collections.emptyList();

    public C2SSaveSettings() {}

    public C2SSaveSettings(List<BoardColumn> columns, List<CardType> types) {
        super("", "", null, null, null, null, 0, false, null, null);
        this.columns = new ArrayList<BoardColumn>(columns);
        this.types = new ArrayList<CardType>(types);
    }

    @Override
    public RequestType getType() {
        return RequestType.SAVE_SETTINGS;
    }

    public List<BoardColumn> getColumns() {
        return columns;
    }

    public List<CardType> getTypes() {
        return types;
    }

    @Override
    protected void writeExtra(ByteBuf buffer) {
        buffer.writeInt(columns.size());
        for (BoardColumn column : columns) {
            PacketData.writeUuid(buffer, column.getId());
            PacketData.writeString(buffer, column.getName());
        }
        buffer.writeInt(types.size());
        for (CardType type : types) {
            PacketData.writeUuid(buffer, type.getId());
            PacketData.writeString(buffer, type.getName());
            buffer.writeInt(type.getColor());
        }
    }

    @Override
    protected void readExtra(ByteBuf buffer) {
        int columnCount = PacketData.readCount(buffer, 64);
        List<BoardColumn> readColumns = new ArrayList<BoardColumn>();
        for (int i = 0; i < columnCount; i++)
            readColumns.add(new BoardColumn(requireId(buffer), PacketData.readString(buffer, 256)));
        int typeCount = PacketData.readCount(buffer, 128);
        List<CardType> readTypes = new ArrayList<CardType>();
        for (int i = 0; i < typeCount; i++)
            readTypes.add(new CardType(requireId(buffer), PacketData.readString(buffer, 256), buffer.readInt()));
        columns = readColumns;
        types = readTypes;
    }

    private static UUID requireId(ByteBuf buffer) {
        UUID id = PacketData.readUuid(buffer);
        if (id == null) throw new IllegalArgumentException("Missing settings id.");
        return id;
    }
}
