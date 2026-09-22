package com.grupomariposa.orders.infrastructure.persistence;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.persistence.pool")
public record MongoPoolProperties(
        @Min(1) int maxSize,
        @Min(0) int minSize,
        @NotNull Duration maxWait,
        @NotNull Duration maxIdle) {
}
