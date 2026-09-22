package com.grupomariposa.orders.domain.model;

public enum OrderStatus {
    APPROVED,
    REJECTED,
    TECHNICAL_FAILURE;

    public boolean isTerminal() {
        return this != TECHNICAL_FAILURE;
    }
}
