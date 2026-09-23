package com.grupomariposa.orders.application.error;

import com.grupomariposa.orders.application.validation.ValidationError;
import java.util.List;

public final class InvalidTaxRateException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private static final String MESSAGE = "Tax rate proposal is invalid";

    private final transient List<ValidationError> errors;

    public InvalidTaxRateException(final List<ValidationError> errors) {
        super(MESSAGE);
        this.errors = List.copyOf(errors);
    }

    public List<ValidationError> errors() {
        return errors;
    }
}
