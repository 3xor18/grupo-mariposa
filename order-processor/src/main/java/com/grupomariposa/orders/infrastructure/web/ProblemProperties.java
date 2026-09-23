package com.grupomariposa.orders.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.api.problems")
public record ProblemProperties(@NotBlank String typeBase) {
}
