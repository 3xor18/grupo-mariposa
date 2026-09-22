package com.grupomariposa.orders.infrastructure.config;

import com.grupomariposa.orders.infrastructure.http.HttpDependenciesProperties;
import com.grupomariposa.orders.infrastructure.kafka.MessagingProperties;
import com.grupomariposa.orders.infrastructure.kafka.OutboxRelayProperties;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OperationalConfiguration {

    @Bean
    public OperationalBudget operationalBudget(final MessagingProperties messaging,
                                               final HttpDependenciesProperties http,
                                               final ProcessingProperties processing,
                                               final OutboxRelayProperties relay,
                                               final KafkaProperties kafka) {
        return OperationalBudget.of(messaging, http, processing, relay, kafka).verify();
    }
}
