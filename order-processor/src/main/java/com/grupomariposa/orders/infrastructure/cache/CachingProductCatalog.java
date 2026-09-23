package com.grupomariposa.orders.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupomariposa.orders.application.port.out.ProductCatalog;
import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.ProductProfile;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

public final class CachingProductCatalog implements ProductCatalog {

    static final String ERRORS_METRIC = "orders.cache.errors";
    static final String HITS_METRIC = "orders.cache.hits";
    static final String MISSES_METRIC = "orders.cache.misses";
    private static final String OPERATION = "operation";
    private static final String READ = "read";
    private static final String WRITE = "write";
    private static final String CACHE_TAG = "cache";
    private static final String CACHE_NAME = "products";
    private static final String KEY_SEPARATOR = ":";
    private static final Logger LOG = LoggerFactory.getLogger(CachingProductCatalog.class);

    private final ProductCatalog delegate;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ProductCacheProperties properties;
    private final Counter readErrors;
    private final Counter writeErrors;
    private final Counter hits;
    private final Counter misses;

    public CachingProductCatalog(final ProductCatalog delegate, final StringRedisTemplate redis,
                                 final ObjectMapper objectMapper,
                                 final ProductCacheProperties properties,
                                 final MeterRegistry registry) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.redis = Objects.requireNonNull(redis, "redis");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.readErrors = counter(registry, ERRORS_METRIC, READ);
        this.writeErrors = counter(registry, ERRORS_METRIC, WRITE);
        this.hits = Counter.builder(HITS_METRIC).tag(CACHE_TAG, CACHE_NAME).register(registry);
        this.misses = Counter.builder(MISSES_METRIC).tag(CACHE_TAG, CACHE_NAME).register(registry);
    }

    @Override
    public Lookup<ProductProfile> findProduct(final String productId, final MarketCode market) {
        final String key = keyOf(productId, market);
        final Optional<ProductProfile> cached = read(key);
        if (cached.isPresent()) {
            hits.increment();
            return Lookup.found(cached.get());
        }
        misses.increment();
        final Lookup<ProductProfile> fresh = delegate.findProduct(productId, market);
        fresh.value().ifPresent(product -> write(key, product));
        return fresh;
    }

    private Optional<ProductProfile> read(final String key) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(key)).flatMap(this::decode);
        } catch (DataAccessException | JsonConversionFailure unavailable) {
            readErrors.increment();
            LOG.warn("Product cache read degraded: {}", unavailable.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private void write(final String key, final ProductProfile product) {
        try {
            redis.opsForValue().set(key, encode(product), properties.ttl());
        } catch (DataAccessException | JsonConversionFailure unavailable) {
            writeErrors.increment();
            LOG.warn("Product cache write degraded: {}", unavailable.getClass().getSimpleName());
        }
    }

    private Optional<ProductProfile> decode(final String json) {
        try {
            return Optional.of(objectMapper.readValue(json, CachedProduct.class))
                    .filter(CachedProduct::isComplete)
                    .map(CachedProduct::toProfile);
        } catch (JsonProcessingException invalid) {
            throw new JsonConversionFailure(invalid);
        }
    }

    private String encode(final ProductProfile product) {
        try {
            return objectMapper.writeValueAsString(CachedProduct.from(product));
        } catch (JsonProcessingException invalid) {
            throw new JsonConversionFailure(invalid);
        }
    }

    private String keyOf(final String productId, final MarketCode market) {
        return properties.keyPrefix() + KEY_SEPARATOR + market.value() + KEY_SEPARATOR + productId;
    }

    private static Counter counter(final MeterRegistry registry, final String name,
                                   final String operation) {
        return Counter.builder(name).tag(CACHE_TAG, CACHE_NAME).tag(OPERATION, operation)
                .register(registry);
    }

    private static final class JsonConversionFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;

        JsonConversionFailure(final Exception cause) {
            super(cause);
        }
    }
}
