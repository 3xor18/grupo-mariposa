package com.grupomariposa.orders.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.processing")
public record ProcessingProperties(@Min(1) int maxConcurrentLookups, String instanceId) {
}
