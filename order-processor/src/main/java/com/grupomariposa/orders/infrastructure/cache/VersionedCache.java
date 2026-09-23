package com.grupomariposa.orders.infrastructure.cache;

import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.infrastructure.masterdata.Versioned;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

public final class VersionedCache<T> {

    private static final String KEY_SEPARATOR = ":";
    private static final Logger LOG = LoggerFactory.getLogger(VersionedCache.class);

    private final String keyPrefix;
    private final Duration ttl;
    private final VersionedRedisStore store;
    private final CacheCodec<T> codec;
    private final CacheMetrics metrics;

    public VersionedCache(final String keyPrefix, final Duration ttl,
                          final VersionedRedisStore store, final CacheCodec<T> codec,
                          final CacheMetrics metrics) {
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.store = Objects.requireNonNull(store, "store");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public Lookup<T> readThrough(final String id, final Supplier<Lookup<Versioned<T>>> loader) {
        final String key = keyOf(id);
        final Optional<T> cached = read(key);
        if (cached.isPresent()) {
            metrics.hit();
            return Lookup.found(cached.get());
        }
        metrics.miss();
        final Lookup<Versioned<T>> fresh = loader.get();
        final Optional<Versioned<T>> loaded = fresh.value();
        if (loaded.isPresent() && write(key, loaded.get()) == CacheWrite.STALE) {
            return read(key).map(Lookup::found).orElseGet(() -> fresh.map(Versioned::value));
        }
        return fresh.map(Versioned::value);
    }

    public CacheWrite apply(final String id, final Versioned<T> change) {
        return record(write(keyOf(id), change, CacheOperation.EVENT));
    }

    public CacheWrite evict(final String id, final long version) {
        try {
            return record(store.evict(keyOf(id), version, ttl)
                    ? CacheWrite.APPLIED : CacheWrite.STALE);
        } catch (DataAccessException unavailable) {
            return record(degraded(CacheOperation.EVENT, unavailable));
        }
    }

    public void ignored() {
        metrics.ignored();
    }

    public void failed() {
        metrics.failed();
    }

    private Optional<T> read(final String key) {
        try {
            return store.read(key).flatMap(codec::decode);
        } catch (DataAccessException | CacheCodecException unavailable) {
            degraded(CacheOperation.READ, unavailable);
            return Optional.empty();
        }
    }

    private CacheWrite write(final String key, final Versioned<T> entry) {
        return write(key, entry, CacheOperation.WRITE);
    }

    private CacheWrite write(final String key, final Versioned<T> entry,
                             final CacheOperation operation) {
        try {
            return store.put(key, entry.version(), codec.encode(entry.value()), ttl)
                    ? CacheWrite.APPLIED : CacheWrite.STALE;
        } catch (DataAccessException | CacheCodecException unavailable) {
            return degraded(operation, unavailable);
        }
    }

    private CacheWrite record(final CacheWrite result) {
        if (result != CacheWrite.FAILED) {
            metrics.invalidation(result);
        }
        return result;
    }

    private CacheWrite degraded(final CacheOperation operation, final RuntimeException cause) {
        metrics.error(operation);
        LOG.warn("Cache {} degraded for {}: {}", operation.tagValue(), keyPrefix,
                cause.getClass().getSimpleName());
        return CacheWrite.FAILED;
    }

    private String keyOf(final String id) {
        return keyPrefix + KEY_SEPARATOR + id;
    }
}
