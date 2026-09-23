package com.grupomariposa.orders.application.error;

public final class TaxRateNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private static final String MESSAGE = "Tax rate %s does not exist";

    public TaxRateNotFoundException(final String id) {
        super(MESSAGE.formatted(id));
    }
}
