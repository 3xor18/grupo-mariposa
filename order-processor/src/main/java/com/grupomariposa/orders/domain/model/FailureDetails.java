package com.grupomariposa.orders.domain.model;

import java.util.Objects;

public record FailureDetails(String category, String cause, int attempts) {

    public FailureDetails {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(cause, "cause");
        if (attempts < 1) {
            throw new IllegalArgumentException("Attempts must be at least one");
        }
    }
}
