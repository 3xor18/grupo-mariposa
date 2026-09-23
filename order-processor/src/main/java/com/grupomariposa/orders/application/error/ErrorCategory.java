package com.grupomariposa.orders.application.error;

public enum ErrorCategory {
    DESERIALIZATION(false, false),
    VALIDATION(false, false),
    VERSION_CONFLICT(false, false),
    EXTERNAL_TRANSIENT(true, true),
    EXTERNAL_PERMANENT(false, true),
    PERSISTENCE(true, true),
    UNEXPECTED(false, true);

    private final boolean retryable;
    private final boolean recordsTechnicalFailure;

    ErrorCategory(final boolean retryable, final boolean recordsTechnicalFailure) {
        this.retryable = retryable;
        this.recordsTechnicalFailure = recordsTechnicalFailure;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public boolean recordsTechnicalFailure() {
        return recordsTechnicalFailure;
    }
}
