package com.grupomariposa.orders.infrastructure.pricing;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.tax-rates")
public record TaxRateProperties(
        @NotNull Instant seedFrom,
        @NotNull @DurationMin(millis = 1) Duration refreshInterval,
        boolean allowPastValidFrom) {
}
