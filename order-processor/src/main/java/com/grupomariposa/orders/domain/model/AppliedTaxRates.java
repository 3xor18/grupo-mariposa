package com.grupomariposa.orders.domain.model;

import java.time.Instant;
import java.util.Objects;

public record AppliedTaxRates(TaxRateTable table, Instant effectiveFrom) {

    public AppliedTaxRates {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
    }
}
