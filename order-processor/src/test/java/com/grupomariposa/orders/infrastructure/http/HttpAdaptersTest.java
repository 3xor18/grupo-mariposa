package com.grupomariposa.orders.infrastructure.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.grupomariposa.orders.application.error.ExternalPermanentException;
import com.grupomariposa.orders.application.error.ExternalTransientException;
import com.grupomariposa.orders.domain.Markets;
import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.ClientSegment;
import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.domain.model.ProductStatus;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.infrastructure.http.client.ClientResponseMapper;
import com.grupomariposa.orders.infrastructure.http.client.HttpClientDirectory;
import com.grupomariposa.orders.infrastructure.http.product.HttpProductCatalog;
import com.grupomariposa.orders.infrastructure.http.product.ProductResponseMapper;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

class HttpAdaptersTest {

    private static final String CLIENT_BODY = """
            {"clientId":"CLI-99821","name":"Distribuidora Central","status":"ACTIVE",
             "segment":"WHOLESALE","taxRegime":"GENERAL","market":"MX","extra":"ignored"}""";
    private static final String PRODUCT_BODY = """
            {"productId":"PRD-001","name":"Bebida 600 ml","sku":"BEB-600-PET",
             "status":"ACTIVE","taxCategory":"STANDARD"}""";

    @RegisterExtension
    static final WireMockExtension API = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort()).build();

    private HttpClientDirectory clients;
    private HttpProductCatalog products;
    private CircuitBreakerRegistry circuitBreakers;
    private RetryRegistry retries;

    @BeforeEach
    void setUp() {
        circuitBreakers = CircuitBreakerRegistry.ofDefaults();
        retries = RetryRegistry.ofDefaults();
        final HttpDependenciesProperties properties = properties();
        final ResilienceFactory resilience = new ResilienceFactory(properties.resilience(),
                retries, circuitBreakers, BulkheadRegistry.ofDefaults());
        final RestClientFactory restClients =
                new RestClientFactory(RestClient.builder(), properties, List.of());
        final RetryAfterParser retryAfter = new RetryAfterParser(Clock.systemUTC());
        clients = new HttpClientDirectory(restClients.create(properties.clients()),
                new LookupExchange(Dependency.CLIENTS_API, retryAfter),
                resilience.create(Dependency.CLIENTS_API), new ClientResponseMapper());
        products = new HttpProductCatalog(restClients.create(properties.products()),
                new LookupExchange(Dependency.PRODUCTS_API, retryAfter),
                resilience.create(Dependency.PRODUCTS_API), new ProductResponseMapper());
    }

    @Test
    void should_map_found_client_ignoring_unknown_fields() {
        API.stubFor(get("/clients/CLI-99821").willReturn(okJson(CLIENT_BODY)));

        final Lookup<ClientProfile> client = clients.findClient("CLI-99821");

        assertThat(client.value()).hasValueSatisfying(profile -> {
            assertThat(profile.segment()).isEqualTo(ClientSegment.WHOLESALE);
            assertThat(profile.market()).isEqualTo(Markets.MX);
        });
    }

    @Test
    void should_map_found_product_for_market() {
        API.stubFor(get(urlPathEqualTo("/products/PRD-001")).withQueryParam("market",
                equalTo("MX"))
                .willReturn(okJson(PRODUCT_BODY)));

        final Lookup<ProductProfile> product = products.findProduct("PRD-001", Markets.MX);

        assertThat(product.value()).hasValueSatisfying(profile -> {
            assertThat(profile.status()).isEqualTo(ProductStatus.ACTIVE);
            assertThat(profile.taxCategory()).isEqualTo(TaxCategory.STANDARD);
        });
    }

    @Test
    void should_read_client_version_from_body_before_etag() {
        API.stubFor(get("/clients/CLI-99821").willReturn(okJson(
                CLIENT_BODY.replace("\"extra\"", "\"version\":7,\"extra\""))
                .withHeader("ETag", "\"3\"")));

        assertThat(clients.findVersionedClient("CLI-99821").value())
                .hasValueSatisfying(client -> assertThat(client.version()).isEqualTo(7L));
    }

    @Test
    void should_read_product_version_from_weak_etag_when_body_has_none() {
        API.stubFor(get(urlPathEqualTo("/products/PRD-001")).willReturn(okJson(PRODUCT_BODY)
                .withHeader("ETag", "W/\"12\"")));

        assertThat(products.findVersionedProduct("PRD-001", Markets.MX).value())
                .hasValueSatisfying(product -> assertThat(product.version()).isEqualTo(12L));
    }

    @Test
    void should_treat_404_as_business_not_found() {
        API.stubFor(get("/clients/CLI-404").willReturn(aResponse().withStatus(404)));

        assertThat(clients.findClient("CLI-404")).isEqualTo(Lookup.notFound());
        API.verify(1, getRequestedFor(urlPathEqualTo("/clients/CLI-404")));
    }

    @Test
    void should_treat_any_2xx_with_body_as_found() {
        API.stubFor(get("/clients/CLI-203").willReturn(aResponse().withStatus(203)
                .withHeader("Content-Type", "application/json").withBody(CLIENT_BODY)));

        assertThat(clients.findClient("CLI-203").value()).isPresent();
    }

    @Test
    void should_close_created_http_clients() {
        final RestClientFactory factory = new RestClientFactory(RestClient.builder(),
                properties(), List.of());
        factory.create(properties().clients());

        factory.close();
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 429, 500, 502, 503, 504})
    void should_retry_and_then_fail_transient_statuses(final int status) {
        API.stubFor(get("/clients/CLI-DOWN").willReturn(aResponse().withStatus(status)));

        assertThatThrownBy(() -> clients.findClient("CLI-DOWN"))
                .isInstanceOf(ExternalTransientException.class)
                .hasMessage("clients-api responded " + status);
        assertThat(retries.retry(Dependency.CLIENTS_API.id()).getMetrics()
                .getNumberOfFailedCallsWithRetryAttempt()).isOne();
        API.verify(moreThanOrExactly(3), getRequestedFor(urlPathEqualTo("/clients/CLI-DOWN")));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 409, 422})
    void should_fail_fast_on_permanent_statuses(final int status) {
        API.stubFor(get("/clients/CLI-BAD").willReturn(aResponse().withStatus(status)));

        assertThatThrownBy(() -> clients.findClient("CLI-BAD"))
                .isInstanceOf(ExternalPermanentException.class);
        assertThat(retries.retry(Dependency.CLIENTS_API.id()).getMetrics()
                .getNumberOfFailedCallsWithoutRetryAttempt()).isOne();
        assertThat(retries.retry(Dependency.CLIENTS_API.id()).getMetrics()
                .getNumberOfFailedCallsWithRetryAttempt()).isZero();
    }

    @Test
    void should_recover_when_transient_failures_stop() {
        API.stubFor(get("/clients/CLI-FLAKY").inScenario("flaky")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("second"));
        API.stubFor(get("/clients/CLI-FLAKY").inScenario("flaky").whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0"))
                .willSetStateTo("ok"));
        API.stubFor(get("/clients/CLI-FLAKY").inScenario("flaky").whenScenarioStateIs("ok")
                .willReturn(okJson(CLIENT_BODY)));

        assertThat(clients.findClient("CLI-FLAKY").value()).isPresent();
    }

    @Test
    void should_treat_timeouts_as_transient() {
        API.stubFor(get("/clients/CLI-SLOW").willReturn(okJson(CLIENT_BODY)
                .withFixedDelay(1500)));

        assertThatThrownBy(() -> clients.findClient("CLI-SLOW"))
                .isInstanceOf(ExternalTransientException.class)
                .hasMessage("clients-api unreachable or timed out");
    }

    @Test
    void should_treat_connection_faults_as_transient() {
        API.stubFor(get("/clients/CLI-RESET").willReturn(aResponse()
                .withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> clients.findClient("CLI-RESET"))
                .isInstanceOf(ExternalTransientException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{not json",
        "{\"clientId\":\"CLI-1\",\"status\":\"SUSPENDED\",\"segment\":\"RETAIL\","
                + "\"taxRegime\":\"GENERAL\",\"market\":\"MX\"}",
        "{\"clientId\":\"CLI-1\",\"status\":\"ACTIVE\",\"segment\":\"RETAIL\","
                + "\"taxRegime\":\"GENERAL\",\"market\":\"ARG\"}",
        "{\"clientId\":\" \",\"status\":\"ACTIVE\"}",
        "null"
    })
    void should_treat_invalid_bodies_as_permanent(final String body) {
        API.stubFor(get("/clients/CLI-ODD").willReturn(okJson(body)));

        assertThatThrownBy(() -> clients.findClient("CLI-ODD"))
                .isInstanceOf(ExternalPermanentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"productId\":\"PRD-1\",\"status\":\"ACTIVE\",\"taxCategory\":\"LUXURY\"}",
        "{\"status\":\"ACTIVE\",\"taxCategory\":\"STANDARD\"}"
    })
    void should_treat_invalid_product_bodies_as_permanent(final String body) {
        API.stubFor(get(urlPathEqualTo("/products/PRD-ODD")).willReturn(okJson(body)));

        assertThatThrownBy(() -> products.findProduct("PRD-ODD", Markets.PE))
                .isInstanceOf(ExternalPermanentException.class);
    }

    @Test
    void should_fail_fast_as_transient_when_circuit_is_open() {
        circuitBreakers.circuitBreaker(Dependency.PRODUCTS_API.id()).transitionToOpenState();

        assertThatThrownBy(() -> products.findProduct("PRD-001", Markets.MX))
                .isInstanceOf(ExternalTransientException.class)
                .hasMessage("products-api circuit breaker is open");
        API.verify(0, getRequestedFor(urlPathEqualTo("/products/PRD-001")));
    }

    static HttpDependenciesProperties properties() {
        final HttpDependenciesProperties.Endpoint endpoint =
                new HttpDependenciesProperties.Endpoint(URI.create(API.baseUrl()));
        return new HttpDependenciesProperties(endpoint, endpoint, Duration.ofMillis(500),
                Duration.ofMillis(1000), new HttpDependenciesProperties.OAuth(false, "test",
                        URI.create(API.baseUrl()), "order-processor"),
                new HttpDependenciesProperties.Resilience(3, Duration.ofMillis(5), 2.0, 0.5,
                        Duration.ofMillis(50), 20, 10, 50f, Duration.ofSeconds(10), 3, 32,
                        Duration.ofMillis(100)));
    }
}
