package com.grupomariposa.orders.infrastructure.config;

import com.grupomariposa.orders.application.port.out.ClientDirectory;
import com.grupomariposa.orders.application.port.out.ProductCatalog;
import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.infrastructure.cache.CachingClientDirectory;
import com.grupomariposa.orders.infrastructure.cache.CachingProductCatalog;
import com.grupomariposa.orders.infrastructure.cache.ClientCacheProperties;
import com.grupomariposa.orders.infrastructure.cache.ProductCacheProperties;
import com.grupomariposa.orders.infrastructure.cache.VersionedCache;
import com.grupomariposa.orders.infrastructure.http.HttpDependenciesProperties;
import com.grupomariposa.orders.infrastructure.http.ResilienceFactory;
import com.grupomariposa.orders.infrastructure.http.RestClientFactory;
import com.grupomariposa.orders.infrastructure.http.RetryAfterParser;
import com.grupomariposa.orders.infrastructure.http.client.HttpClientDirectory;
import com.grupomariposa.orders.infrastructure.http.product.HttpProductCatalog;
import com.grupomariposa.orders.infrastructure.observability.ProcessingMetrics;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class HttpConfiguration {

    private static final String APPLICATION_NAME = "${spring.application.name}";
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
    public OAuth2ClientHttpRequestInterceptor serviceTokenInterceptor(
            final HttpDependenciesProperties properties, final EnvironmentSecrets secrets,
            @Value(APPLICATION_NAME) final String applicationName) {
        final HttpDependenciesProperties.OAuth oauth = properties.oauth();
        final ClientRegistrationRepository registrations =
                new InMemoryClientRegistrationRepository(
                        ClientRegistration.withRegistrationId(oauth.registrationId())
                                .clientId(oauth.clientId())
                                .clientSecret(secrets.required(
                                        EnvironmentSecrets.OAUTH_CLIENT_SECRET))
                                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                                .tokenUri(oauth.tokenUri().toString())
                                .build());
        final OAuth2AuthorizedClientService authorizedClients =
                new InMemoryOAuth2AuthorizedClientService(registrations);
        final AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations,
                        authorizedClients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials().build());
        return oauthInterceptor(manager, authorizedClients, oauth.registrationId(),
                applicationName);
    }

    @Bean(destroyMethod = "close")
    public RestClientFactory restClientFactory(
            final RestClient.Builder builder, final HttpDependenciesProperties properties,
            final ObjectProvider<OAuth2ClientHttpRequestInterceptor> tokenInterceptor) {
        final List<ClientHttpRequestInterceptor> interceptors =
                tokenInterceptor.stream().map(ClientHttpRequestInterceptor.class::cast).toList();
        return new RestClientFactory(builder, properties, interceptors);
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
    public ClientDirectory clientDirectory(final HttpAdapterSupport support,
                                           final ClientCacheProperties cache,
                                           final VersionedCache<ClientProfile> clientCache) {
        final HttpClientDirectory http = support.clientDirectory();
        return cache.enabled() ? new CachingClientDirectory(http, clientCache) : http;
    }

    @Bean
    public ProductCatalog productCatalog(final HttpAdapterSupport support,
                                         final ProductCacheProperties cache,
                                         final VersionedCache<ProductProfile> productCache) {
        final HttpProductCatalog http = support.productCatalog();
        return cache.enabled() ? new CachingProductCatalog(http, productCache) : http;
    }

    private static OAuth2ClientHttpRequestInterceptor oauthInterceptor(
            final OAuth2AuthorizedClientManager manager,
            final OAuth2AuthorizedClientService authorizedClients, final String registrationId,
            final String principalName) {
        final OAuth2ClientHttpRequestInterceptor interceptor =
                new OAuth2ClientHttpRequestInterceptor(manager);
        final Authentication principal = new AnonymousAuthenticationToken(principalName,
                principalName, AuthorityUtils.createAuthorityList(SERVICE_ROLE));
        interceptor.setClientRegistrationIdResolver(request -> registrationId);
        interceptor.setPrincipalResolver(request -> principal);
        interceptor.setAuthorizationFailureHandler(
                OAuth2ClientHttpRequestInterceptor.authorizationFailureHandler(authorizedClients));
        return interceptor;
    }
}
