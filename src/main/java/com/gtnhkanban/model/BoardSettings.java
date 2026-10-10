package com.gtnhkanban.model;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Server-wide board columns (in display order) and card types. There is always at least one column. */
public final class BoardSettings {

    public static final UUID BACKLOG = fixedId("column:backlog");
    public static final UUID READY = fixedId("column:ready");
    public static final UUID IN_PROGRESS = fixedId("column:in_progress");
    public static final UUID REVIEW = fixedId("column:review");
    public static final UUID DONE = fixedId("column:done");

    private final List<BoardColumn> columns;
    private final List<CardType> types;

    public BoardSettings(List<BoardColumn> columns, List<CardType> types) {
        if (columns == null || columns.isEmpty()) throw new IllegalArgumentException("A board needs a column.");
        this.columns = Collections.unmodifiableList(new ArrayList<BoardColumn>(columns));
        this.types = Collections.unmodifiableList(new ArrayList<CardType>(Objects.requireNonNull(types, "types")));
    }

    public static BoardSettings defaults() {
        return new BoardSettings(
            Arrays.asList(
                new BoardColumn(BACKLOG, "Backlog"),
                new BoardColumn(READY, "Ready"),
                new BoardColumn(IN_PROGRESS, "In Progress"),
                new BoardColumn(REVIEW, "Review"),
                new BoardColumn(DONE, "Done")),
            Arrays.asList(
                new CardType(fixedId("type:bug"), "Bug", 0xE04848),
                new CardType(fixedId("type:feature"), "Feature", 0x4CAF50),
                new CardType(fixedId("type:power_failure"), "Power Failure", 0xFF9800),
                new CardType(fixedId("type:planning"), "Planning", 0x42A5F5),
                new CardType(fixedId("type:future"), "Future", 0xAB47BC),
                new CardType(fixedId("type:maintenance"), "Maintenance", 0x9E9E9E)));
    }

    /** Where cards saved before columns existed go: To do, In progress and Done map to the default columns. */
    public UUID legacyColumn(CardStatus status) {
        UUID wanted = status == CardStatus.DONE ? DONE : status == CardStatus.IN_PROGRESS ? IN_PROGRESS : BACKLOG;
        return hasColumn(wanted) ? wanted : firstColumn();
    }

    public List<BoardColumn> getColumns() {
        return columns;
    }

    public List<CardType> getTypes() {
        return types;
    }

    public UUID firstColumn() {
        return columns.get(0)
            .getId();
    }

    /** The column that means finished: the Done column, or the last column if it was removed. */
    public UUID doneColumn() {
        return hasColumn(DONE) ? DONE
            : columns.get(columns.size() - 1)
                .getId();
    }

    public boolean hasColumn(UUID id) {
        for (BoardColumn column : columns) if (column.getId()
            .equals(id)) return true;
        return false;
    }

    public boolean hasType(UUID id) {
        for (CardType type : types) if (type.getId()
            .equals(id)) return true;
        return false;
    }

    private static UUID fixedId(String name) {
        return UUID.nameUUIDFromBytes(("gtnhkanban:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
