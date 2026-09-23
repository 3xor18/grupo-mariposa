package com.grupomariposa.orders.infrastructure.persistence;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.persistence")
public record PersistenceProperties(
        @Min(1) int transactionAttempts,
        @NotNull Duration transactionRetryBackoff,
        @NotNull Duration inboxRetention,
        @NotNull Duration outboxRetention) {
}
