package com.gtnhkanban.network.message;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.gtnhkanban.model.ItemKey;

import io.netty.buffer.ByteBuf;

/**
 * One part of an encoded {@link RecipeTreeCodec breakdown}. Client-to-server packets are limited to 32 KB in 1.7.10,
 * so a large tree is sent as several parts that the server reassembles before applying anything.
 */
public final class C2SUploadBreakdown extends KanbanRequest {

    public static final int MAX_PART_BYTES = 24000;
    public static final int MAX_PARTS = 96;

    private long expectedRevision;
    private int uploadId;
    private int part;
    private int partCount;
    private byte[] data = new byte[0];

    public C2SUploadBreakdown() {}

    private C2SUploadBreakdown(UUID projectId, UUID cardId, UUID entryId, ItemKey item, int quantity,
        long expectedRevision, int uploadId, int part, int partCount, byte[] data) {
        super("", "", projectId, cardId, entryId, null, quantity, false, null, item);
        this.expectedRevision = expectedRevision;
        this.uploadId = uploadId;
        this.part = part;
        this.partCount = partCount;
        this.data = data;
    }

    /**
     * Splits an encoded tree into packets. With a null {@code entryId} the server adds {@code item} as a new row;
     * otherwise it replaces that row's branch, which must still have {@code quantity} and {@code expectedRevision}.
     */
    public static List<C2SUploadBreakdown> split(UUID projectId, UUID cardId, UUID entryId, ItemKey item, int quantity,
        long expectedRevision, int uploadId, byte[] encodedTree) {
        int partCount = Math.max(1, (encodedTree.length + MAX_PART_BYTES - 1) / MAX_PART_BYTES);
        if (partCount > MAX_PARTS) throw new IllegalArgumentException("This breakdown is too large to send.");
        List<C2SUploadBreakdown> parts = new ArrayList<C2SUploadBreakdown>();
        for (int part = 0; part < partCount; part++) {
            int start = part * MAX_PART_BYTES;
            int length = Math.min(MAX_PART_BYTES, encodedTree.length - start);
            byte[] slice = new byte[length];
            System.arraycopy(encodedTree, start, slice, 0, length);
            parts.add(
                new C2SUploadBreakdown(
                    projectId,
                    cardId,
                    entryId,
                    item,
                    quantity,
                    expectedRevision,
                    uploadId,
                    part,
                    partCount,
                    slice));
        }
        return parts;
    }

    @Override
    public RequestType getType() {
        return RequestType.UPLOAD_BREAKDOWN;
    }

    @Override
    public long getExpectedRevision() {
        return expectedRevision;
    }

    public int getUploadId() {
        return uploadId;
    }

    public int getPart() {
        return part;
    }

    public int getPartCount() {
        return partCount;
    }

    public byte[] getData() {
        return data;
    }

    @Override
    protected void writeExtra(ByteBuf buffer) {
        buffer.writeLong(expectedRevision);
        buffer.writeInt(uploadId);
        buffer.writeInt(part);
        buffer.writeInt(partCount);
        buffer.writeInt(data.length);
        buffer.writeBytes(data);
    }

    @Override
    protected void readExtra(ByteBuf buffer) {
        expectedRevision = buffer.readLong();
        uploadId = buffer.readInt();
        part = buffer.readInt();
        partCount = buffer.readInt();
        if (part < 0 || partCount < 1 || partCount > MAX_PARTS || part >= partCount)
            throw new IllegalArgumentException("Invalid breakdown part " + part + "/" + partCount);
        int length = PacketData.readCount(buffer, MAX_PART_BYTES);
        if (length > buffer.readableBytes()) throw new IllegalArgumentException("Truncated breakdown part.");
        data = new byte[length];
        buffer.readBytes(data);
    }
}
