package com.gtnhkanban.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.CardView;
import com.gtnhkanban.api.MemberSummary;
import com.gtnhkanban.api.ProjectSummary;
import com.gtnhkanban.api.RequirementView;
import com.gtnhkanban.model.BoardSettings;

/**
 * The bug: a member of two projects, looking at a card in one, received the other project's board when someone
 * changed it. The single board slot was replaced, the card vanished, and the screen flashed and lost its scroll.
 */
public class KanbanClientStateTest {

    private final UUID projectA = UUID.randomUUID(), projectB = UUID.randomUUID();
    private final CardView cardA = card("Boiler"), cardB = card("Pump");

    @Before
    public void setUp() {
        KanbanClientState.clearBoards();
    }

    @Test
    public void anotherProjectsBoardDoesNotReplaceTheOneOnScreen() {
        KanbanClientState.setActiveProject(projectA);
        KanbanClientState.setBoard(board(projectA, cardA));

        KanbanClientState.setBoard(board(projectB, cardB));

        assertEquals(
            projectA,
            KanbanClientState.getBoard()
                .getProject()
                .getId());
        assertSame(cardA, KanbanClientState.findCard(cardA.getId()));
        assertNotNull("The other board is kept for when it is opened", KanbanClientState.getBoard(projectB));
    }

    @Test
    public void cardsAreFoundOnWhicheverBoardTheyAreOn() {
        KanbanClientState.setActiveProject(projectA);
        KanbanClientState.setBoard(board(projectA, cardA));
        KanbanClientState.setBoard(board(projectB, cardB));

        assertSame(cardB, KanbanClientState.findCard(cardB.getId()));
    }

    @Test
    public void aProjectScreenWaitsForItsOwnBoard() {
        KanbanClientState.setBoard(board(projectB, cardB));
        KanbanClientState.setActiveProject(projectA);

        assertNull(KanbanClientState.getBoard());
        assertNotNull("Server-wide settings come from any board", KanbanClientState.getSettings());
    }

    @Test
    public void boardsOfProjectsNoLongerAccessibleAreDropped() {
        KanbanClientState.setActiveProject(projectA);
        KanbanClientState.setBoard(board(projectA, cardA));
        KanbanClientState.setBoard(board(projectB, cardB));

        KanbanClientState.setProjects(Collections.singletonList(new ProjectSummary(projectA, "A", true)));

        assertNull(KanbanClientState.getBoard(projectB));
        assertNull(KanbanClientState.findCard(cardB.getId()));
        assertNotNull(KanbanClientState.getBoard());
    }

    @Test
    public void withNoProjectOnScreenTheNewestBoardIsShown() {
        KanbanClientState.setBoard(board(projectA, cardA));
        KanbanClientState.setBoard(board(projectB, cardB));

        assertEquals(
            projectB,
            KanbanClientState.getBoard()
                .getProject()
                .getId());
    }

    private static BoardSnapshot board(UUID projectId, CardView card) {
        return new BoardSnapshot(
            new ProjectSummary(projectId, "P", true),
            Collections.<MemberSummary>emptyList(),
            Arrays.asList(card),
            BoardSettings.defaults());
    }

    private static CardView card(String title) {
        return new CardView(
            UUID.randomUUID(),
            title,
            "",
            BoardSettings.BACKLOG,
            Collections.<RequirementView>emptyList());
    }
}
