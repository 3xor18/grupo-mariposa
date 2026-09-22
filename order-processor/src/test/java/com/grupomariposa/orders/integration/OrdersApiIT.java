package com.grupomariposa.orders.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupomariposa.orders.application.ApplicationFixtures;
import com.grupomariposa.orders.application.outcome.ProcessingOutcome;
import com.grupomariposa.orders.application.port.in.ProcessOrderUseCase;
import com.grupomariposa.orders.support.Contracts;
import com.grupomariposa.orders.support.IntegrationTest;
import com.grupomariposa.orders.support.JwtTokens;
import com.grupomariposa.orders.support.OrderEvents;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class OrdersApiIT extends IntegrationTest {

    private static final String OPENAPI = "http/order-processor.openapi.yaml";
    private static final String PROBLEM_SCHEMA = "common/problem.schema.json";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProcessOrderUseCase useCase;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void should_return_order_matching_openapi_contract_with_decrypted_name() throws Exception {
        final String orderId = seedApprovedOrder();

        final JsonNode body = read(mockMvc.perform(authorized(get("/orders/" + orderId)))
                .andReturn(), 200);

        assertThat(Contracts.validateOpenApiSchema(OPENAPI, "Order", body)).isEmpty();
        assertThat(body.at("/client/name").asText()).isEqualTo("Distribuidora Central");
        assertThat(body.at("/totals/grandTotal").decimalValue()).isEqualByComparingTo("2100.11");
        assertThat(body.at("/lines/0/discountRate").decimalValue()).isEqualByComparingTo("0.03");
    }

    @Test
    void should_list_orders_matching_openapi_contract() throws Exception {
        final String orderId = seedApprovedOrder();

        final JsonNode body = read(mockMvc.perform(authorized(
                get("/orders").param("status", "APPROVED").param("market", "MX")
                        .param("size", "100"))).andReturn(), 200);

        assertThat(Contracts.validateOpenApiSchema(OPENAPI, "OrderPage", body)).isEmpty();
        assertThat(body.get("items")).extracting(item -> item.get("orderId").asText())
                .contains(orderId);
    }

    @Test
    void should_answer_problem_details_for_errors() throws Exception {
        final JsonNode missing = read(mockMvc.perform(authorized(get("/orders/ORD-NOPE-1")))
                .andReturn(), 404);
        final JsonNode invalid = read(mockMvc.perform(authorized(get("/orders")
                .param("page", "-1"))).andReturn(), 400);
        final JsonNode unauthorized = read(mockMvc.perform(get("/orders")).andReturn(), 401);
        final JsonNode forbidden = read(mockMvc.perform(get("/orders").with(jwt()))
                .andReturn(), 403);

        for (final JsonNode problem : List.of(missing, invalid, unauthorized, forbidden)) {
            assertThat(Contracts.validateEvent(PROBLEM_SCHEMA, problem)).isEmpty();
        }
        assertThat(missing.get("code").asText()).isEqualTo("ORDER_NOT_FOUND");
        assertThat(invalid.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(unauthorized.get("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(forbidden.get("code").asText()).isEqualTo("FORBIDDEN");
    }

    @Test
    void should_authorize_real_keycloak_style_tokens_by_realm_role_and_audience()
            throws Exception {
        JwtTokens.publishKeys(WIREMOCK);
        final String orderId = seedApprovedOrder();
        final String reader = JwtTokens.token(JwtTokens.AUDIENCE, List.of("orders-reader"));
        final String admin = JwtTokens.token(JwtTokens.AUDIENCE, List.of("orders-admin"));
        final String noRole = JwtTokens.token(JwtTokens.AUDIENCE, List.of("offline_access"));
        final String foreign = JwtTokens.token("another-api", List.of("orders-reader"));

        assertThat(status(get("/orders/" + orderId), reader)).isEqualTo(200);
        assertThat(status(get("/orders/" + orderId), noRole)).isEqualTo(403);
        assertThat(status(get("/orders/" + orderId), foreign)).isEqualTo(401);
        assertThat(status(get("/actuator/metrics"), reader)).isEqualTo(403);
        assertThat(status(get("/actuator/metrics"), admin)).isEqualTo(200);
        assertThat(mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getStatus())
                .isEqualTo(401);
    }

    @Test
    void should_expose_public_health_and_metrics() throws Exception {
        seedApprovedOrder();

        assertThat(read(mockMvc.perform(get("/health/live")).andReturn(), 200)
                .get("status").asText()).isEqualTo("UP");
        assertThat(read(mockMvc.perform(get("/health/ready")).andReturn(), 200)
                .get("status").asText()).isEqualTo("UP");
        assertThat(read(mockMvc.perform(get("/livez")).andReturn(), 200)
                .get("status").asText()).isEqualTo("UP");
        assertThat(read(mockMvc.perform(get("/readyz")).andReturn(), 200)
                .get("status").asText()).isEqualTo("UP");
        final String metrics = mockMvc.perform(get("/actuator/prometheus")).andReturn()
                .getResponse().getContentAsString();
        assertThat(metrics).contains("orders_processed_total", "orders_processing_latency",
                "outbox_pending");
    }

    private String seedApprovedOrder() {
        stubs.golden();
        final String orderId = OrderEvents.freshOrderId("API");
        final ProcessingOutcome outcome = useCase.process(
                ApplicationFixtures.command(orderId, "EVT-" + UUID.randomUUID(), 1));
        assertThat(outcome).isInstanceOf(ProcessingOutcome.Processed.class);
        return orderId;
    }

    private static MockHttpServletRequestBuilder authorized(
            final MockHttpServletRequestBuilder request) {
        return request.with(jwt()
                .jwt(token -> token.claim("realm_access", Map.of("roles",
                        List.of("orders-reader"))))
                .authorities(new SimpleGrantedAuthority("ROLE_orders-reader")));
    }

    private int status(final MockHttpServletRequestBuilder request, final String token)
            throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)).andReturn()
                .getResponse().getStatus();
    }

    private JsonNode read(final MvcResult result, final int status) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt() {
        return SecurityMockMvcRequestPostProcessors.jwt();
    }
}
