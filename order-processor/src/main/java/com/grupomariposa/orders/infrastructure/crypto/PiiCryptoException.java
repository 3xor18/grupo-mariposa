package com.grupomariposa.orders.infrastructure.crypto;

public final class PiiCryptoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PiiCryptoException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
