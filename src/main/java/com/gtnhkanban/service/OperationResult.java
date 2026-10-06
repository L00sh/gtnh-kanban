package com.gtnhkanban.service;

public final class OperationResult<T> {

    private final T value;
    private final String errorCode;
    private final String message;

    private OperationResult(T value, String errorCode, String message) {
        this.value = value;
        this.errorCode = errorCode;
        this.message = message;
    }

    public static <T> OperationResult<T> success(T value) {
        return new OperationResult<T>(value, null, null);
    }

    public static <T> OperationResult<T> failure(String errorCode, String message) {
        return new OperationResult<T>(null, errorCode, message);
    }

    public boolean isSuccess() {
        return errorCode == null;
    }

    public T getValue() {
        return value;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }
}
