package com.grupomariposa.orders.infrastructure.cache;

import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.infrastructure.masterdata.Versioned;
import java.util.Objects;

public final class MasterDataCacheUpdater {

    private final VersionedCache<ClientProfile> clients;
    private final VersionedCache<ProductProfile> products;

    public MasterDataCacheUpdater(final VersionedCache<ClientProfile> clients,
                                  final VersionedCache<ProductProfile> products) {
        this.clients = Objects.requireNonNull(clients, "clients");
        this.products = Objects.requireNonNull(products, "products");
    }

    public CacheWrite clientChanged(final Versioned<ClientProfile> change) {
        return clients.apply(change.value().clientId(), change);
    }

    public CacheWrite clientRemoved(final String clientId, final long version) {
        return clients.evict(clientId, version);
    }

    public void clientChangeIgnored() {
        clients.ignored();
    }

    public CacheWrite productChanged(final MarketCode market,
                                     final Versioned<ProductProfile> change) {
        return products.apply(CachingProductCatalog.entryId(market, change.value().productId()),
                change);
    }

    public CacheWrite productRemoved(final MarketCode market, final String productId,
                                     final long version) {
        return products.evict(CachingProductCatalog.entryId(market, productId), version);
    }

    public void productChangeIgnored() {
        products.ignored();
    }
}
