package com.grupomariposa.orders.infrastructure.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import java.util.Objects;

public final class CacheMetrics {

    public static final String HITS = "orders.cache.hits";
    public static final String MISSES = "orders.cache.misses";
    public static final String ERRORS = "orders.cache.errors";
    public static final String INVALIDATIONS = "orders.cache.invalidations";
    public static final String CACHE_TAG = "cache";
    public static final String OPERATION_TAG = "operation";
    public static final String OUTCOME_TAG = "outcome";
    public static final String IGNORED = "ignored";
    public static final String ERROR = "error";

    private final MeterRegistry registry;
    private final String cache;
    private final Counter hits;
    private final Counter misses;

    public CacheMetrics(final MeterRegistry registry, final String cache) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.hits = Counter.builder(HITS).tag(CACHE_TAG, cache).register(registry);
        this.misses = Counter.builder(MISSES).tag(CACHE_TAG, cache).register(registry);
    }

    public void hit() {
        hits.increment();
    }

    public void miss() {
        misses.increment();
    }

    public void error(final CacheOperation operation) {
        Counter.builder(ERRORS).tag(CACHE_TAG, cache).tag(OPERATION_TAG, operation.tagValue())
                .register(registry).increment();
        if (operation == CacheOperation.EVENT) {
            invalidation(ERROR);
        }
    }

    public void invalidation(final CacheWrite result) {
        invalidation(result.name().toLowerCase(Locale.ROOT));
    }

    public void ignored() {
        invalidation(IGNORED);
    }

    private void invalidation(final String outcome) {
        Counter.builder(INVALIDATIONS).tag(CACHE_TAG, cache).tag(OUTCOME_TAG, outcome)
                .register(registry).increment();
    }
}
