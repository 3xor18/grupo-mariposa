package com.grupomariposa.orders.infrastructure.config;

import java.util.Objects;
import java.util.Optional;
import org.springframework.core.env.Environment;

public final class EnvironmentSecrets {

    public static final String PII_ENCRYPTION_KEY = "PII_ENCRYPTION_KEY";
    public static final String PII_PREVIOUS_ENCRYPTION_KEY = "PII_PREVIOUS_ENCRYPTION_KEY";
    public static final String OAUTH_CLIENT_SECRET = "OAUTH_CLIENT_SECRET";
    private static final String MISSING = "Secret %s must be provided through the environment";

    private final Environment environment;

    public EnvironmentSecrets(final Environment environment) {
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    public String required(final String name) {
        return optional(name).orElseThrow(() ->
                new IllegalStateException(MISSING.formatted(name)));
    }

    public Optional<String> optional(final String name) {
        return Optional.ofNullable(environment.getProperty(name))
                .filter(value -> !value.isBlank());
    }
}
