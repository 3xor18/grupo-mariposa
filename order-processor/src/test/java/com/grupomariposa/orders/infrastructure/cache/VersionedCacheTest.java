package com.grupomariposa.orders.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.domain.model.ProductStatus;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.infrastructure.masterdata.Versioned;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

class VersionedCacheTest {

    private static final String ID = "MX:PRD-001";
    private static final String KEY = "products:MX:PRD-001";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final ProductProfile ACTIVE = new ProductProfile("PRD-001", "Bebida",
            "BEB-600-PET", ProductStatus.ACTIVE, TaxCategory.STANDARD);
    private static final ProductProfile DISCONTINUED = new ProductProfile("PRD-001", "Bebida",
            "BEB-600-PET", ProductStatus.DISCONTINUED, TaxCategory.STANDARD);

    private final VersionedRedisStore store = mock(VersionedRedisStore.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final CacheCodec<ProductProfile> codec = CacheCodecs.products(new ObjectMapper());
    private final VersionedCache<ProductProfile> cache = new VersionedCache<>("products", TTL,
            store, codec, new CacheMetrics(registry, "products"));
    private final AtomicInteger loads = new AtomicInteger();

    @Test
    void should_serve_hits_without_calling_the_loader() {
        when(store.read(KEY)).thenReturn(Optional.of(codec.encode(ACTIVE)));

        assertThat(cache.readThrough(ID, loader(Lookup.notFound())))
                .isEqualTo(Lookup.found(ACTIVE));
        assertThat(loads).hasValue(0);
        assertThat(count(CacheMetrics.HITS)).isOne();
    }

    @Test
    void should_fill_found_entries_with_their_version_on_miss() {
        when(store.read(KEY)).thenReturn(Optional.empty());
        when(store.put(eq(KEY), eq(4L), anyString(), eq(TTL))).thenReturn(true);

        assertThat(cache.readThrough(ID, loader(Lookup.found(new Versioned<>(ACTIVE, 4L)))))
                .isEqualTo(Lookup.found(ACTIVE));
        assertThat(count(CacheMetrics.MISSES)).isOne();
    }

    @Test
    void should_not_cache_not_found_results() {
        when(store.read(KEY)).thenReturn(Optional.empty());

        assertThat(cache.readThrough(ID, loader(Lookup.notFound()))).isEqualTo(Lookup.notFound());
        verify(store, never()).put(anyString(), anyLong(), anyString(), eq(TTL));
    }

    @Test
    void should_prefer_newer_cached_entry_when_the_fill_is_stale() {
        when(store.read(KEY)).thenReturn(Optional.empty(),
                Optional.of(codec.encode(DISCONTINUED)));
        when(store.put(eq(KEY), eq(2L), anyString(), eq(TTL))).thenReturn(false);

        assertThat(cache.readThrough(ID, loader(Lookup.found(new Versioned<>(ACTIVE, 2L)))))
                .isEqualTo(Lookup.found(DISCONTINUED));
    }

    @Test
    void should_return_loaded_value_when_stale_fill_hits_an_evicted_entry() {
        when(store.read(KEY)).thenReturn(Optional.empty());
        when(store.put(eq(KEY), eq(2L), anyString(), eq(TTL))).thenReturn(false);

        assertThat(cache.readThrough(ID, loader(Lookup.found(new Versioned<>(ACTIVE, 2L)))))
                .isEqualTo(Lookup.found(ACTIVE));
    }

    @Test
    void should_degrade_to_the_loader_when_redis_is_down() {
        when(store.read(KEY)).thenThrow(new RedisConnectionFailureException("down"));
        when(store.put(eq(KEY), eq(1L), anyString(), eq(TTL)))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThat(cache.readThrough(ID, loader(Lookup.found(new Versioned<>(ACTIVE, 1L)))))
                .isEqualTo(Lookup.found(ACTIVE));
        assertThat(errors("read")).isOne();
        assertThat(errors("write")).isOne();
    }

    @Test
    void should_treat_corrupted_and_incomplete_entries_as_misses() {
        when(store.read(KEY)).thenReturn(Optional.of("{corrupted"),
                Optional.of("{\"productId\":\"PRD-001\"}"));

        cache.readThrough(ID, loader(Lookup.notFound()));
        cache.readThrough(ID, loader(Lookup.notFound()));

        assertThat(loads).hasValue(2);
        assertThat(errors("read")).isOne();
    }

    @Test
    void should_count_event_outcomes() {
        when(store.put(eq(KEY), eq(5L), anyString(), eq(TTL))).thenReturn(true, false)
                .thenThrow(new RedisConnectionFailureException("down"));
        final Versioned<ProductProfile> change = new Versioned<>(DISCONTINUED, 5L);

        assertThat(cache.apply(ID, change)).isEqualTo(CacheWrite.APPLIED);
        assertThat(cache.apply(ID, change)).isEqualTo(CacheWrite.STALE);
        assertThat(cache.apply(ID, change)).isEqualTo(CacheWrite.FAILED);
        assertThat(outcome("error")).isZero();
        cache.ignored();
        cache.failed();

        assertThat(outcome("applied")).isOne();
        assertThat(outcome("stale")).isOne();
        assertThat(outcome("error")).isOne();
        assertThat(outcome("ignored")).isOne();
        assertThat(errors("event")).isOne();
    }

    @Test
    void should_peek_cached_entries_without_counting_hits() {
        when(store.read(KEY)).thenReturn(Optional.of(codec.encode(ACTIVE)), Optional.empty());

        assertThat(cache.peek(ID)).contains(ACTIVE);
        assertThat(cache.peek(ID)).isEmpty();
        assertThat(count(CacheMetrics.HITS)).isZero();
    }

    @Test
    void should_evict_with_version_guard() {
        when(store.evict(KEY, 6L, TTL)).thenReturn(true, false)
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThat(cache.evict(ID, 6L)).isEqualTo(CacheWrite.APPLIED);
        assertThat(cache.evict(ID, 6L)).isEqualTo(CacheWrite.STALE);
        assertThat(cache.evict(ID, 6L)).isEqualTo(CacheWrite.FAILED);
    }

    private Supplier<Lookup<Versioned<ProductProfile>>> loader(
            final Lookup<Versioned<ProductProfile>> result) {
        return () -> {
            loads.incrementAndGet();
            return result;
        };
    }

    private double count(final String metric) {
        return registry.counter(metric, CacheMetrics.CACHE_TAG, "products").count();
    }

    private double errors(final String operation) {
        return registry.counter(CacheMetrics.ERRORS, CacheMetrics.CACHE_TAG, "products",
                CacheMetrics.OPERATION_TAG, operation).count();
    }

    private double outcome(final String outcome) {
        return registry.counter(CacheMetrics.INVALIDATIONS, CacheMetrics.CACHE_TAG, "products",
                CacheMetrics.OUTCOME_TAG, outcome).count();
    }
}
