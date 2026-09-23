package com.grupomariposa.orders.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupomariposa.orders.domain.Markets;
import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.ClientSegment;
import com.grupomariposa.orders.domain.model.ClientStatus;
import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.domain.model.ProductStatus;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRegime;
import com.grupomariposa.orders.infrastructure.crypto.AesGcmPiiCipher;
import com.grupomariposa.orders.infrastructure.crypto.PiiKeys;
import com.grupomariposa.orders.infrastructure.masterdata.Versioned;
import com.grupomariposa.orders.infrastructure.masterdata.VersionedClientSource;
import com.grupomariposa.orders.infrastructure.masterdata.VersionedProductSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CachingAdaptersTest {

    private static final int KEY_BYTES = 32;
    private static final Duration CLIENT_TTL = Duration.ofSeconds(60);
    private static final Duration PRODUCT_TTL = Duration.ofMinutes(10);
    private static final ClientProfile CLIENT = new ClientProfile("CLI-1", "Cliente",
            ClientStatus.BLOCKED, ClientSegment.RETAIL, TaxRegime.GENERAL, Markets.CO);
    private static final ProductProfile PRODUCT = new ProductProfile("PRD-9", null, null,
            ProductStatus.DISCONTINUED, TaxCategory.REDUCED);

    private final VersionedRedisStore store = mock(VersionedRedisStore.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final VersionedCache<ClientProfile> clientCache = new VersionedCache<>("clients",
            CLIENT_TTL, store, CacheCodecs.clients(new ObjectMapper(), new AesGcmPiiCipher(
                    PiiKeys.single("k1", Base64.getEncoder().encodeToString(
                            new byte[KEY_BYTES])))),
            new CacheMetrics(registry, "clients"));
    private final VersionedCache<ProductProfile> productCache = new VersionedCache<>("products",
            PRODUCT_TTL, store, CacheCodecs.products(new ObjectMapper()),
            new CacheMetrics(registry, "products"));
    private final MasterDataCacheUpdater updater =
            new MasterDataCacheUpdater(clientCache, productCache);

    @Test
    void should_key_clients_by_id_and_fill_from_the_versioned_source() {
        final VersionedClientSource source = mock(VersionedClientSource.class);
        when(store.read("clients:CLI-1")).thenReturn(Optional.empty());
        when(source.findVersionedClient("CLI-1"))
                .thenReturn(Lookup.found(new Versioned<>(CLIENT, 3L)));

        assertThat(new CachingClientDirectory(source, clientCache).findClient("CLI-1"))
                .isEqualTo(Lookup.found(CLIENT));
        verify(store).put(eq("clients:CLI-1"), eq(3L), anyString(), eq(CLIENT_TTL));
    }

    @Test
    void should_key_products_by_market_and_id() {
        final VersionedProductSource source = mock(VersionedProductSource.class);
        when(store.read("products:PE:PRD-9")).thenReturn(Optional.empty());
        when(source.findVersionedProduct("PRD-9", Markets.PE))
                .thenReturn(Lookup.found(new Versioned<>(PRODUCT, 1L)));

        assertThat(new CachingProductCatalog(source, productCache)
                .findProduct("PRD-9", Markets.PE)).isEqualTo(Lookup.found(PRODUCT));
        verify(store).put(eq("products:PE:PRD-9"), eq(1L), anyString(), eq(PRODUCT_TTL));
    }

    @Test
    void should_route_change_events_to_the_matching_keys() {
        when(store.put(eq("clients:CLI-1"), eq(8L), anyString(), eq(CLIENT_TTL)))
                .thenReturn(true);
        when(store.put(eq("products:CL:PRD-9"), eq(2L), anyString(), eq(PRODUCT_TTL)))
                .thenReturn(false);
        when(store.evict("clients:CLI-2", 4L, CLIENT_TTL)).thenReturn(true);
        when(store.evict("products:EC:PRD-7", 5L, PRODUCT_TTL)).thenReturn(true);

        assertThat(updater.clientChanged(new Versioned<>(CLIENT, 8L)))
                .isEqualTo(CacheWrite.APPLIED);
        assertThat(updater.productChanged(Markets.CL, new Versioned<>(PRODUCT, 2L)))
                .isEqualTo(CacheWrite.STALE);
        assertThat(updater.clientRemoved("CLI-2", 4L)).isEqualTo(CacheWrite.APPLIED);
        assertThat(updater.productRemoved(Markets.EC, "PRD-7", 5L))
                .isEqualTo(CacheWrite.APPLIED);
        updater.clientChangeIgnored();
        updater.productChangeIgnored();

        assertThat(ignored("clients")).isOne();
        assertThat(ignored("products")).isOne();
    }

    private double ignored(final String cache) {
        return registry.counter(CacheMetrics.INVALIDATIONS, CacheMetrics.CACHE_TAG, cache,
                CacheMetrics.OUTCOME_TAG, CacheMetrics.IGNORED).count();
    }
}
