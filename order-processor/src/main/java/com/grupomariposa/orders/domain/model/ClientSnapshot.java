package com.grupomariposa.orders.domain.model;

import java.util.Objects;

public record ClientSnapshot(
        String clientId,
        String name,
        ClientStatus status,
        ClientSegment segment,
        TaxRegime taxRegime,
        MarketCode market) {

    public ClientSnapshot {
        Objects.requireNonNull(clientId, "clientId");
    }

    public static ClientSnapshot unresolved(final String clientId) {
        return new ClientSnapshot(clientId, null, null, null, null, null);
    }

    public static ClientSnapshot of(final String clientId, final Lookup<ClientProfile> lookup) {
        return lookup.value().map(ClientSnapshot::of).orElseGet(() -> unresolved(clientId));
    }

    public static ClientSnapshot of(final ClientProfile profile) {
        return new ClientSnapshot(profile.clientId(), profile.name(), profile.status(),
                profile.segment(), profile.taxRegime(), profile.market());
    }
}
