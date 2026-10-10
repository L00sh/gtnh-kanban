package com.gtnhkanban.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class KanbanProject {

    private final UUID id;
    private String name;
    private final UUID ownerId;
    private final Set<UUID> memberIds;
    private final List<KanbanCard> cards;
    private int nextCardNumber = 1;
    private ItemKey icon;

    public KanbanProject(UUID id, String name, UUID ownerId) {
        this(id, name, ownerId, Collections.<UUID>emptySet(), Collections.<KanbanCard>emptyList());
    }

    public KanbanProject(UUID id, String name, UUID ownerId, Set<UUID> memberIds, List<KanbanCard> cards) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.memberIds = new LinkedHashSet<UUID>(Objects.requireNonNull(memberIds, "memberIds"));
        this.memberIds.remove(ownerId);
        this.cards = new ArrayList<KanbanCard>(Objects.requireNonNull(cards, "cards"));
    }

    /**
     * Moves cards whose column no longer exists to the first column and clears types that no longer exist. Cards are
     * never removed.
     *
     * @return whether anything changed
     */
    public boolean fitToSettings(BoardSettings settings) {
        boolean changed = false;
        for (KanbanCard card : cards) {
            if (!settings.hasColumn(card.getColumnId())) {
                card.setColumnId(settings.firstColumn());
                changed = true;
            }
            if (card.getTypeId() != null && !settings.hasType(card.getTypeId())) {
                card.setTypeId(null);
                changed = true;
            }
        }
        return changed;
    }

    /** Hands out the next per-project card number. */
    public int takeCardNumber() {
        return nextCardNumber++;
    }

    public int getNextCardNumber() {
        return nextCardNumber;
    }

    public void setNextCardNumber(int nextCardNumber) {
        this.nextCardNumber = Math.max(1, nextCardNumber);
    }

    /** Null when the project has no icon. */
    public ItemKey getIcon() {
        return icon;
    }

    public void setIcon(ItemKey icon) {
        this.icon = icon;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public Set<UUID> getMemberIds() {
        return Collections.unmodifiableSet(memberIds);
    }

    public boolean addMember(UUID memberId) {
        return !ownerId.equals(memberId) && memberIds.add(Objects.requireNonNull(memberId, "memberId"));
    }

    public boolean removeMember(UUID memberId) {
        boolean removed = memberIds.remove(memberId);
        if (removed) for (KanbanCard card : cards) card.setAssigned(memberId, false);
        return removed;
    }

    public boolean isOwnerOrMember(UUID playerId) {
        return ownerId.equals(playerId) || memberIds.contains(playerId);
    }

    public List<KanbanCard> getCards() {
        return Collections.unmodifiableList(cards);
    }

    public void addCard(KanbanCard card) {
        cards.add(Objects.requireNonNull(card, "card"));
    }

    public boolean removeCard(UUID cardId) {
        KanbanCard card = findCard(cardId);
        if (card == null || !cards.remove(card)) return false;
        // Nothing may keep pointing at a card that is gone.
        for (KanbanCard other : cards) for (CardLink link : CardLink.values()) other.setLinked(link, cardId, false);
        return true;
    }

    /**
     * Moves a card into {@code columnId}, just before {@code beforeCardId} (which must be another card already in that
     * column) or, when that is null, to the end of the column.
     *
     * @return false when the card or the target position does not exist
     */
    public boolean moveCard(UUID cardId, UUID columnId, UUID beforeCardId) {
        KanbanCard card = findCard(cardId);
        if (card == null || columnId == null) return false;
        KanbanCard before = beforeCardId == null ? null : findCard(beforeCardId);
        if (beforeCardId != null && (before == null || before == card || !columnId.equals(before.getColumnId())))
            return false;
        cards.remove(card);
        card.setColumnId(columnId);
        cards.add(CardOrder.insertionIndex(cards, new CardOrder.Columns<KanbanCard>() {

            @Override
            public UUID columnOf(KanbanCard value) {
                return value.getColumnId();
            }
        }, columnId, before), card);
        return true;
    }

    public KanbanCard findCard(UUID cardId) {
        for (KanbanCard card : cards) {
            if (card.getId()
                .equals(cardId)) {
                return card;
            }
        }
        return null;
    }
}
