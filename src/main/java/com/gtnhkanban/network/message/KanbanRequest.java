package com.gtnhkanban.network.message;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.gtnhkanban.model.CardStatus;
import com.gtnhkanban.model.ItemKey;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/** Shared wire shape for client requests. The actor identity is deliberately absent. */
public abstract class KanbanRequest implements IMessage {

    private static final int MAX_STRING_BYTES = 2048;

    private String firstText = "";
    private String secondText = "";
    private UUID projectId;
    private UUID cardId;
    private UUID entryId;
    private UUID memberId;
    private int quantity;
    private boolean complete;
    private CardStatus status;
    private ItemKey item;

    protected KanbanRequest() {}

    protected KanbanRequest(String firstText, String secondText, UUID projectId, UUID cardId, UUID entryId,
        UUID memberId, int quantity, boolean complete, CardStatus status, ItemKey item) {
        this.firstText = firstText == null ? "" : firstText;
        this.secondText = secondText == null ? "" : secondText;
        this.projectId = projectId;
        this.cardId = cardId;
        this.entryId = entryId;
        this.memberId = memberId;
        this.quantity = quantity;
        this.complete = complete;
        this.status = status;
        this.item = item;
    }

    @Override
    public final void toBytes(ByteBuf buffer) {
        writeText(buffer, firstText);
        writeText(buffer, secondText);
        writeUuid(buffer, projectId);
        writeUuid(buffer, cardId);
        writeUuid(buffer, entryId);
        writeUuid(buffer, memberId);
        buffer.writeInt(quantity);
        buffer.writeBoolean(complete);
        buffer.writeInt(status == null ? -1 : status.ordinal());
        buffer.writeBoolean(item != null);
        if (item != null) {
            writeText(buffer, item.getRegistryName());
            buffer.writeInt(item.getMetadata());
        }
    }

    @Override
    public final void fromBytes(ByteBuf buffer) {
        firstText = readText(buffer, MAX_STRING_BYTES);
        secondText = readText(buffer, MAX_STRING_BYTES);
        projectId = readUuid(buffer);
        cardId = readUuid(buffer);
        entryId = readUuid(buffer);
        memberId = readUuid(buffer);
        quantity = buffer.readInt();
        complete = buffer.readBoolean();
        int statusOrdinal = buffer.readInt();
        status = statusOrdinal >= 0 && statusOrdinal < CardStatus.values().length ? CardStatus.values()[statusOrdinal]
            : null;
        item = buffer.readBoolean() ? new ItemKey(readText(buffer, 512), buffer.readInt()) : null;
    }

    public abstract RequestType getType();

    public String getFirstText() {
        return firstText;
    }

    public String getSecondText() {
        return secondText;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getCardId() {
        return cardId;
    }

    public UUID getEntryId() {
        return entryId;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public int getQuantity() {
        return quantity;
    }

    public boolean isComplete() {
        return complete;
    }

    public CardStatus getStatus() {
        return status;
    }

    public ItemKey getItem() {
        return item;
    }

    private static void writeText(ByteBuf buffer, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        buffer.writeInt(bytes.length);
        buffer.writeBytes(bytes);
    }

    private static String readText(ByteBuf buffer, int maximumBytes) {
        int length = buffer.readInt();
        if (length < 0 || length > maximumBytes || length > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid packet text length: " + length);
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeUuid(ByteBuf buffer, UUID value) {
        buffer.writeBoolean(value != null);
        if (value != null) {
            buffer.writeLong(value.getMostSignificantBits());
            buffer.writeLong(value.getLeastSignificantBits());
        }
    }

    private static UUID readUuid(ByteBuf buffer) {
        return buffer.readBoolean() ? new UUID(buffer.readLong(), buffer.readLong()) : null;
    }
}
