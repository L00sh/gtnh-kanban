package com.gtnhkanban.network.message;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.RecipeIngredient;
import com.gtnhkanban.model.RecipePlan;

import io.netty.buffer.ByteBuf;

public final class C2SExpandRequirement extends KanbanRequest {

    private long expectedRevision;
    private RecipePlan plan;

    public C2SExpandRequirement() {}

    public C2SExpandRequirement(UUID projectId, UUID cardId, UUID entryId, int expectedQuantity, long revision,
        RecipePlan plan) {
        super("", "", projectId, cardId, entryId, null, expectedQuantity, false, null, null);
        this.expectedRevision = revision;
        this.plan = plan;
    }

    @Override
    public RequestType getType() {
        return RequestType.EXPAND_REQUIREMENT;
    }

    @Override
    public RecipePlan getRecipePlan() {
        return plan;
    }

    @Override
    public long getExpectedRevision() {
        return expectedRevision;
    }

    public static boolean fitsPacket(UUID projectId, UUID cardId, UUID entryId, int quantity, long revision,
        RecipePlan plan) {
        ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
        try {
            new C2SExpandRequirement(projectId, cardId, entryId, quantity, revision, plan).toBytes(buffer);
            return buffer.readableBytes() < 30000;
        } finally {
            buffer.release();
        }
    }

    @Override
    protected void writeExtra(ByteBuf buffer) {
        buffer.writeLong(expectedRevision);
        buffer.writeBoolean(plan != null);
        if (plan == null) return;
        PacketData.writeString(buffer, plan.getName());
        buffer.writeInt(plan.getOutputAmount());
        buffer.writeInt(
            plan.getIngredients()
                .size());
        for (RecipeIngredient input : plan.getIngredients()) {
            PacketData.writeMaterial(buffer, input.getMaterial());
            buffer.writeInt(input.getAmount());
            buffer.writeBoolean(input.isReusable());
        }
    }

    @Override
    protected void readExtra(ByteBuf buffer) {
        expectedRevision = buffer.readLong();
        if (!buffer.readBoolean()) {
            plan = null;
            return;
        }
        String name = PacketData.readString(buffer, 512);
        int output = buffer.readInt();
        int count = PacketData.readCount(buffer, 256);
        List<RecipeIngredient> inputs = new ArrayList<RecipeIngredient>();
        for (int i = 0; i < count; i++) {
            ItemKey key = PacketData.readMaterial(buffer);
            int amount = buffer.readInt();
            boolean reusable = buffer.readBoolean();
            inputs.add(new RecipeIngredient(key, amount, reusable));
        }
        plan = new RecipePlan(name, output, inputs);
    }
}
