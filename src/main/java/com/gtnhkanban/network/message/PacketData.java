package com.gtnhkanban.network.message;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.gtnhkanban.model.CardStatus;

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

    static void writeStatus(ByteBuf buffer, CardStatus status) {
        buffer.writeInt(status == null ? -1 : status.ordinal());
    }

    static CardStatus readStatus(ByteBuf buffer) {
        int ordinal = buffer.readInt();
        return ordinal >= 0 && ordinal < CardStatus.values().length ? CardStatus.values()[ordinal] : CardStatus.TODO;
    }
}
