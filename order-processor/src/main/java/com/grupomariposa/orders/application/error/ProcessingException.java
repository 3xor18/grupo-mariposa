package com.grupomariposa.orders.application.error;

import java.util.Objects;

public abstract class ProcessingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCategory category;

    protected ProcessingException(final ErrorCategory category, final String message,
                                  final Throwable cause) {
        super(message, cause);
        this.category = Objects.requireNonNull(category, "category");
    }

    public ErrorCategory category() {
        return category;
    }
}
