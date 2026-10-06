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
        return card != null && cards.remove(card);
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
