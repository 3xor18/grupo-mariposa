package com.grupomariposa.orders.infrastructure.persistence.document;

public record ViolationDocument(String code, String message, String productId) {
}
