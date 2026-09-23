package com.grupomariposa.orders.infrastructure.config;

import com.grupomariposa.orders.infrastructure.http.HttpDependenciesProperties;
import com.grupomariposa.orders.infrastructure.kafka.MessagingProperties;
import com.grupomariposa.orders.infrastructure.kafka.OutboxRelayProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;

public record OperationalBudget(
        MessagingProperties.RecordRetry recordRetry,
        HttpDependenciesProperties http,
        int maxConcurrentLookups,
        int maxPollRecords,
        Duration maxPollInterval,
        Duration producerDeliveryTimeout,
        OutboxRelayProperties relay) {

    static final String MAX_POLL_INTERVAL = "max.poll.interval.ms";
    static final String DELIVERY_TIMEOUT = "delivery.timeout.ms";
    static final int DEFAULT_MAX_POLL_RECORDS = 500;
    static final long DEFAULT_MAX_POLL_INTERVAL_MS = 300_000L;
    static final long DEFAULT_DELIVERY_TIMEOUT_MS = 120_000L;
    private static final String SEPARATOR = "; ";
    private static final String LOOKUPS_EXCEED_BULKHEAD =
            "processing max-concurrent-lookups (%d) must not exceed the HTTP bulkhead (%d)";
    private static final String POLL_BUDGET =
            "worst-case poll cycle %s (max.poll.records x attempt budget + record backoff) "
                    + "must be below max.poll.interval.ms %s";
    private static final String DELIVERY_EXCEEDS_SEND =
            "producer delivery.timeout.ms %s must not exceed the outbox send timeout %s";
    private static final String SEND_EXCEEDS_LEASE =
            "outbox send timeout %s must be shorter than the outbox lease %s";

    public OperationalBudget {
        Objects.requireNonNull(recordRetry, "recordRetry");
        Objects.requireNonNull(http, "http");
        Objects.requireNonNull(maxPollInterval, "maxPollInterval");
        Objects.requireNonNull(producerDeliveryTimeout, "producerDeliveryTimeout");
        Objects.requireNonNull(relay, "relay");
    }

    public static OperationalBudget of(final MessagingProperties messaging,
                                       final HttpDependenciesProperties http,
                                       final ProcessingProperties processing,
                                       final OutboxRelayProperties relay,
                                       final KafkaProperties kafka) {
        final Integer pollRecords = kafka.getConsumer().getMaxPollRecords();
        return new OperationalBudget(messaging.recordRetry(), http,
                processing.maxConcurrentLookups(),
                pollRecords == null ? DEFAULT_MAX_POLL_RECORDS : pollRecords,
                millis(kafka.getConsumer().getProperties(), MAX_POLL_INTERVAL,
                        DEFAULT_MAX_POLL_INTERVAL_MS),
                millis(kafka.getProducer().getProperties(), DELIVERY_TIMEOUT,
                        DEFAULT_DELIVERY_TIMEOUT_MS),
                relay);
    }

    public OperationalBudget verify() {
        final List<String> violations = violations();
        if (!violations.isEmpty()) {
            throw new IllegalStateException(String.join(SEPARATOR, violations));
        }
        return this;
    }

    public List<String> violations() {
        final List<String> violations = new ArrayList<>();
        final int bulkhead = http.resilience().bulkheadMaxConcurrentCalls();
        if (maxConcurrentLookups > bulkhead) {
            violations.add(LOOKUPS_EXCEED_BULKHEAD.formatted(maxConcurrentLookups, bulkhead));
        }
        if (pollCycleBudget().compareTo(maxPollInterval) >= 0) {
            violations.add(POLL_BUDGET.formatted(pollCycleBudget(), maxPollInterval));
        }
        if (producerDeliveryTimeout.compareTo(relay.sendTimeout()) > 0) {
            violations.add(DELIVERY_EXCEEDS_SEND.formatted(producerDeliveryTimeout,
                    relay.sendTimeout()));
        }
        if (relay.sendTimeout().compareTo(relay.lease()) >= 0) {
            violations.add(SEND_EXCEEDS_LEASE.formatted(relay.sendTimeout(), relay.lease()));
        }
        return List.copyOf(violations);
    }

    public Duration pollCycleBudget() {
        return attemptBudget().multipliedBy(maxPollRecords).plus(worstRecordBackoff());
    }

    public Duration attemptBudget() {
        final HttpDependenciesProperties.Resilience resilience = http.resilience();
        final int attempts = resilience.maxAttempts();
        final Duration perCall = http.connectTimeout().plus(http.readTimeout());
        final Duration retryWait = max(worstCallBackoff(), resilience.maxRetryAfter());
        return perCall.multipliedBy(attempts).plus(retryWait.multipliedBy(attempts - 1L))
                .plus(resilience.bulkheadMaxWait());
    }

    private Duration worstCallBackoff() {
        final HttpDependenciesProperties.Resilience resilience = http.resilience();
        final double growth = Math.pow(resilience.backoffMultiplier(),
                Math.max(resilience.maxAttempts() - 2, 0));
        final double jittered = growth * (1 + resilience.jitter());
        return Duration.ofMillis(Math.round(resilience.initialBackoff().toMillis() * jittered));
    }

    private Duration worstRecordBackoff() {
        final double growth = Math.pow(recordRetry.multiplier(),
                Math.max(recordRetry.maxAttempts() - 2, 0));
        return Duration.ofMillis(Math.round(recordRetry.initialInterval().toMillis() * growth));
    }

    private static Duration max(final Duration first, final Duration second) {
        return first.compareTo(second) >= 0 ? first : second;
    }

    private static Duration millis(final Map<String, String> properties, final String key,
                                   final long fallback) {
        final String value = properties.get(key);
        return Duration.ofMillis(value == null ? fallback : Long.parseLong(value.trim()));
    }
}
