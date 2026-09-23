package com.grupomariposa.orders.infrastructure.config;

import com.grupomariposa.orders.infrastructure.kafka.MessagingProperties;
import com.grupomariposa.orders.infrastructure.observability.CauseSanitizer;
import com.grupomariposa.orders.infrastructure.observability.CompositeProcessingObserver;
import com.grupomariposa.orders.infrastructure.observability.KafkaHealthIndicator;
import com.grupomariposa.orders.infrastructure.observability.LoggingProcessingObserver;
import com.grupomariposa.orders.infrastructure.observability.MetricsProcessingObserver;
import com.grupomariposa.orders.infrastructure.observability.OutboxMetrics;
import com.grupomariposa.orders.infrastructure.observability.ProcessingMetrics;
import com.grupomariposa.orders.infrastructure.observability.TraceIds;
import com.grupomariposa.orders.infrastructure.persistence.MongoOutboxStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import java.time.Clock;
import java.util.List;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration(proxyBeanMethods = false)
public class ObservabilityConfiguration {

    @Bean
    public CompositeProcessingObserver processingObserver(final MeterRegistry registry,
                                                          final CauseSanitizer sanitizer) {
        return new CompositeProcessingObserver(List.of(new MetricsProcessingObserver(registry),
                new LoggingProcessingObserver(sanitizer)));
    }

    @Bean
    public CauseSanitizer causeSanitizer() {
        return new CauseSanitizer();
    }

    @Bean
    public ProcessingMetrics processingMetrics(final MeterRegistry registry) {
        return new ProcessingMetrics(registry);
    }

    @Bean
    public TraceIds traceIds(final Tracer tracer) {
        return new TraceIds(tracer);
    }

    @Bean(destroyMethod = "close")
    public AdminClient healthAdminClient(final KafkaAdmin kafkaAdmin) {
        return AdminClient.create(kafkaAdmin.getConfigurationProperties());
    }

    @Bean
    public KafkaHealthIndicator kafkaHealthIndicator(final AdminClient healthAdminClient,
                                                     final MessagingProperties properties) {
        return new KafkaHealthIndicator(healthAdminClient, properties.healthTimeout());
    }

    @Bean
    public OutboxMetrics outboxMetrics(final MongoOutboxStore outboxStore, final Clock clock) {
        return new OutboxMetrics(outboxStore, clock);
    }
}
