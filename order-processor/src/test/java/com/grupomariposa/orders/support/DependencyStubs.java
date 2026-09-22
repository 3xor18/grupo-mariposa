package com.grupomariposa.orders.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

public final class DependencyStubs {

    public static final String TOKEN = "integration-token";
    public static final String TOKEN_PATH = "/realms/mariposa/protocol/openid-connect/token";
    private static final String CLIENTS_SPEC = "http/clients-api.openapi.yaml";
    private static final String PRODUCTS_SPEC = "http/products-api.openapi.yaml";
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer " + TOKEN;
    private static final String PROBLEM = "application/problem+json";
    private static final String RECOVERED = "recovered";

    private final WireMockServer server;

    public DependencyStubs(final WireMockServer server) {
        this.server = server;
    }

    public void token() {
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH)).willReturn(okJson(
                "{\"access_token\":\"" + TOKEN + "\",\"token_type\":\"Bearer\","
                        + "\"expires_in\":300}")));
    }

    public void client(final String clientId, final String market, final String segment,
                       final String regime, final String status) {
        final ObjectNode body = ((ObjectNode) Contracts.openApiExample(CLIENTS_SPEC,
                "/clients/{clientId}")).deepCopy();
        body.put("clientId", clientId).put("market", market).put("segment", segment)
                .put("taxRegime", regime).put("status", status);
        server.stubFor(get(urlPathEqualTo("/clients/" + clientId))
                .withHeader(AUTHORIZATION, equalTo(BEARER))
                .willReturn(okJson(body.toString())));
    }

    public void goldenClient(final String clientId) {
        client(clientId, "MX", "WHOLESALE", "GENERAL", "ACTIVE");
    }

    public void product(final String productId, final String market, final String status,
                        final String taxCategory) {
        server.stubFor(get(urlPathEqualTo("/products/" + productId))
                .withQueryParam("market", equalTo(market))
                .withHeader(AUTHORIZATION, equalTo(BEARER))
                .willReturn(okJson(productBody(productId, status, taxCategory).toString())));
    }

    public void productStatus(final String productId, final int status) {
        server.stubFor(get(urlPathEqualTo("/products/" + productId))
                .willReturn(aResponse().withStatus(status).withHeader("Content-Type", PROBLEM)
                        .withBody("{\"code\":\"FAULT\"}")));
    }

    public void productFailsThenRecovers(final String productId, final String market,
                                         final int failures, final int status) {
        final String scenario = "flaky-" + productId;
        String state = Scenario.STARTED;
        for (int attempt = 1; attempt <= failures; attempt++) {
            final String next = attempt == failures ? RECOVERED : "failure-" + attempt;
            server.stubFor(get(urlPathEqualTo("/products/" + productId))
                    .inScenario(scenario).whenScenarioStateIs(state)
                    .willReturn(aResponse().withStatus(status))
                    .willSetStateTo(next));
            state = next;
        }
        server.stubFor(get(urlPathEqualTo("/products/" + productId))
                .withQueryParam("market", equalTo(market))
                .inScenario(scenario).whenScenarioStateIs(RECOVERED)
                .willReturn(okJson(productBody(productId, "ACTIVE", "STANDARD").toString())));
    }

    public void productTimesOutOnce(final String productId, final String market,
                                    final int delayMillis) {
        final String scenario = "slow-" + productId;
        server.stubFor(get(urlPathEqualTo("/products/" + productId))
                .inScenario(scenario).whenScenarioStateIs(Scenario.STARTED)
                .willReturn(okJson(productBody(productId, "ACTIVE", "STANDARD").toString())
                        .withFixedDelay(delayMillis))
                .willSetStateTo(RECOVERED));
        server.stubFor(get(urlPathEqualTo("/products/" + productId))
                .withQueryParam("market", equalTo(market))
                .inScenario(scenario).whenScenarioStateIs(RECOVERED)
                .willReturn(okJson(productBody(productId, "ACTIVE", "STANDARD").toString())));
    }

    private static JsonNode productBody(final String productId, final String status,
                                        final String taxCategory) {
        final ObjectNode body = ((ObjectNode) Contracts.openApiExample(PRODUCTS_SPEC,
                "/products/{productId}")).deepCopy();
        return body.put("productId", productId).put("name", "Product " + productId)
                .put("sku", "SKU-" + productId).put("status", status)
                .put("taxCategory", taxCategory);
    }
}
