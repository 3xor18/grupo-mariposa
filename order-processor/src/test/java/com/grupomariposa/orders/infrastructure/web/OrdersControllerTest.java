package com.grupomariposa.orders.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.grupomariposa.orders.application.error.PersistenceException;
import com.grupomariposa.orders.application.port.in.FindOrderQuery;
import com.grupomariposa.orders.application.port.in.ListOrdersQuery;
import com.grupomariposa.orders.application.query.OrderSearchCriteria;
import com.grupomariposa.orders.application.query.PageResult;
import com.grupomariposa.orders.domain.Markets;
import com.grupomariposa.orders.domain.model.OrderStatus;
import com.grupomariposa.orders.infrastructure.config.WebConfiguration;
import com.grupomariposa.orders.infrastructure.observability.CauseSanitizer;
import com.grupomariposa.orders.infrastructure.observability.TraceIds;
import com.grupomariposa.orders.infrastructure.persistence.PersistenceFixtures;
import com.grupomariposa.orders.infrastructure.web.security.WebSecurityProperties;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(OrdersController.class)
@Import({WebConfiguration.class, OrdersControllerTest.Support.class})
@TestPropertySource(properties = {
    "app.security.enabled=true",
    "spring.security.oauth2.resourceserver.jwt.audiences=order-processor",
    "app.security.allowed-origins=http://localhost:8090",
    "app.security.reader-role=orders-reader",
    "app.security.admin-role=orders-admin",
    "app.security.api-docs-enabled=false",
    "app.security.public-paths=/actuator/health/**,/livez,/readyz,/error",
    "app.security.cors-allowed-methods=GET,OPTIONS",
    "app.security.cors-allowed-headers=Authorization,traceparent",
    "app.api.problems.type-base=https://contracts.grupomariposa.dev/problems/",
    "app.api.orders.default-page-size=20",
    "app.api.orders.max-page-size=100",
    "app.api.orders.max-offset=10000"
})
class OrdersControllerTest {

    private static final String READER = "ROLE_orders-reader";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FindOrderQuery findOrder;

    @MockitoBean
    private ListOrdersQuery listOrders;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private TraceIds traceIds;

    @BeforeEach
    void setUp() {
        when(traceIds.currentTraceId()).thenReturn(Optional.of("4bf92f3577b34da6"));
    }

    @Test
    void should_require_a_token() throws Exception {
        mockMvc.perform(get("/orders/ORD-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").value("4bf92f3577b34da6"));
    }

    @Test
    void should_forbid_tokens_without_reader_role() throws Exception {
        mockMvc.perform(get("/orders").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void should_return_order_for_readers() throws Exception {
        when(findOrder.find("ORD-MX-000147"))
                .thenReturn(Optional.of(PersistenceFixtures.approvedOrder()));

        mockMvc.perform(reader(get("/orders/ORD-MX-000147")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.totals.grandTotal").value(2100.11))
                .andExpect(jsonPath("$.client.name").value("Distribuidora Central"))
                .andExpect(jsonPath("$.lines[0].discountRate").value(0.03))
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    void should_accept_admin_role() throws Exception {
        when(listOrders.list(any())).thenReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(get("/orders").with(jwt().authorities(
                        new SimpleGrantedAuthority("ROLE_orders-admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void should_answer_404_for_unknown_orders() throws Exception {
        when(findOrder.find("ORD-404")).thenReturn(Optional.empty());

        mockMvc.perform(reader(get("/orders/ORD-404")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value("/orders/ORD-404"));
    }

    @Test
    void should_reject_order_ids_that_could_be_injections() throws Exception {
        mockMvc.perform(reader(get("/orders/{id}", "{\"$gt\":\"\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("orderId"));
    }

    @Test
    void should_validate_every_listing_parameter() throws Exception {
        mockMvc.perform(reader(get("/orders").param("status", "LOST").param("market", "ARG")
                        .param("page", "-1").param("size", "500")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(4));
    }

    @Test
    void should_pass_typed_filters_to_the_query() throws Exception {
        final OrderSearchCriteria expected =
                new OrderSearchCriteria(OrderStatus.REJECTED, Markets.CO, 2, 5);
        when(listOrders.list(expected)).thenReturn(new PageResult<>(List.of(), 2, 5, 11));

        mockMvc.perform(reader(get("/orders").param("status", "REJECTED").param("market", "CO")
                        .param("page", "2").param("size", "5")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.totalElements").value(11));
    }

    @Test
    void should_hide_api_docs_when_disabled() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(jwt().authorities(
                        new SimpleGrantedAuthority(READER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_cap_deep_pagination() throws Exception {
        mockMvc.perform(reader(get("/orders").param("page", "101").param("size", "100")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("page"));
    }

    @Test
    void should_answer_503_when_store_is_unavailable() throws Exception {
        when(findOrder.find("ORD-DOWN"))
                .thenThrow(new PersistenceException("MongoDB read failed", null));

        mockMvc.perform(reader(get("/orders/ORD-DOWN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void should_hide_unexpected_errors_behind_500() throws Exception {
        when(findOrder.find("ORD-BOOM")).thenThrow(new IllegalStateException("db password"));

        mockMvc.perform(reader(get("/orders/ORD-BOOM")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(
                        "Unexpected error while processing the request"));
    }

    @Test
    void should_reject_unsupported_methods_and_paths() throws Exception {
        mockMvc.perform(reader(post("/orders")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mockMvc.perform(reader(get("/orders/ORD-1/lines")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private static MockHttpServletRequestBuilder reader(
            final MockHttpServletRequestBuilder request) {
        return request.with(jwt().authorities(new SimpleGrantedAuthority(READER)));
    }

    @TestConfiguration
    @EnableConfigurationProperties({WebSecurityProperties.class, OrdersApiProperties.class,
        ProblemProperties.class})
    static class Support {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        CauseSanitizer causeSanitizer() {
            return new CauseSanitizer();
        }
    }

    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt() {
        return SecurityMockMvcRequestPostProcessors.jwt();
    }
}
