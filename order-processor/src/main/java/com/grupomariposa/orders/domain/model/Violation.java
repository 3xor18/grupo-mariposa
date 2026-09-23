package com.grupomariposa.orders.domain.model;

import java.util.Objects;

public record Violation(RejectionCode code, String message, String productId) {

    public Violation {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }

    public static Violation of(final RejectionCode code) {
        return new Violation(code, code.message(), null);
    }

    public static Violation ofProduct(final RejectionCode code, final String productId) {
        return new Violation(code, code.message(), productId);
    }
}
