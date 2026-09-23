package com.grupomariposa.orders.domain.model;

import java.util.Objects;

public record ClientProfile(
        String clientId,
        String name,
        ClientStatus status,
        ClientSegment segment,
        TaxRegime taxRegime,
        MarketCode market) {

    public ClientProfile {
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(taxRegime, "taxRegime");
        Objects.requireNonNull(market, "market");
    }

    public boolean isActive() {
        return status == ClientStatus.ACTIVE;
    }

    public boolean isTaxExempt() {
        return taxRegime == TaxRegime.EXEMPT;
    }
}
