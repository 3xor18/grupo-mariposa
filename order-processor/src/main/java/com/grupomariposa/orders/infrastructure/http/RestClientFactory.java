package com.grupomariposa.orders.infrastructure.http;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public final class RestClientFactory implements AutoCloseable {

    private final RestClient.Builder builder;
    private final HttpDependenciesProperties properties;
    private final List<ClientHttpRequestInterceptor> interceptors;
    private final List<HttpClient> createdClients = new CopyOnWriteArrayList<>();

    public RestClientFactory(final RestClient.Builder builder,
                             final HttpDependenciesProperties properties,
                             final List<ClientHttpRequestInterceptor> interceptors) {
        this.builder = Objects.requireNonNull(builder, "builder");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.interceptors = List.copyOf(interceptors);
    }

    public RestClient create(final HttpDependenciesProperties.Endpoint endpoint) {
        final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        createdClients.add(httpClient);
        final JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder.clone()
                .baseUrl(endpoint.baseUrl().toString())
                .requestFactory(requestFactory)
                .requestInterceptors(list -> list.addAll(interceptors))
                .build();
    }

    @Override
    public void close() {
        createdClients.forEach(HttpClient::close);
        createdClients.clear();
    }
}
