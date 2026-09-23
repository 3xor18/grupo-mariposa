package com.grupomariposa.orders.domain.model;

public enum RejectionCode {
    CLIENT_NOT_FOUND("Client does not exist"),
    CLIENT_NOT_ACTIVE("Client is not active"),
    CLIENT_MARKET_MISMATCH("Client belongs to a different market than the order"),
    PRODUCT_NOT_FOUND("Product does not exist in the order market"),
    PRODUCT_NOT_ACTIVE("Product is not active");

    private final String message;

    RejectionCode(final String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
