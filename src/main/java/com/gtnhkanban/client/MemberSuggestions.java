package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Which usernames to suggest while typing a new member's name. */
final class MemberSuggestions {

    private MemberSuggestions() {}

    /**
     * Names starting with {@code typed} first, then names merely containing it, ignoring case and skipping
     * {@code exclude} (current members). Empty {@code typed} matches everything.
     */
    static List<String> matching(List<String> names, String typed, Collection<String> exclude, int max) {
        String needle = typed.trim()
            .toLowerCase(Locale.ROOT);
        Set<String> skip = new HashSet<String>();
        for (String name : exclude) skip.add(name.toLowerCase(Locale.ROOT));
        List<String> starts = new ArrayList<String>(), contains = new ArrayList<String>();
        for (String name : names) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (skip.contains(lower)) continue;
            if (lower.startsWith(needle)) starts.add(name);
            else if (lower.contains(needle)) contains.add(name);
        }
        starts.addAll(contains);
        return starts.size() > max ? new ArrayList<String>(starts.subList(0, max)) : starts;
    }
}
