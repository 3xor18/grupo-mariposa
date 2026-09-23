package com.grupomariposa.orders.infrastructure.persistence.document;

public enum OutboxStatus {
    PENDING,
    IN_FLIGHT,
    PUBLISHED
}
