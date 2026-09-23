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
        final ClientProfile incoming = change.value();
        final String clientId = incoming.clientId();
        if (incoming.name() != null) {
            return clients.apply(clientId, change);
        }
        return clients.peek(clientId).map(ClientProfile::name)
                .map(name -> clients.apply(clientId,
                        new Versioned<>(withName(incoming, name), change.version())))
                .orElseGet(() -> clients.evict(clientId, change.version()));
    }

    public CacheWrite clientRemoved(final String clientId, final long version) {
        return clients.evict(clientId, version);
    }

    public void clientChangeIgnored() {
        clients.ignored();
    }

    public void clientChangeFailed() {
        clients.failed();
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

    public void productChangeFailed() {
        products.failed();
    }

    private static ClientProfile withName(final ClientProfile profile, final String name) {
        return new ClientProfile(profile.clientId(), name, profile.status(), profile.segment(),
                profile.taxRegime(), profile.market());
    }
}
