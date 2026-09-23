package com.grupomariposa.orders.infrastructure.web;

import java.util.List;

public final class InvalidRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private static final String MESSAGE = "Request parameters are invalid";

    private final transient List<FieldViolation> violations;

    public InvalidRequestException(final List<FieldViolation> violations) {
        super(MESSAGE);
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> violations() {
        return violations;
    }
}
