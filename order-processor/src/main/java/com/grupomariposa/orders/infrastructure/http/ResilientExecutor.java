package com.grupomariposa.orders.infrastructure.http;

import com.grupomariposa.orders.application.error.ExternalTransientException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.Objects;
import java.util.function.Supplier;

public final class ResilientExecutor {

    private static final String CIRCUIT_OPEN = "%s circuit breaker is open";
    private static final String BULKHEAD_FULL = "%s bulkhead is saturated";

    private final Dependency dependency;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;

    public ResilientExecutor(final Dependency dependency, final Retry retry,
                             final CircuitBreaker circuitBreaker, final Bulkhead bulkhead) {
        this.dependency = Objects.requireNonNull(dependency, "dependency");
        this.retry = Objects.requireNonNull(retry, "retry");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker");
        this.bulkhead = Objects.requireNonNull(bulkhead, "bulkhead");
    }

    public Retry retry() {
        return retry;
    }

    public <T> T execute(final Supplier<T> call) {
        final Supplier<T> guarded = Bulkhead.decorateSupplier(bulkhead, call);
        final Supplier<T> broken = CircuitBreaker.decorateSupplier(circuitBreaker, guarded);
        try {
            return Retry.decorateSupplier(retry, broken).get();
        } catch (CallNotPermittedException open) {
            throw new ExternalTransientException(dependency.id(),
                    CIRCUIT_OPEN.formatted(dependency.id()), open);
        } catch (BulkheadFullException saturated) {
            throw new ExternalTransientException(dependency.id(),
                    BULKHEAD_FULL.formatted(dependency.id()), saturated);
        }
    }
}
