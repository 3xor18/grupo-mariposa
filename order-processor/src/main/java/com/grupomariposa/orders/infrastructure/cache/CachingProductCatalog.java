package com.grupomariposa.orders.infrastructure.cache;

import com.grupomariposa.orders.application.port.out.ProductCatalog;
import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.infrastructure.masterdata.VersionedProductSource;
import java.util.Objects;

public final class CachingProductCatalog implements ProductCatalog {

    private static final String ID_SEPARATOR = ":";

    private final VersionedProductSource source;
    private final VersionedCache<ProductProfile> cache;

    public CachingProductCatalog(final VersionedProductSource source,
                                 final VersionedCache<ProductProfile> cache) {
        this.source = Objects.requireNonNull(source, "source");
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    @Override
    public Lookup<ProductProfile> findProduct(final String productId, final MarketCode market) {
        return cache.readThrough(entryId(market, productId),
                () -> source.findVersionedProduct(productId, market));
    }

    static String entryId(final MarketCode market, final String productId) {
        return market.value() + ID_SEPARATOR + productId;
    }
}
