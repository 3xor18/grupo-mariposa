package com.grupomariposa.orders.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.grupomariposa.orders.application.error.ExternalPermanentException;
import com.grupomariposa.orders.application.error.ExternalTransientException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResilienceTest {

    private static final HttpDependenciesProperties.Resilience SETTINGS =
            new HttpDependenciesProperties.Resilience(3, Duration.ofMillis(200), 2.0, 0.5,
                    Duration.ofSeconds(2), 20, 10, 50f, Duration.ofSeconds(10), 3, 32,
                    Duration.ofMillis(100));

    private final RetryRegistry retries = RetryRegistry.ofDefaults();
    private final CircuitBreakerRegistry circuitBreakers = CircuitBreakerRegistry.ofDefaults();
    private final BulkheadRegistry bulkheads = BulkheadRegistry.ofDefaults();
    private final ResilienceFactory factory =
            new ResilienceFactory(SETTINGS, retries, circuitBreakers, bulkheads);

    @Test
    void should_honour_retry_after_up_to_the_cap() {
        final IntervalBiFunction<Object> interval = intervalFunction();

        assertThat(interval.apply(1, Either.left(transientWithRetryAfter(Duration.ofSeconds(1)))))
                .isEqualTo(1000L);
        assertThat(interval.apply(1, Either.left(transientWithRetryAfter(Duration.ofSeconds(9)))))
                .isEqualTo(2000L);
    }

    @Test
    void should_use_jittered_exponential_backoff_otherwise() {
        final IntervalBiFunction<Object> interval = intervalFunction();

        assertThat(interval.apply(1, Either.left(new ExternalTransientException("x", "y", null))))
                .isBetween(100L, 300L);
        assertThat(interval.apply(2, Either.right("ok"))).isBetween(200L, 600L);
        assertThat(interval.apply(1, Either.left(new IllegalStateException())))
                .isBetween(100L, 300L);
    }

    @Test
    void should_configure_breaker_and_bulkhead_from_settings() {
        final ResilientExecutor executor = factory.create(Dependency.CLIENTS_API);
        final CircuitBreakerConfig breaker = circuitBreakers
                .circuitBreaker(Dependency.CLIENTS_API.id()).getCircuitBreakerConfig();

        assertThat(breaker.getSlidingWindowSize()).isEqualTo(20);
        assertThat(breaker.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(breaker.getRecordExceptionPredicate()
                .test(new ExternalPermanentException("x", "y", null))).isFalse();
        assertThat(bulkheads.bulkhead(Dependency.CLIENTS_API.id()).getBulkheadConfig()
                .getMaxConcurrentCalls()).isEqualTo(32);
        assertThat(executor.retry().getRetryConfig().getMaxAttempts()).isEqualTo(3);
    }

    @Test
    void should_not_retry_permanent_failures() {
        final AtomicInteger calls = new AtomicInteger();
        final ResilientExecutor executor = factory.create(Dependency.CLIENTS_API);

        assertThatThrownBy(() -> executor.execute(() -> {
            calls.incrementAndGet();
            throw new ExternalPermanentException("clients-api", "400", null);
        })).isInstanceOf(ExternalPermanentException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void should_translate_saturated_bulkhead_to_transient() {
        final Bulkhead full = Bulkhead.of("full", BulkheadConfig.custom()
                .maxConcurrentCalls(1).maxWaitDuration(Duration.ZERO).build());
        full.tryAcquirePermission();
        final ResilientExecutor executor = new ResilientExecutor(Dependency.PRODUCTS_API,
                Retry.ofDefaults("r"), CircuitBreaker.ofDefaults("c"), full);

        assertThatThrownBy(() -> executor.execute(() -> "never"))
                .isInstanceOf(ExternalTransientException.class)
                .hasMessage("products-api bulkhead is saturated");
    }

    @SuppressWarnings("unchecked")
    private IntervalBiFunction<Object> intervalFunction() {
        return (IntervalBiFunction<Object>) factory.create(Dependency.CLIENTS_API).retry()
                .getRetryConfig().getIntervalBiFunction();
    }

    private static ExternalTransientException transientWithRetryAfter(final Duration wait) {
        return new ExternalTransientException("clients-api", "429", wait, null);
    }
}
