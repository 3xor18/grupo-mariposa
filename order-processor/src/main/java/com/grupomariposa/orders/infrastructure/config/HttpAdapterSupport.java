package com.grupomariposa.orders.infrastructure.config;

import com.grupomariposa.orders.application.port.out.ClientDirectory;
import com.grupomariposa.orders.application.port.out.ProductCatalog;
import com.grupomariposa.orders.infrastructure.http.Dependency;
import com.grupomariposa.orders.infrastructure.http.HttpDependenciesProperties;
import com.grupomariposa.orders.infrastructure.http.LookupExchange;
import com.grupomariposa.orders.infrastructure.http.ResilienceFactory;
import com.grupomariposa.orders.infrastructure.http.ResilientExecutor;
import com.grupomariposa.orders.infrastructure.http.RestClientFactory;
import com.grupomariposa.orders.infrastructure.http.RetryAfterParser;
import com.grupomariposa.orders.infrastructure.http.client.ClientResponseMapper;
import com.grupomariposa.orders.infrastructure.http.client.HttpClientDirectory;
import com.grupomariposa.orders.infrastructure.http.product.HttpProductCatalog;
import com.grupomariposa.orders.infrastructure.http.product.ProductResponseMapper;
import com.grupomariposa.orders.infrastructure.observability.ProcessingMetrics;

public record HttpAdapterSupport(
        RestClientFactory restClients,
        HttpDependenciesProperties properties,
        ResilienceFactory resilience,
        RetryAfterParser retryAfter,
        ProcessingMetrics metrics) {

    ClientDirectory clientDirectory() {
        return new HttpClientDirectory(restClients.create(properties.clients()),
                new LookupExchange(Dependency.CLIENTS_API, retryAfter),
                resilient(Dependency.CLIENTS_API), new ClientResponseMapper());
    }

    ProductCatalog productCatalog() {
        return new HttpProductCatalog(restClients.create(properties.products()),
                new LookupExchange(Dependency.PRODUCTS_API, retryAfter),
                resilient(Dependency.PRODUCTS_API), new ProductResponseMapper());
    }

    private ResilientExecutor resilient(final Dependency dependency) {
        final ResilientExecutor executor = resilience.create(dependency);
        resilience.retryOf(dependency).getEventPublisher()
                .onRetry(event -> metrics.retried(dependency.id()));
        return executor;
    }
}
