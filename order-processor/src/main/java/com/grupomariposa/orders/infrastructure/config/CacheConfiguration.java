package com.grupomariposa.orders.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.infrastructure.cache.CacheCodecs;
import com.grupomariposa.orders.infrastructure.cache.CacheMetrics;
import com.grupomariposa.orders.infrastructure.cache.ClientCacheProperties;
import com.grupomariposa.orders.infrastructure.cache.MasterDataCacheUpdater;
import com.grupomariposa.orders.infrastructure.cache.ProductCacheProperties;
import com.grupomariposa.orders.infrastructure.cache.VersionedCache;
import com.grupomariposa.orders.infrastructure.cache.VersionedRedisStore;
import com.grupomariposa.orders.infrastructure.crypto.AesGcmPiiCipher;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
public class CacheConfiguration {

    private static final String CLIENTS = "clients";
    private static final String PRODUCTS = "products";

    @Bean
    public VersionedRedisStore versionedRedisStore(final StringRedisTemplate redis) {
        return new VersionedRedisStore(redis);
    }

    @Bean
    public VersionedCache<ClientProfile> clientCache(final ClientCacheProperties properties,
                                                     final VersionedRedisStore store,
                                                     final ObjectMapper objectMapper,
                                                     final AesGcmPiiCipher cipher,
                                                     final MeterRegistry registry) {
        return new VersionedCache<>(properties.keyPrefix(), properties.ttl(), store,
                CacheCodecs.clients(objectMapper, cipher), new CacheMetrics(registry, CLIENTS));
    }

    @Bean
    public VersionedCache<ProductProfile> productCache(final ProductCacheProperties properties,
                                                       final VersionedRedisStore store,
                                                       final ObjectMapper objectMapper,
                                                       final MeterRegistry registry) {
        return new VersionedCache<>(properties.keyPrefix(), properties.ttl(), store,
                CacheCodecs.products(objectMapper), new CacheMetrics(registry, PRODUCTS));
    }

    @Bean
    public MasterDataCacheUpdater masterDataCacheUpdater(
            final VersionedCache<ClientProfile> clientCache,
            final VersionedCache<ProductProfile> productCache) {
        return new MasterDataCacheUpdater(clientCache, productCache);
    }
}
