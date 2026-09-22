package com.grupomariposa.orders.infrastructure.http;

import com.grupomariposa.orders.application.error.ExternalTransientException;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public final class ResilienceFactory {

    private final HttpDependenciesProperties.Resilience settings;
    private final RetryRegistry retries;
    private final CircuitBreakerRegistry circuitBreakers;
    private final BulkheadRegistry bulkheads;

    public ResilienceFactory(final HttpDependenciesProperties.Resilience settings,
                             final RetryRegistry retries,
                             final CircuitBreakerRegistry circuitBreakers,
                             final BulkheadRegistry bulkheads) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.retries = Objects.requireNonNull(retries, "retries");
        this.circuitBreakers = Objects.requireNonNull(circuitBreakers, "circuitBreakers");
        this.bulkheads = Objects.requireNonNull(bulkheads, "bulkheads");
    }

    public ResilientExecutor create(final Dependency dependency) {
        final String name = dependency.id();
        return new ResilientExecutor(dependency, retries.retry(name, retryConfig()),
                circuitBreakers.circuitBreaker(name, circuitBreakerConfig()),
                bulkheads.bulkhead(name, bulkheadConfig()));
    }

    public Retry retryOf(final Dependency dependency) {
        return retries.retry(dependency.id(), retryConfig());
    }

    RetryConfig retryConfig() {
        return RetryConfig.custom()
                .maxAttempts(settings.maxAttempts())
                .retryOnException(ExternalTransientException.class::isInstance)
                .intervalBiFunction(intervalFunction())
                .build();
    }

    IntervalBiFunction<Object> intervalFunction() {
        final IntervalFunction backoff = IntervalFunction.ofExponentialRandomBackoff(
                settings.initialBackoff(), settings.backoffMultiplier(), settings.jitter());
        return (attempt, outcome) -> retryAfter(outcome)
                .map(Duration::toMillis)
                .orElseGet(() -> backoff.apply(attempt));
    }

    private Optional<Duration> retryAfter(final Either<Throwable, Object> outcome) {
        if (outcome.isLeft() && outcome.getLeft() instanceof ExternalTransientException failure) {
            return failure.retryAfter().map(this::capRetryAfter);
        }
        return Optional.empty();
    }

    private Duration capRetryAfter(final Duration requested) {
        return requested.compareTo(settings.maxRetryAfter()) > 0
                ? settings.maxRetryAfter() : requested;
    }

    CircuitBreakerConfig circuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.slidingWindowSize())
                .minimumNumberOfCalls(settings.minimumNumberOfCalls())
                .failureRateThreshold(settings.failureRateThreshold())
                .waitDurationInOpenState(settings.openStateDuration())
                .permittedNumberOfCallsInHalfOpenState(settings.halfOpenCalls())
                .recordException(ExternalTransientException.class::isInstance)
                .build();
    }

    BulkheadConfig bulkheadConfig() {
        return BulkheadConfig.custom()
                .maxConcurrentCalls(settings.bulkheadMaxConcurrentCalls())
                .maxWaitDuration(settings.bulkheadMaxWait())
                .build();
    }
}
