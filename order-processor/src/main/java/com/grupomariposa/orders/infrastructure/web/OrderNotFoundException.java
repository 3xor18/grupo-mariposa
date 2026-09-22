package com.grupomariposa.orders.infrastructure.web;

public final class OrderNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private static final String MESSAGE = "Order %s was not found";

    public OrderNotFoundException(final String orderId) {
        super(MESSAGE.formatted(orderId));
    }
}
