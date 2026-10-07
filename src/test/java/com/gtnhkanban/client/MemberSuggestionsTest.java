package com.gtnhkanban.client;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class MemberSuggestionsTest {

    private static final List<String> WHITELIST = Arrays.asList("Steve", "alex", "Stella", "Notch", "BestSteve");

    @Test
    public void namesStartingWithTheTextComeBeforeNamesContainingIt() {
        assertEquals(
            Arrays.asList("Steve", "Stella", "BestSteve"),
            MemberSuggestions.matching(WHITELIST, "st", Collections.<String>emptyList(), 6));
    }

    @Test
    public void currentMembersAreLeftOutIgnoringCase() {
        assertEquals(
            Arrays.asList("Stella", "BestSteve"),
            MemberSuggestions.matching(WHITELIST, "ST", Arrays.asList("steve"), 6));
    }

    @Test
    public void emptyTextSuggestsEveryoneUpToTheLimit() {
        assertEquals(
            Arrays.asList("Steve", "alex"),
            MemberSuggestions.matching(WHITELIST, "", Collections.<String>emptyList(), 2));
    }
}
