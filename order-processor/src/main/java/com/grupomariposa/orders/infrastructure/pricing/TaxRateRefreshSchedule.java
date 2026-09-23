package com.grupomariposa.orders.infrastructure.pricing;

import java.time.Duration;
import java.util.Objects;

public record TaxRateRefreshSchedule(Duration interval) {

    public static final String BEAN_NAME = "taxRateRefreshSchedule";

    public TaxRateRefreshSchedule {
        Objects.requireNonNull(interval, "interval");
    }

    public long intervalMillis() {
        return interval.toMillis();
    }
}
