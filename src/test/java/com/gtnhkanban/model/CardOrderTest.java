package com.gtnhkanban.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.junit.Test;

public class CardOrderTest {

    private static final UUID TODO = BoardSettings.BACKLOG, DONE = BoardSettings.DONE;

    private final KanbanProject project = new KanbanProject(UUID.randomUUID(), "P", UUID.randomUUID());
    private final KanbanCard a = card("A", TODO), b = card("B", TODO), c = card("C", TODO), x = card("X", DONE),
        y = card("Y", DONE);

    public CardOrderTest() {
        for (KanbanCard card : new KanbanCard[] { a, x, b, y, c }) project.addCard(card);
    }

    @Test
    public void reordersWithinAColumn() {
        assertTrue(project.moveCard(c.getId(), TODO, a.getId()));

        assertEquals("CAB", titles(TODO));
        assertEquals("XY", titles(DONE));
    }

    @Test
    public void movesToTheBottomOfAColumnWithoutATarget() {
        assertTrue(project.moveCard(a.getId(), TODO, null));

        assertEquals("BCA", titles(TODO));
    }

    @Test
    public void movesIntoAnotherColumnAtAPosition() {
        assertTrue(project.moveCard(b.getId(), DONE, y.getId()));

        assertEquals("AC", titles(TODO));
        assertEquals("XBY", titles(DONE));
        assertEquals(DONE, b.getColumnId());
    }

    @Test
    public void movesIntoAnEmptyColumn() {
        UUID review = BoardSettings.REVIEW;
        assertTrue(project.moveCard(x.getId(), review, null));

        assertEquals("X", titles(review));
    }

    @Test
    public void refusesTargetsInOtherColumnsOrItself() {
        assertFalse(project.moveCard(a.getId(), TODO, x.getId()));
        assertFalse(project.moveCard(a.getId(), TODO, a.getId()));
        assertFalse(project.moveCard(a.getId(), TODO, UUID.randomUUID()));
        assertEquals("Refused moves change nothing", "ABC", titles(TODO));
    }

    private String titles(UUID column) {
        StringBuilder order = new StringBuilder();
        for (KanbanCard card : project.getCards()) if (card.getColumnId()
            .equals(column)) order.append(card.getTitle());
        return order.toString();
    }

    private static KanbanCard card(String title, UUID column) {
        return new KanbanCard(UUID.randomUUID(), title, "", column);
    }
}
