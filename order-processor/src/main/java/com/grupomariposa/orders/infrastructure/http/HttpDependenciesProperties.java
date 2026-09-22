package com.grupomariposa.orders.infrastructure.http;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.http")
public record HttpDependenciesProperties(
        @Valid @NotNull Endpoint clients,
        @Valid @NotNull Endpoint products,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @Valid @NotNull OAuth oauth,
        @Valid @NotNull Resilience resilience) {

    public record Endpoint(@NotNull URI baseUrl) {
    }

    public record OAuth(boolean enabled, @NotBlank String registrationId) {
    }

    public record Resilience(
            @Min(1) int maxAttempts,
            @NotNull Duration initialBackoff,
            @DecimalMin("1.0") double backoffMultiplier,
            @DecimalMin("0.0") @DecimalMax("1.0") double jitter,
            @NotNull Duration maxRetryAfter,
            @Min(1) int slidingWindowSize,
            @Min(1) int minimumNumberOfCalls,
            @DecimalMin("1.0") @DecimalMax("100.0") float failureRateThreshold,
            @NotNull Duration openStateDuration,
            @Min(1) int halfOpenCalls,
            @Min(1) int bulkheadMaxConcurrentCalls,
            @NotNull Duration bulkheadMaxWait) {
    }
}
