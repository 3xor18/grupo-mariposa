package com.grupomariposa.orders.infrastructure.web.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security")
public record WebSecurityProperties(
        boolean enabled,
        @NotNull List<String> allowedOrigins,
        @NotBlank String readerRole,
        @NotBlank String adminRole) {
}
