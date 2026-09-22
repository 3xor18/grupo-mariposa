package com.grupomariposa.orders.infrastructure.persistence.document;

public record FailureDocument(String category, String cause, int attempts) {
}
