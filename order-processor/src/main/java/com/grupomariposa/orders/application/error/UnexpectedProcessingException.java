package com.grupomariposa.orders.application.error;

public final class UnexpectedProcessingException extends ProcessingException {

    private static final long serialVersionUID = 1L;

    public UnexpectedProcessingException(final String message, final Throwable cause) {
        super(ErrorCategory.UNEXPECTED, message, cause);
    }
}
