package com.grupomariposa.orders.infrastructure.http.product;

import com.grupomariposa.orders.application.port.out.ProductCatalog;
import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.infrastructure.http.LookupExchange;
import com.grupomariposa.orders.infrastructure.http.ResilientExecutor;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public final class HttpProductCatalog implements ProductCatalog {

    private static final String PRODUCT_PATH = "/products/{productId}?market={market}";

    private final RestClient restClient;
    private final LookupExchange exchange;
    private final ResilientExecutor resilience;
    private final ProductResponseMapper mapper = new ProductResponseMapper();

    public HttpProductCatalog(final RestClient restClient, final LookupExchange exchange,
                              final ResilientExecutor resilience) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.resilience = Objects.requireNonNull(resilience, "resilience");
    }

    @Override
    public Lookup<ProductProfile> findProduct(final String productId, final Market market) {
        return resilience.execute(() -> exchange.fetch(
                restClient.get().uri(PRODUCT_PATH, productId, market.name())
                        .accept(MediaType.APPLICATION_JSON),
                ProductResponse.class, mapper::toProfile));
    }
}
