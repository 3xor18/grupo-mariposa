package com.grupomariposa.orders.infrastructure.kafka;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
        @Min(1) int concurrency) {
}
