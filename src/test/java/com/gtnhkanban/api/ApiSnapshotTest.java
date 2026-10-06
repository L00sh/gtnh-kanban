package com.gtnhkanban.api;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.Test;

import com.gtnhkanban.model.CardStatus;
import com.gtnhkanban.model.ItemKey;

public class ApiSnapshotTest {

    @Test(expected = UnsupportedOperationException.class)
    public void boardSnapshotCopiesAndProtectsItsLists() {
        List<MemberSummary> members = new ArrayList<MemberSummary>();
        List<CardView> cards = new ArrayList<CardView>();
        BoardSnapshot snapshot = new BoardSnapshot(project(), members, cards);

        members.add(new MemberSummary(UUID.randomUUID(), "Member", false));
        cards.add(card());

        assertEquals(
            0,
            snapshot.getMembers()
                .size());
        assertEquals(
            0,
            snapshot.getCards()
                .size());
        snapshot.getMembers()
            .add(new MemberSummary(UUID.randomUUID(), "Another", false));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void cardViewCopiesAndProtectsRequirements() {
        List<RequirementView> requirements = new ArrayList<RequirementView>();
        CardView view = new CardView(UUID.randomUUID(), "Card", "", CardStatus.TODO, requirements);

        requirements.add(requirement());

        assertEquals(
            0,
            view.getRequirements()
                .size());
        view.getRequirements()
            .add(requirement());
    }

    private static ProjectSummary project() {
        return new ProjectSummary(UUID.randomUUID(), "Project", true);
    }

    private static CardView card() {
        return new CardView(UUID.randomUUID(), "Card", "", CardStatus.TODO, new ArrayList<RequirementView>());
    }

    private static RequirementView requirement() {
        return new RequirementView(UUID.randomUUID(), new ItemKey("minecraft:stone", 0), 1, false);
    }
}
