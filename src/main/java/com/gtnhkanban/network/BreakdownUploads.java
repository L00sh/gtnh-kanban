package com.gtnhkanban.network;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.gtnhkanban.network.message.C2SUploadBreakdown;

/** Server-thread reassembly of multi-part breakdown uploads, at most one in flight per player. */
final class BreakdownUploads {

    private static final Map<UUID, Upload> PENDING = new HashMap<UUID, Upload>();

    private static final class Upload {

        final int id;
        final int partCount;
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        int nextPart;

        Upload(int id, int partCount) {
            this.id = id;
            this.partCount = partCount;
        }
    }

    private BreakdownUploads() {}

    /**
     * Records one part. Parts must arrive in order; a part 0 starts a new upload and discards any unfinished one.
     *
     * @return the complete encoded tree once the last part arrives, otherwise null
     * @throws IllegalArgumentException when parts arrive out of order
     */
    static byte[] accept(UUID playerId, C2SUploadBreakdown part) {
        Upload upload = PENDING.get(playerId);
        if (part.getPart() == 0) {
            upload = new Upload(part.getUploadId(), part.getPartCount());
            PENDING.put(playerId, upload);
        }
        if (upload == null || upload.id != part.getUploadId()
            || upload.partCount != part.getPartCount()
            || upload.nextPart != part.getPart()) {
            PENDING.remove(playerId);
            throw new IllegalArgumentException("Breakdown upload parts arrived out of order.");
        }
        upload.data.write(part.getData(), 0, part.getData().length);
        upload.nextPart++;
        if (upload.nextPart < upload.partCount) return null;
        PENDING.remove(playerId);
        return upload.data.toByteArray();
    }

    static void clear() {
        PENDING.clear();
    }
}
