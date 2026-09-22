package com.grupomariposa.orders.infrastructure.crypto;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.pii")
public record PiiProperties(@NotBlank String keyId, String previousKeyId) {
}
