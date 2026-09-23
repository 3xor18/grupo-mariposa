package com.grupomariposa.orders.infrastructure.kafka;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.kafka.master-data")
public record MasterDataProperties(
        boolean enabled,
        @NotBlank String clientsChangedTopic,
        @NotBlank String productsChangedTopic,
        @Min(1) int topicPartitions,
        @NotBlank String consumerGroup,
        @Min(1) int concurrency,
        @Valid @NotNull Retry retry) {

    public record Retry(
            @NotNull @DurationMin(millis = 1) Duration initialInterval,
            @DecimalMin("1.0") double multiplier,
            @NotNull @DurationMin(millis = 1) Duration maxInterval,
            @Min(1) int maxAttempts) {
    }
}
