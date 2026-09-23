package com.grupomariposa.orders.infrastructure.masterdata;

import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.ProductProfile;

public interface VersionedProductSource {

    Lookup<Versioned<ProductProfile>> findVersionedProduct(String productId, MarketCode market);
}
