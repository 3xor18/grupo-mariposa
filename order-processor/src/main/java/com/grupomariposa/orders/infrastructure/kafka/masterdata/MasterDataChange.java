package com.grupomariposa.orders.infrastructure.kafka.masterdata;

import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.infrastructure.cache.CacheWrite;
import com.grupomariposa.orders.infrastructure.cache.MasterDataCacheUpdater;
import com.grupomariposa.orders.infrastructure.masterdata.Versioned;

public sealed interface MasterDataChange {

    CacheWrite applyTo(MasterDataCacheUpdater updater);

    record ClientChanged(Versioned<ClientProfile> client) implements MasterDataChange {

        @Override
        public CacheWrite applyTo(final MasterDataCacheUpdater updater) {
            return updater.clientChanged(client);
        }
    }

    record ClientRemoved(String clientId, long version) implements MasterDataChange {

        @Override
        public CacheWrite applyTo(final MasterDataCacheUpdater updater) {
            return updater.clientRemoved(clientId, version);
        }
    }

    record ProductChanged(MarketCode market, Versioned<ProductProfile> product)
            implements MasterDataChange {

        @Override
        public CacheWrite applyTo(final MasterDataCacheUpdater updater) {
            return updater.productChanged(market, product);
        }
    }

    record ProductRemoved(MarketCode market, String productId, long version)
            implements MasterDataChange {

        @Override
        public CacheWrite applyTo(final MasterDataCacheUpdater updater) {
            return updater.productRemoved(market, productId, version);
        }
    }
}
