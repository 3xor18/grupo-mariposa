package com.grupomariposa.orders.infrastructure.config;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public final class ConfigServerActivation implements EnvironmentPostProcessor, Ordered {

    public static final String CONFIG_SERVER_URL = "CONFIG_SERVER_URL";
    public static final String ENABLED_PROPERTY = "spring.cloud.config.enabled";
    private static final String SOURCE_NAME = "configServerActivation";
    private static final int BEFORE_CONFIG_DATA = Ordered.HIGHEST_PRECEDENCE + 5;

    @Override
    public void postProcessEnvironment(final ConfigurableEnvironment environment,
                                       final SpringApplication application) {
        final String url = environment.getProperty(CONFIG_SERVER_URL);
        if (url == null || url.isBlank()) {
            environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME,
                    Map.of(ENABLED_PROPERTY, Boolean.FALSE.toString())));
        }
    }

    @Override
    public int getOrder() {
        return BEFORE_CONFIG_DATA;
    }
}
