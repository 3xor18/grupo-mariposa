package com.grupomariposa.orders.infrastructure.cache;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.cache.products")
public record ProductCacheProperties(
        boolean enabled,
        @NotNull @DurationMin(millis = 1) Duration ttl,
        @NotBlank String keyPrefix) {
}
