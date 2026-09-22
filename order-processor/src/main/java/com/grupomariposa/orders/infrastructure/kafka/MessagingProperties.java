package com.grupomariposa.orders.infrastructure.kafka;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.kafka")
public record MessagingProperties(
        @Valid @NotNull Topics topics,
        @NotBlank String consumerGroup,
        @Min(1) int concurrency,
        boolean createTopics,
        @Min(1) short replicationFactor,
        @Valid @NotNull RecordRetry recordRetry,
        @NotNull Duration healthTimeout) {

    public record Topics(
            @NotBlank String ordersCreated,
            @Min(1) int ordersCreatedPartitions,
            @NotBlank String ordersProcessed,
            @Min(1) int ordersProcessedPartitions,
            @NotBlank String deadLetter,
            @Min(1) int deadLetterPartitions) {
    }

    public record RecordRetry(
            @NotNull Duration initialInterval,
            @DecimalMin("1.0") double multiplier,
            @Min(1) int maxAttempts) {
    }
}
