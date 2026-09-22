package com.grupomariposa.orders.infrastructure.config;

import com.grupomariposa.orders.infrastructure.observability.CauseSanitizer;
import com.grupomariposa.orders.application.port.in.ProcessOrderUseCase;
import com.grupomariposa.orders.application.port.in.PublishPendingEventsUseCase;
import com.grupomariposa.orders.application.port.in.RecordTechnicalFailureUseCase;
import com.grupomariposa.orders.application.port.out.ProcessingObserver;
import com.grupomariposa.orders.application.port.out.TimeProvider;
import com.grupomariposa.orders.application.validation.OrderCommandValidator;
import com.grupomariposa.orders.infrastructure.kafka.MessagingProperties;
import com.grupomariposa.orders.infrastructure.kafka.dlt.DeadLetterProducer;
import com.grupomariposa.orders.infrastructure.kafka.dlt.DeadLetterRecoverer;
import com.grupomariposa.orders.infrastructure.kafka.dlt.DltHeadersFactory;
import com.grupomariposa.orders.infrastructure.kafka.dlt.OrderDeadLetterPublisher;
import com.grupomariposa.orders.infrastructure.kafka.inbound.OrderCreatedListener;
import com.grupomariposa.orders.infrastructure.kafka.inbound.OrderMessageMapper;
import com.grupomariposa.orders.infrastructure.kafka.inbound.OrderMessageReader;
import com.grupomariposa.orders.infrastructure.kafka.inbound.RetryableRecordFailure;
import com.grupomariposa.orders.infrastructure.kafka.outbound.KafkaEventPublisher;
import com.grupomariposa.orders.infrastructure.kafka.outbound.OutboxRelayScheduler;
import com.grupomariposa.orders.infrastructure.observability.ProcessingMetrics;
import com.grupomariposa.orders.infrastructure.observability.TraceContext;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration(proxyBeanMethods = false)
public class KafkaConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.kafka", name = "create-topics", havingValue = "true")
    public KafkaAdmin.NewTopics orderTopics(final MessagingProperties properties) {
        final MessagingProperties.Topics topics = properties.topics();
        final short replicas = properties.replicationFactor();
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(topics.ordersCreated())
                        .partitions(topics.ordersCreatedPartitions()).replicas(replicas).build(),
                TopicBuilder.name(topics.ordersProcessed())
                        .partitions(topics.ordersProcessedPartitions()).replicas(replicas)
                        .build(),
                TopicBuilder.name(topics.deadLetter())
                        .partitions(topics.deadLetterPartitions()).replicas(replicas).build());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            final ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            final ConsumerFactory<Object, Object> consumerFactory) {
        final ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.getContainerProperties().setDeliveryAttemptHeader(true);
        return factory;
    }

    @Bean(destroyMethod = "close")
    public DeadLetterProducer deadLetterProducer(
            final ProducerFactory<Object, Object> kafkaProducerFactory) {
        return new DeadLetterProducer(kafkaProducerFactory);
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            final DeadLetterProducer deadLetterProducer,
            final MessagingProperties properties, final Clock clock,
            final CauseSanitizer sanitizer, final RecordTechnicalFailureUseCase technicalFailures,
            final ProcessingObserver observer, final ProcessingMetrics metrics) {
        final OrderDeadLetterPublisher deadLetters = new OrderDeadLetterPublisher(
                deadLetterProducer.template(), properties.topics().deadLetter(),
                new DltHeadersFactory(clock, sanitizer));
        final DefaultErrorHandler handler = new DefaultErrorHandler(
                new DeadLetterRecoverer(deadLetters, technicalFailures, observer, metrics,
                        sanitizer), backOff(properties.recordRetry()));
        handler.setResetStateOnRecoveryFailure(false);
        handler.defaultFalse();
        handler.addRetryableExceptions(RetryableRecordFailure.class);
        return handler;
    }

    @Bean
    public OrderCreatedListener orderCreatedListener(final OrderCommandValidator validator,
                                                     final ProcessOrderUseCase useCase,
                                                     final ProcessingObserver observer,
                                                     final TimeProvider timeProvider,
                                                     final TraceContext traceContext,
                                                     final ProcessingMetrics metrics) {
        return new OrderCreatedListener(new OrderMessageReader(), new OrderMessageMapper(),
                validator, useCase, observer,
                timeProvider, traceContext, metrics);
    }

    @Bean
    public KafkaEventPublisher eventPublisher(final KafkaTemplate<String, String> kafkaTemplate) {
        return new KafkaEventPublisher(kafkaTemplate);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.outbox", name = "enabled", havingValue = "true")
    public OutboxRelayScheduler outboxRelayScheduler(final PublishPendingEventsUseCase relay,
                                                     final CauseSanitizer sanitizer,
                                                     final MeterRegistry registry) {
        return new OutboxRelayScheduler(relay, sanitizer, registry);
    }

    private static ExponentialBackOffWithMaxRetries backOff(
            final MessagingProperties.RecordRetry retry) {
        final ExponentialBackOffWithMaxRetries backOff =
                new ExponentialBackOffWithMaxRetries(retry.maxAttempts() - 1);
        backOff.setInitialInterval(retry.initialInterval().toMillis());
        backOff.setMultiplier(retry.multiplier());
        return backOff;
    }
}
