package com.gtnhkanban.service;

import java.nio.charset.StandardCharsets;

import com.gtnhkanban.model.ItemRequirement;
import com.gtnhkanban.model.KanbanCard;
import com.gtnhkanban.model.KanbanProject;

/** Conservative snapshot budget below Forge's custom payload limit. */
final class BoardSizeBudget {

    private static final long MAX_BYTES = 1900000;

    private BoardSizeBudget() {}

    static long text(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    static long requirement(ItemRequirement row) {
        long bytes = 60 + text(
            row.getItem()
                .getRegistryName())
            + text(
                row.getItem()
                    .getNbt())
            + text(row.getRecipeName());
        for (ItemRequirement child : row.getChildren()) bytes += requirement(child);
        return bytes;
    }

    static long card(KanbanCard card) {
        long bytes = 37 + text(card.getTitle())
            + text(card.getDescription())
            + 17L * card.getAssigneeIds()
                .size();
        for (ItemRequirement row : card.getRequirements()) bytes += requirement(row);
        return bytes;
    }

    static boolean fits(KanbanProject project, long additionalBytes) {
        // Snapshot usernames have a 128-byte limit; budget its maximum for every member.
        long bytes = 30 + text(project.getName())
            + 150L * (project.getMemberIds()
                .size() + 1);
        for (KanbanCard card : project.getCards()) bytes += card(card);
        return bytes + additionalBytes < MAX_BYTES;
    }
}
