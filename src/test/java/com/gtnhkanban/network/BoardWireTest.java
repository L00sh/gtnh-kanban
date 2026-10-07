package com.gtnhkanban.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.junit.Test;

import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.CardView;
import com.gtnhkanban.api.CommentView;
import com.gtnhkanban.api.MemberSummary;
import com.gtnhkanban.api.ProjectSummary;
import com.gtnhkanban.api.RequirementView;
import com.gtnhkanban.api.TaskView;
import com.gtnhkanban.model.BoardColumn;
import com.gtnhkanban.model.BoardSettings;
import com.gtnhkanban.model.CardType;
import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.Priority;
import com.gtnhkanban.network.message.C2SSaveSettings;
import com.gtnhkanban.network.message.C2SUpdateCard;
import com.gtnhkanban.network.message.S2CBoardSnapshot;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class BoardWireTest {

    private static final ItemKey ICON = new ItemKey("minecraft:furnace", 0);

    @Test
    public void boardSnapshotCarriesCardsSettingsAndIcons() {
        UUID creator = UUID.randomUUID();
        CardType type = new CardType(UUID.randomUUID(), "Bug", 0xE04848);
        BoardSettings settings = new BoardSettings(
            Arrays.asList(new BoardColumn(UUID.randomUUID(), "One"), new BoardColumn(UUID.randomUUID(), "Two")),
            Collections.singletonList(type));
        UUID column = settings.getColumns()
            .get(1)
            .getId();
        CardView card = new CardView(
            UUID.randomUUID(),
            7,
            "Leak",
            "Fix",
            column,
            type.getId(),
            Priority.HIGH,
            creator,
            "Owner",
            123L,
            ICON,
            Collections.<RequirementView>emptyList(),
            Collections.singleton(creator),
            Arrays.asList(new TaskView(UUID.randomUUID(), "Seal", true)),
            Arrays.asList(new CommentView(UUID.randomUUID(), creator, "Owner", 456L, "On it")));
        BoardSnapshot snapshot = new BoardSnapshot(
            new ProjectSummary(UUID.randomUUID(), "Steam", true, ICON),
            Collections.singletonList(new MemberSummary(creator, "Owner", true)),
            Collections.singletonList(card),
            settings);

        S2CBoardSnapshot received = new S2CBoardSnapshot();
        received.fromBytes(bytes(new S2CBoardSnapshot(snapshot)));
        BoardSnapshot decoded = received.getSnapshot();

        assertEquals(
            ICON,
            decoded.getProject()
                .getIcon());
        assertEquals(
            "Two",
            decoded.getSettings()
                .getColumns()
                .get(1)
                .getName());
        assertEquals(
            0xE04848,
            decoded.getSettings()
                .getTypes()
                .get(0)
                .getColor());
        CardView read = decoded.getCards()
            .get(0);
        assertEquals(7, read.getNumber());
        assertEquals(column, read.getColumnId());
        assertEquals(type.getId(), read.getTypeId());
        assertEquals(Priority.HIGH, read.getPriority());
        assertEquals("Owner", read.getCreatorName());
        assertEquals(123L, read.getCreatedAt());
        assertEquals(ICON, read.getIcon());
        assertEquals(1, read.progressDone());
        assertEquals(
            "On it",
            read.getComments()
                .get(0)
                .getText());
        assertEquals(
            456L,
            read.getComments()
                .get(0)
                .getCreatedAt());
    }

    @Test
    public void cardEditsAndSettingsSurviveTheWire() {
        UUID column = UUID.randomUUID();
        C2SUpdateCard update = new C2SUpdateCard(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Title",
            "Body",
            column,
            null,
            Priority.LOW,
            ICON);
        C2SUpdateCard updated = new C2SUpdateCard();
        updated.fromBytes(bytes(update));

        assertEquals(column, updated.getColumnId());
        assertNull(updated.getTypeId());
        assertEquals(Priority.LOW, updated.getPriority());
        assertEquals(ICON, updated.getIcon());

        C2SSaveSettings settings = new C2SSaveSettings(
            Arrays.asList(new BoardColumn(UUID.randomUUID(), "A")),
            Arrays.asList(new CardType(UUID.randomUUID(), "T", 0x00FF00)));
        C2SSaveSettings received = new C2SSaveSettings();
        received.fromBytes(bytes(settings));

        assertEquals(
            "A",
            received.getColumns()
                .get(0)
                .getName());
        assertEquals(
            0x00FF00,
            received.getTypes()
                .get(0)
                .getColor());
    }

    @Test
    public void playerNamesSurviveTheWire() {
        UUID project = UUID.randomUUID();
        com.gtnhkanban.network.message.S2CPlayerNames received = new com.gtnhkanban.network.message.S2CPlayerNames();
        received.fromBytes(
            bytes(new com.gtnhkanban.network.message.S2CPlayerNames(project, Arrays.asList("Steve", "alex"))));

        assertEquals(project, received.getProjectId());
        assertEquals(Arrays.asList("Steve", "alex"), received.getNames());
    }

    private static ByteBuf bytes(cpw.mods.fml.common.network.simpleimpl.IMessage message) {
        ByteBuf buffer = Unpooled.buffer();
        message.toBytes(buffer);
        return buffer;
    }
}
