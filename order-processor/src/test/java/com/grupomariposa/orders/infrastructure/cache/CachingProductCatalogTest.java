package com.grupomariposa.orders.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupomariposa.orders.application.port.out.ProductCatalog;
import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.domain.model.ProductStatus;
import com.grupomariposa.orders.domain.model.TaxCategory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class CachingProductCatalogTest {

    private static final String KEY = "products:MX:PRD-001";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final ProductProfile PRODUCT = new ProductProfile("PRD-001", "Bebida",
            "BEB-600-PET", ProductStatus.ACTIVE, TaxCategory.STANDARD);

    private final ProductCatalog delegate = mock(ProductCatalog.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private CachingProductCatalog catalog;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        catalog = new CachingProductCatalog(delegate, redis, objectMapper,
                new ProductCacheProperties(true, TTL, "products"), registry);
    }

    @Test
    void should_serve_hits_without_calling_the_api() throws Exception {
        when(values.get(KEY)).thenReturn(objectMapper.writeValueAsString(
                CachedProduct.from(PRODUCT)));

        assertThat(catalog.findProduct("PRD-001", Market.MX)).isEqualTo(Lookup.found(PRODUCT));
        verifyNoInteractions(delegate);
        assertThat(registry.counter(CachingProductCatalog.HITS_METRIC, "cache", "products")
                .count()).isOne();
    }

    @Test
    void should_cache_found_products_with_ttl_on_miss() {
        when(delegate.findProduct("PRD-001", Market.MX)).thenReturn(Lookup.found(PRODUCT));

        assertThat(catalog.findProduct("PRD-001", Market.MX)).isEqualTo(Lookup.found(PRODUCT));
        verify(values).set(eq(KEY), anyString(), eq(TTL));
    }

    @Test
    void should_not_cache_not_found_results() {
        when(delegate.findProduct("PRD-404", Market.CO)).thenReturn(Lookup.notFound());

        assertThat(catalog.findProduct("PRD-404", Market.CO)).isEqualTo(Lookup.notFound());
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void should_degrade_to_direct_call_when_redis_is_down() {
        when(values.get(KEY)).thenThrow(new RedisConnectionFailureException("down"));
        doThrow(new RedisConnectionFailureException("down"))
                .when(values).set(anyString(), anyString(), any(Duration.class));
        when(delegate.findProduct("PRD-001", Market.MX)).thenReturn(Lookup.found(PRODUCT));

        assertThat(catalog.findProduct("PRD-001", Market.MX)).isEqualTo(Lookup.found(PRODUCT));
        assertThat(registry.counter(CachingProductCatalog.ERRORS_METRIC, "cache", "products",
                "operation", "read").count()).isOne();
        assertThat(registry.counter(CachingProductCatalog.ERRORS_METRIC, "cache", "products",
                "operation", "write").count()).isOne();
    }

    @Test
    void should_ignore_corrupted_entries() {
        when(values.get(KEY)).thenReturn("{corrupted");
        when(delegate.findProduct("PRD-001", Market.MX)).thenReturn(Lookup.found(PRODUCT));

        assertThat(catalog.findProduct("PRD-001", Market.MX)).isEqualTo(Lookup.found(PRODUCT));
        assertThat(registry.counter(CachingProductCatalog.MISSES_METRIC, "cache", "products")
                .count()).isOne();
    }
}
