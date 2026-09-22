package com.grupomariposa.orders.infrastructure.persistence.document;

public record ClientDocument(
        String clientId,
        String encryptedName,
        String status,
        String segment,
        String taxRegime,
        String market) {
}
