package com.grupomariposa.orders.application.error;

import java.time.Duration;
import java.util.Optional;

public final class ExternalTransientException extends ProcessingException {

    private static final long serialVersionUID = 1L;

    private final String dependency;
    private final transient Duration retryAfter;

    public ExternalTransientException(final String dependency, final String message,
                                      final Duration retryAfter, final Throwable cause) {
        super(ErrorCategory.EXTERNAL_TRANSIENT, message, cause);
        this.dependency = dependency;
        this.retryAfter = retryAfter;
    }

    public ExternalTransientException(final String dependency, final String message,
                                      final Throwable cause) {
        this(dependency, message, null, cause);
    }

    public String dependency() {
        return dependency;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
