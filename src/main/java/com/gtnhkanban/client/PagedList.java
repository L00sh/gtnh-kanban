package com.gtnhkanban.client;

import java.util.Collections;
import java.util.List;

public final class PagedList {

    private PagedList() {}

    public static int pageCount(int itemCount, int pageSize) {
        requirePageSize(pageSize);
        if (itemCount <= 0) {
            return 1;
        }
        return (int) (((long) itemCount + pageSize - 1L) / pageSize);
    }

    public static <T> List<T> pageItems(List<T> items, int page, int pageSize) {
        requirePageSize(pageSize);
        if (items == null || page < 0 || page >= pageCount(items.size(), pageSize)) {
            return Collections.emptyList();
        }
        long startLong = (long) page * pageSize;
        if (startLong >= items.size()) {
            return Collections.emptyList();
        }
        int start = (int) startLong;
        int end = Math.min(items.size(), start + pageSize);
        return Collections.unmodifiableList(items.subList(start, end));
    }

    private static void requirePageSize(int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
    }
}
