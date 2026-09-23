package com.grupomariposa.orders.infrastructure.kafka;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxRelayProperties(
        boolean enabled,
        @NotNull Duration fixedDelay,
        @Min(1) int batchSize,
        @NotNull Duration lease,
        @NotNull Duration sendTimeout,
        @NotNull Duration initialBackoff,
        @NotNull Duration maxBackoff) {
}
