package com.grupomariposa.orders.application.error;

public final class TaxRateConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TaxRateConflictException(final String message) {
        super(message);
    }
}
