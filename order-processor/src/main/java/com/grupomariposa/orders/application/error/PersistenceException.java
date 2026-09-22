package com.grupomariposa.orders.application.error;

public final class PersistenceException extends ProcessingException {

    private static final long serialVersionUID = 1L;

    public PersistenceException(final String message, final Throwable cause) {
        super(ErrorCategory.PERSISTENCE, message, cause);
    }
}
