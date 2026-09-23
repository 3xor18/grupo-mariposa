package com.grupomariposa.orders.domain.model;

import java.util.List;
import java.util.Objects;

public record EvaluationInput(MarketCode market, int fractionDigits,
                              Lookup<ClientProfile> client, List<ResolvedItem> items,
                              AppliedTaxRates taxRates) {

    private static final String NEGATIVE_DIGITS = "Fraction digits cannot be negative";

    public EvaluationInput {
        Objects.requireNonNull(market, "market");
        if (fractionDigits < 0) {
            throw new IllegalArgumentException(NEGATIVE_DIGITS);
        }
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(taxRates, "taxRates");
        items = List.copyOf(items);
    }
}
