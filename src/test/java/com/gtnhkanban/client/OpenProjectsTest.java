package com.gtnhkanban.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

public class OpenProjectsTest {

    private final UUID[] projects = new UUID[6];

    @Before
    public void setUp() {
        OpenProjects.clear();
        for (int i = 0; i < projects.length; i++) projects[i] = UUID.randomUUID();
    }

    @Test
    public void atMostFiveProjectsOpenAndReopeningIsFine() {
        for (int i = 0; i < 5; i++) assertTrue(OpenProjects.open(projects[i]));

        assertFalse("A sixth project is refused", OpenProjects.open(projects[5]));
        assertTrue("An open project can be opened again", OpenProjects.open(projects[2]));
        assertEquals(
            5,
            OpenProjects.list()
                .size());
    }

    @Test
    public void closingMovesToTheNextTabOrThePreviousAtTheEnd() {
        for (int i = 0; i < 3; i++) OpenProjects.open(projects[i]);

        assertEquals(projects[2], OpenProjects.close(projects[1]));
        assertEquals(projects[0], OpenProjects.close(projects[2]));
        assertNull("Closing the last tab leaves none", OpenProjects.close(projects[0]));
    }

    @Test
    public void closingFreesASlotAndTabsKeepTheirOrder() {
        for (int i = 0; i < 5; i++) OpenProjects.open(projects[i]);
        OpenProjects.close(projects[0]);

        assertTrue(OpenProjects.open(projects[5]));
        assertEquals(
            projects[5],
            OpenProjects.list()
                .get(4));
    }

    @Test
    public void projectsNoLongerAccessibleAreClosed() {
        for (int i = 0; i < 3; i++) OpenProjects.open(projects[i]);

        OpenProjects.retain(Arrays.asList(projects[0], projects[2]));

        assertEquals(Arrays.asList(projects[0], projects[2]), OpenProjects.list());
    }
}
