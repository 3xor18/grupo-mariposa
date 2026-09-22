package com.grupomariposa.orders.infrastructure.http;

import java.net.http.HttpClient;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public final class RestClientFactory {

    private final RestClient.Builder builder;
    private final HttpDependenciesProperties properties;
    private final Optional<ClientHttpRequestInterceptor> authentication;

    public RestClientFactory(final RestClient.Builder builder,
                             final HttpDependenciesProperties properties,
                             final Optional<ClientHttpRequestInterceptor> authentication) {
        this.builder = Objects.requireNonNull(builder, "builder");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.authentication = Objects.requireNonNull(authentication, "authentication");
    }

    public RestClient create(final HttpDependenciesProperties.Endpoint endpoint) {
        final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        final JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        final RestClient.Builder configured = builder.clone()
                .baseUrl(endpoint.baseUrl().toString())
                .requestFactory(requestFactory);
        authentication.ifPresent(interceptor ->
                configured.requestInterceptors(list -> list.add(interceptor)));
        return configured.build();
    }
}
