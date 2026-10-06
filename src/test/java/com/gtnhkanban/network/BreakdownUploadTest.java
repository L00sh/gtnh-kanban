package com.gtnhkanban.network;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.After;
import org.junit.Test;

import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.RecipeIngredient;
import com.gtnhkanban.model.RecipePlan;
import com.gtnhkanban.model.RecipeTree;
import com.gtnhkanban.network.message.C2SUploadBreakdown;
import com.gtnhkanban.network.message.RecipeTreeCodec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class BreakdownUploadTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final ItemKey MACHINE = new ItemKey("gregtech:machine", 3, false, "{tier:1}");
    private static final ItemKey PLATE = new ItemKey("gregtech:plate", 0);
    private static final ItemKey STEAM = new ItemKey("steam", 0, true, "");

    @After
    public void tearDown() {
        BreakdownUploads.clear();
    }

    @Test
    public void treeSurvivesTheWireFormat() {
        RecipeTree plate = new RecipeTree(
            new RecipePlan(
                "Bender",
                1,
                Arrays.asList(new RecipeIngredient(new ItemKey("gregtech:ingot", 0), 1, false))),
            Arrays.asList((RecipeTree) null));
        RecipeTree tree = new RecipeTree(
            new RecipePlan(
                "Assembler",
                2,
                Arrays.asList(new RecipeIngredient(PLATE, 4, false), new RecipeIngredient(STEAM, 1000, false))),
            Arrays.asList(plate, null));

        RecipeTree decoded = RecipeTreeCodec.decode(RecipeTreeCodec.encode(tree));

        assertEquals(
            "Assembler",
            decoded.getPlan()
                .getName());
        assertEquals(
            2,
            decoded.getPlan()
                .getOutputAmount());
        assertEquals(
            STEAM,
            decoded.getPlan()
                .getIngredients()
                .get(1)
                .getMaterial());
        assertNull(
            decoded.getChildren()
                .get(1));
        assertEquals(
            "Bender",
            decoded.getChildren()
                .get(0)
                .getPlan()
                .getName());
        assertEquals(tree.nodeCount(), decoded.nodeCount());
    }

    @Test
    public void malformedTreesAreRejected() {
        byte[] encoded = RecipeTreeCodec.encode(singleStep());
        byte[] truncated = Arrays.copyOf(encoded, encoded.length - 3);
        try {
            RecipeTreeCodec.decode(truncated);
            fail("truncated tree decoded");
        } catch (RuntimeException expected) {}
        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
        try {
            RecipeTreeCodec.decode(trailing);
            fail("trailing data accepted");
        } catch (IllegalArgumentException expected) {}
    }

    @Test
    public void largeUploadsAreSplitUnderThePacketLimitAndReassembled() {
        byte[] payload = new byte[C2SUploadBreakdown.MAX_PART_BYTES * 2 + 123];
        new Random(7).nextBytes(payload);

        List<C2SUploadBreakdown> parts = split(payload, 1);

        assertEquals(3, parts.size());
        byte[] assembled = null;
        for (C2SUploadBreakdown part : parts) {
            C2SUploadBreakdown received = overTheWire(part);
            assertEquals(1, received.getQuantity());
            assertEquals(MACHINE, received.getItem());
            assertEquals(42L, received.getExpectedRevision());
            assertNull("finished early", assembled);
            assembled = BreakdownUploads.accept(PLAYER, received);
        }
        assertArrayEquals(payload, assembled);
    }

    @Test
    public void outOfOrderOrInterruptedUploadsAreDiscarded() {
        List<C2SUploadBreakdown> parts = split(new byte[C2SUploadBreakdown.MAX_PART_BYTES * 2], 1);
        try {
            BreakdownUploads.accept(PLAYER, parts.get(1));
            fail("accepted a part without its start");
        } catch (IllegalArgumentException expected) {}

        assertNull(BreakdownUploads.accept(PLAYER, parts.get(0)));
        List<C2SUploadBreakdown> restart = split(new byte[10], 2);
        // A new upload replaces the unfinished one rather than mixing with it.
        assertArrayEquals(new byte[10], BreakdownUploads.accept(PLAYER, restart.get(0)));
        try {
            BreakdownUploads.accept(PLAYER, parts.get(1));
            fail("accepted a part from an abandoned upload");
        } catch (IllegalArgumentException expected) {}
    }

    @Test
    public void tooLargeUploadsAreRefusedBeforeSending() {
        try {
            split(new byte[C2SUploadBreakdown.MAX_PART_BYTES * C2SUploadBreakdown.MAX_PARTS + 1], 1);
            fail("oversized upload split");
        } catch (IllegalArgumentException expected) {}
    }

    private static List<C2SUploadBreakdown> split(byte[] payload, int uploadId) {
        return C2SUploadBreakdown.split(UUID.randomUUID(), UUID.randomUUID(), null, MACHINE, 1, 42L, uploadId, payload);
    }

    private static C2SUploadBreakdown overTheWire(C2SUploadBreakdown part) {
        ByteBuf buffer = Unpooled.buffer();
        part.toBytes(buffer);
        assertTrue("part exceeds the request limit", buffer.readableBytes() < 30000);
        C2SUploadBreakdown received = new C2SUploadBreakdown();
        received.fromBytes(buffer);
        buffer.release();
        return received;
    }

    private static RecipeTree singleStep() {
        List<RecipeTree> children = new ArrayList<RecipeTree>();
        children.add(null);
        return new RecipeTree(
            new RecipePlan("Step", 1, Arrays.asList(new RecipeIngredient(PLATE, 1, false))),
            children);
    }
}
