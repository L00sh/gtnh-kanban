package com.gtnhkanban.network.message;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.gtnhkanban.api.ProjectSummary;
import com.gtnhkanban.model.BoardColumn;
import com.gtnhkanban.model.BoardSettings;
import com.gtnhkanban.model.CardStatus;
import com.gtnhkanban.model.CardType;
import com.gtnhkanban.model.ItemKey;

import io.netty.buffer.ByteBuf;

final class PacketData {

    private PacketData() {}

    static void writeString(ByteBuf buffer, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        buffer.writeInt(bytes.length);
        buffer.writeBytes(bytes);
    }

    static String readString(ByteBuf buffer, int maximumBytes) {
        int length = buffer.readInt();
        if (length < 0 || length > maximumBytes || length > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid string length: " + length);
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static void writeUuid(ByteBuf buffer, UUID value) {
        buffer.writeBoolean(value != null);
        if (value != null) {
            buffer.writeLong(value.getMostSignificantBits());
            buffer.writeLong(value.getLeastSignificantBits());
        }
    }

    static UUID readUuid(ByteBuf buffer) {
        return buffer.readBoolean() ? new UUID(buffer.readLong(), buffer.readLong()) : null;
    }

    static void writeMaterial(ByteBuf buffer, ItemKey material) {
        writeString(buffer, material.getRegistryName());
        buffer.writeInt(material.getMetadata());
        buffer.writeBoolean(material.isFluid());
        writeString(buffer, material.getNbt());
    }

    static ItemKey readMaterial(ByteBuf buffer) {
        return new ItemKey(readString(buffer, 1024), buffer.readInt(), buffer.readBoolean(), readString(buffer, 16384));
    }

    static void writeOptionalMaterial(ByteBuf buffer, ItemKey material) {
        buffer.writeBoolean(material != null);
        if (material != null) writeMaterial(buffer, material);
    }

    static ItemKey readOptionalMaterial(ByteBuf buffer) {
        return buffer.readBoolean() ? readMaterial(buffer) : null;
    }

    static void writeProject(ByteBuf buffer, ProjectSummary project) {
        writeUuid(buffer, project.getId());
        writeString(buffer, project.getName());
        buffer.writeBoolean(project.isActorIsOwner());
        writeOptionalMaterial(buffer, project.getIcon());
    }

    static ProjectSummary readProject(ByteBuf buffer) {
        return new ProjectSummary(
            readUuid(buffer),
            readString(buffer, 256),
            buffer.readBoolean(),
            readOptionalMaterial(buffer));
    }

    static void writeSettings(ByteBuf buffer, BoardSettings settings) {
        buffer.writeInt(
            settings.getColumns()
                .size());
        for (BoardColumn column : settings.getColumns()) {
            writeUuid(buffer, column.getId());
            writeString(buffer, column.getName());
        }
        buffer.writeInt(
            settings.getTypes()
                .size());
        for (CardType type : settings.getTypes()) {
            writeUuid(buffer, type.getId());
            writeString(buffer, type.getName());
            buffer.writeInt(type.getColor());
        }
    }

    static BoardSettings readSettings(ByteBuf buffer) {
        int columnCount = readCount(buffer, 64);
        List<BoardColumn> columns = new ArrayList<BoardColumn>();
        for (int i = 0; i < columnCount; i++) columns.add(new BoardColumn(readUuid(buffer), readString(buffer, 256)));
        int typeCount = readCount(buffer, 128);
        List<CardType> types = new ArrayList<CardType>();
        for (int i = 0; i < typeCount; i++)
            types.add(new CardType(readUuid(buffer), readString(buffer, 256), buffer.readInt()));
        return new BoardSettings(columns, types);
    }

    static int readCount(ByteBuf buffer, int maximum) {
        int count = buffer.readInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid collection count: " + count);
        return count;
    }

    static void writeStatus(ByteBuf buffer, CardStatus status) {
        buffer.writeInt(status == null ? -1 : status.ordinal());
    }

    static CardStatus readStatus(ByteBuf buffer) {
        int ordinal = buffer.readInt();
        return ordinal >= 0 && ordinal < CardStatus.values().length ? CardStatus.values()[ordinal] : CardStatus.TODO;
    }
}
