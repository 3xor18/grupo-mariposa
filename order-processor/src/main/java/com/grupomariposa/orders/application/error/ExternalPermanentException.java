package com.grupomariposa.orders.application.error;

public final class ExternalPermanentException extends ProcessingException {

    private static final long serialVersionUID = 1L;

    private final String dependency;

    public ExternalPermanentException(final String dependency, final String message,
                                      final Throwable cause) {
        super(ErrorCategory.EXTERNAL_PERMANENT, message, cause);
        this.dependency = dependency;
    }

    public String dependency() {
        return dependency;
    }
}
