package com.grupomariposa.orders.domain.model;

import java.util.List;
import java.util.Objects;

public record EvaluationInput(Market market, Lookup<ClientProfile> client,
                              List<ResolvedItem> items) {

    public EvaluationInput {
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(client, "client");
        items = List.copyOf(items);
    }
}
