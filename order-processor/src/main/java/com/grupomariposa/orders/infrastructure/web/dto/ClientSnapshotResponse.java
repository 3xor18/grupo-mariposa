package com.grupomariposa.orders.infrastructure.web.dto;

public record ClientSnapshotResponse(
        String clientId,
        String name,
        String status,
        String segment,
        String taxRegime,
        String market) {
}
