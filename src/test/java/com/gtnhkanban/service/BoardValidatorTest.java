package com.gtnhkanban.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BoardValidatorTest {

    @Test
    public void acceptsTrimmedProjectAndCardNamesWithinBounds() {
        assertEquals(
            "Project",
            BoardValidator.validateProjectName("  Project  ")
                .getValue());
        assertEquals(
            "Card",
            BoardValidator.validateCardTitle("  Card  ")
                .getValue());
        assertTrue(
            BoardValidator.validateProjectName(repeat('p', 64))
                .isValid());
        assertTrue(
            BoardValidator.validateCardTitle(repeat('c', 255))
                .isValid());
    }

    @Test
    public void rejectsBlankAndOverlongProjectAndCardNames() {
        assertFalse(
            BoardValidator.validateProjectName("   ")
                .isValid());
        assertFalse(
            BoardValidator.validateCardTitle("\t\n")
                .isValid());
        assertFalse(
            BoardValidator.validateProjectName(repeat('p', 65))
                .isValid());
        assertFalse(
            BoardValidator.validateCardTitle(repeat('c', 256))
                .isValid());
    }

    @Test
    public void acceptsEmptyAndBoundedDescriptions() {
        assertEquals(
            "",
            BoardValidator.validateDescription(null)
                .getValue());
        assertEquals(
            "",
            BoardValidator.validateDescription("")
                .getValue());
        assertTrue(
            BoardValidator.validateDescription(repeat('d', 512))
                .isValid());
    }

    @Test
    public void rejectsOverlongDescriptions() {
        assertFalse(
            BoardValidator.validateDescription(repeat('d', 513))
                .isValid());
    }

    @Test
    public void acceptsOnlyPositiveQuantities() {
        assertTrue(
            BoardValidator.validateQuantity(1)
                .isValid());
        assertFalse(
            BoardValidator.validateQuantity(0)
                .isValid());
        assertFalse(
            BoardValidator.validateQuantity(-1)
                .isValid());
    }

    private static String repeat(char character, int count) {
        StringBuilder value = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            value.append(character);
        }
        return value.toString();
    }
}
