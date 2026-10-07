package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Projects open as tabs this session, in the order they were opened. At most {@link #MAX}. */
final class OpenProjects {

    static final int MAX = 5;
    private static final List<UUID> OPEN = new ArrayList<UUID>();

    private OpenProjects() {}

    /** @return false when {@link #MAX} other projects are already open; true when it is (now) open */
    static boolean open(UUID projectId) {
        if (projectId == null) return false;
        if (OPEN.contains(projectId)) return true;
        if (OPEN.size() >= MAX) return false;
        OPEN.add(projectId);
        return true;
    }

    static boolean isOpen(UUID projectId) {
        return OPEN.contains(projectId);
    }

    /**
     * Closes a tab.
     *
     * @return the tab to show instead (the one after it, else the one before), or null when none are left
     */
    static UUID close(UUID projectId) {
        int index = OPEN.indexOf(projectId);
        if (index < 0) return OPEN.isEmpty() ? null : OPEN.get(0);
        OPEN.remove(index);
        if (OPEN.isEmpty()) return null;
        return OPEN.get(Math.min(index, OPEN.size() - 1));
    }

    static List<UUID> list() {
        return Collections.unmodifiableList(new ArrayList<UUID>(OPEN));
    }

    /** Drops projects this player can no longer open. */
    static void retain(Collection<UUID> accessible) {
        OPEN.retainAll(accessible);
    }

    static void clear() {
        OPEN.clear();
    }
}
