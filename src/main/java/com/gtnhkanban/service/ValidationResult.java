package com.gtnhkanban.service;

public final class ValidationResult<T> {

    private final T value;
    private final String errorMessage;

    private ValidationResult(T value, String errorMessage) {
        this.value = value;
        this.errorMessage = errorMessage;
    }

    public static <T> ValidationResult<T> valid(T value) {
        return new ValidationResult<T>(value, null);
    }

    public static <T> ValidationResult<T> invalid(String errorMessage) {
        return new ValidationResult<T>(null, errorMessage);
    }

    public boolean isValid() {
        return errorMessage == null;
    }

    public T getValue() {
        return value;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
