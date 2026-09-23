package com.grupomariposa.orders.infrastructure.config;

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
import java.util.Objects;

public final class HttpAdapterSupport {

    private final RestClientFactory restClients;
    private final HttpDependenciesProperties properties;
    private final ResilienceFactory resilience;
    private final RetryAfterParser retryAfter;
    private final ProcessingMetrics metrics;

    public HttpAdapterSupport(final RestClientFactory restClients,
                              final HttpDependenciesProperties properties,
                              final ResilienceFactory resilience,
                              final RetryAfterParser retryAfter,
                              final ProcessingMetrics metrics) {
        this.restClients = Objects.requireNonNull(restClients, "restClients");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.resilience = Objects.requireNonNull(resilience, "resilience");
        this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    HttpClientDirectory clientDirectory() {
        return new HttpClientDirectory(restClients.create(properties.clients()),
                new LookupExchange(Dependency.CLIENTS_API, retryAfter),
                resilient(Dependency.CLIENTS_API), new ClientResponseMapper());
    }

    HttpProductCatalog productCatalog() {
        return new HttpProductCatalog(restClients.create(properties.products()),
                new LookupExchange(Dependency.PRODUCTS_API, retryAfter),
                resilient(Dependency.PRODUCTS_API), new ProductResponseMapper());
    }

    private ResilientExecutor resilient(final Dependency dependency) {
        final ResilientExecutor executor = resilience.create(dependency);
        executor.retry().getEventPublisher().onRetry(event -> metrics.retried(dependency.id()));
        return executor;
    }
}
