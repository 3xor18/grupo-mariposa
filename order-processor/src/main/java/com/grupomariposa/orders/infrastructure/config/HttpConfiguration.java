package com.grupomariposa.orders.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupomariposa.orders.application.port.out.ClientDirectory;
import com.grupomariposa.orders.application.port.out.ProductCatalog;
import com.grupomariposa.orders.infrastructure.cache.CachingProductCatalog;
import com.grupomariposa.orders.infrastructure.cache.ProductCacheProperties;
import com.grupomariposa.orders.infrastructure.http.HttpDependenciesProperties;
import com.grupomariposa.orders.infrastructure.http.ResilienceFactory;
import com.grupomariposa.orders.infrastructure.http.RestClientFactory;
import com.grupomariposa.orders.infrastructure.http.RetryAfterParser;
import com.grupomariposa.orders.infrastructure.observability.ProcessingMetrics;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class HttpConfiguration {

    private static final String SERVICE_PRINCIPAL = "order-processor";
    private static final String SERVICE_ROLE = "ROLE_SERVICE";

    @Bean
    public RetryAfterParser retryAfterParser(final Clock clock) {
        return new RetryAfterParser(clock);
    }

    @Bean
    public ResilienceFactory resilienceFactory(final HttpDependenciesProperties properties,
                                               final RetryRegistry retries,
                                               final CircuitBreakerRegistry circuitBreakers,
                                               final BulkheadRegistry bulkheads) {
        return new ResilienceFactory(properties.resilience(), retries, circuitBreakers,
                bulkheads);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.http.oauth", name = "enabled", havingValue = "true")
    public OAuth2AuthorizedClientManager serviceAuthorizedClientManager(
            final ClientRegistrationRepository registrations,
            final OAuth2AuthorizedClientService authorizedClients) {
        final AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations,
                        authorizedClients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials().build());
        return manager;
    }

    @Bean
    public RestClientFactory restClientFactory(
            final RestClient.Builder builder, final HttpDependenciesProperties properties,
            final ObjectProvider<OAuth2AuthorizedClientManager> managers,
            final ObjectProvider<OAuth2AuthorizedClientService> authorizedClients) {
        final Optional<ClientHttpRequestInterceptor> authentication =
                Optional.ofNullable(managers.getIfAvailable()).map(manager ->
                        oauthInterceptor(manager, authorizedClients.getObject(),
                                properties.oauth().registrationId()));
        return new RestClientFactory(builder, properties, authentication);
    }

    @Bean
    public HttpAdapterSupport httpAdapterSupport(final RestClientFactory restClients,
                                                 final HttpDependenciesProperties properties,
                                                 final ResilienceFactory resilience,
                                                 final RetryAfterParser retryAfter,
                                                 final ProcessingMetrics metrics) {
        return new HttpAdapterSupport(restClients, properties, resilience, retryAfter, metrics);
    }

    @Bean
    public ClientDirectory clientDirectory(final HttpAdapterSupport support) {
        return support.clientDirectory();
    }

    @Bean
    public ProductCatalog productCatalog(final HttpAdapterSupport support,
                                         final ProductCacheProperties cache,
                                         final ObjectProvider<StringRedisTemplate> redis,
                                         final ObjectMapper objectMapper,
                                         final MeterRegistry registry) {
        final ProductCatalog http = support.productCatalog();
        if (!cache.enabled()) {
            return http;
        }
        return new CachingProductCatalog(http, redis.getObject(), objectMapper, cache, registry);
    }

    private static ClientHttpRequestInterceptor oauthInterceptor(
            final OAuth2AuthorizedClientManager manager,
            final OAuth2AuthorizedClientService authorizedClients, final String registrationId) {
        final OAuth2ClientHttpRequestInterceptor interceptor =
                new OAuth2ClientHttpRequestInterceptor(manager);
        final Authentication principal = new AnonymousAuthenticationToken(SERVICE_PRINCIPAL,
                SERVICE_PRINCIPAL, AuthorityUtils.createAuthorityList(SERVICE_ROLE));
        interceptor.setClientRegistrationIdResolver(request -> registrationId);
        interceptor.setPrincipalResolver(request -> principal);
        interceptor.setAuthorizationFailureHandler(
                OAuth2ClientHttpRequestInterceptor.authorizationFailureHandler(authorizedClients));
        return interceptor;
    }
}
