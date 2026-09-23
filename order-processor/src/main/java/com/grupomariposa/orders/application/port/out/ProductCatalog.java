package com.grupomariposa.orders.application.port.out;

import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.ProductProfile;

public interface ProductCatalog {

    Lookup<ProductProfile> findProduct(String productId, Market market);
}
