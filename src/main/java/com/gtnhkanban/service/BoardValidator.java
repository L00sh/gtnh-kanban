package com.gtnhkanban.service;

public final class BoardValidator {

    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_DESCRIPTION_LENGTH = 512;

    private BoardValidator() {}

    public static ValidationResult<String> validateProjectName(String name) {
        return validateRequiredLabel(name, "Project name");
    }

    public static ValidationResult<String> validateCardTitle(String title) {
        return validateRequiredLabel(title, "Card title");
    }

    public static ValidationResult<String> validateDescription(String description) {
        String normalizedDescription = description == null ? "" : description;
        if (normalizedDescription.length() > MAX_DESCRIPTION_LENGTH) {
            return ValidationResult.invalid("Description must be at most 512 characters.");
        }
        return ValidationResult.valid(normalizedDescription);
    }

    public static ValidationResult<Integer> validateQuantity(int quantity) {
        if (quantity < 1) {
            return ValidationResult.invalid("Quantity must be at least 1.");
        }
        return ValidationResult.valid(Integer.valueOf(quantity));
    }

    private static ValidationResult<String> validateRequiredLabel(String label, String labelName) {
        String normalizedLabel = label == null ? "" : label.trim();
        if (normalizedLabel.length() == 0) {
            return ValidationResult.invalid(labelName + " is required.");
        }
        if (normalizedLabel.length() > MAX_NAME_LENGTH) {
            return ValidationResult.invalid(labelName + " must be at most 64 characters.");
        }
        return ValidationResult.valid(normalizedLabel);
    }
}
