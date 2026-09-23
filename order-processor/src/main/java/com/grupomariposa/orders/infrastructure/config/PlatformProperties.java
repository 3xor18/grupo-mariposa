package com.grupomariposa.orders.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "platform")
public record PlatformProperties(@NotBlank String markets, @NotBlank String currencies) {
}
