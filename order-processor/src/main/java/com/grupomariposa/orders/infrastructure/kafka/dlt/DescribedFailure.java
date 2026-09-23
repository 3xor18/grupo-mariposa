package com.grupomariposa.orders.infrastructure.kafka.dlt;

import java.util.Objects;

public final class DescribedFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient FailureDescription description;

    public DescribedFailure(final Exception failure, final FailureDescription description) {
        super(description.cause(), failure);
        this.description = Objects.requireNonNull(description, "description");
    }

    public FailureDescription description() {
        return description;
    }
}
