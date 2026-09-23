package com.grupomariposa.orders.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupomariposa.orders.application.ApplicationFixtures;
import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.outcome.ProcessingOutcome;
import com.grupomariposa.orders.application.port.in.ProcessOrderUseCase;
import com.grupomariposa.orders.support.Contracts;
import com.grupomariposa.orders.support.IntegrationTest;
import com.grupomariposa.orders.support.OrderEvents;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class TaxRatesApiIT extends IntegrationTest {

    private static final String OPENAPI = "http/order-processor.openapi.yaml";
    private static final String PROBLEM_SCHEMA = "common/problem.schema.json";
    private static final String TAX_RATES = "/tax-rates";
    private static final String ADMIN_ROLE = "orders-admin";
    private static final String READER_ROLE = "orders-reader";
    private static final String PROPOSER = "olga";
    private static final String APPROVER = "auditor";
    private static final Duration LEAD_TIME = Duration.ofDays(30);
    private static final Duration MARGIN = Duration.ofHours(1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProcessOrderUseCase useCase;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void should_apply_an_approved_rate_only_to_orders_that_occur_after_it_starts()
            throws Exception {
        final Instant validFrom = Instant.now().plus(LEAD_TIME).truncatedTo(ChronoUnit.SECONDS);
        final JsonNode proposed = send(propose("MX", "STANDARD", "0.2", validFrom), PROPOSER,
                ADMIN_ROLE, 201);
        final String id = proposed.get("id").asText();

        final JsonNode sameUser = send(post(approvePath(id)), PROPOSER, ADMIN_ROLE, 403);
        final JsonNode approved = send(post(approvePath(id)), APPROVER, ADMIN_ROLE, 200);

        assertThat(Contracts.validateOpenApiSchema(OPENAPI, "TaxRate", proposed)).isEmpty();
        assertThat(Contracts.validateOpenApiSchema(OPENAPI, "TaxRate", approved)).isEmpty();
        assertThat(Contracts.validateEvent(PROBLEM_SCHEMA, sameUser)).isEmpty();
        assertThat(sameUser.get("code").asText()).isEqualTo("FOUR_EYES_REQUIRED");
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.get("reviewedBy").asText()).isEqualTo(APPROVER);
        final JsonNode after = processedOrder(validFrom.plus(MARGIN));
        final JsonNode before = processedOrder(validFrom.minus(MARGIN));
        assertThat(Contracts.validateOpenApiSchema(OPENAPI, "Order", after)).isEmpty();
        assertThat(after.at("/lines/0/taxRate").decimalValue()).isEqualByComparingTo("0.2");
        assertThat(Instant.parse(after.get("taxRateEffectiveFrom").asText()))
                .isEqualTo(validFrom);
        assertThat(before.at("/lines/0/taxRate").decimalValue()).isEqualByComparingTo("0.16");
        assertThat(Instant.parse(before.get("taxRateEffectiveFrom").asText()))
                .isBefore(validFrom);
    }

    @Test
    void should_list_and_reject_proposals_and_answer_problems_for_invalid_requests()
            throws Exception {
        final Instant validFrom = Instant.now().plus(LEAD_TIME).truncatedTo(ChronoUnit.SECONDS);
        final String id = send(propose("CO", "REDUCED", "0.06", validFrom), PROPOSER,
                ADMIN_ROLE, 201).get("id").asText();

        final JsonNode pending = send(get(TAX_RATES).param("market", "CO")
                .param("category", "REDUCED").param("status", "PROPOSED"), APPROVER,
                ADMIN_ROLE, 200);
        final JsonNode rejected = send(post(TAX_RATES + "/" + id + "/reject"), APPROVER,
                ADMIN_ROLE, 200);
        final JsonNode reviewedTwice = send(post(approvePath(id)), APPROVER, ADMIN_ROLE, 409);
        final JsonNode missing = send(post(approvePath("nope")), APPROVER, ADMIN_ROLE, 404);
        final JsonNode invalid = send(propose("CO", "REDUCED", "1.5", validFrom), PROPOSER,
                ADMIN_ROLE, 400);
        final JsonNode badFilter = send(get(TAX_RATES).param("status", "LATER"), APPROVER,
                ADMIN_ROLE, 400);
        final JsonNode unreadable = send(post(TAX_RATES).contentType(MediaType.APPLICATION_JSON)
                .content("{"), PROPOSER, ADMIN_ROLE, 400);
        final JsonNode reader = send(get(TAX_RATES), APPROVER, READER_ROLE, 403);

        assertThat(pending.findValuesAsText("id")).contains(id);
        for (final JsonNode rate : pending) {
            assertThat(Contracts.validateOpenApiSchema(OPENAPI, "TaxRate", rate)).isEmpty();
        }
        assertThat(rejected.get("status").asText()).isEqualTo("REJECTED");
        for (final JsonNode problem : List.of(reviewedTwice, missing, invalid, badFilter,
                unreadable, reader)) {
            assertThat(Contracts.validateEvent(PROBLEM_SCHEMA, problem)).isEmpty();
        }
        assertThat(reviewedTwice.get("code").asText()).isEqualTo("TAX_RATE_CONFLICT");
        assertThat(missing.get("code").asText()).isEqualTo("TAX_RATE_NOT_FOUND");
        assertThat(invalid.at("/errors/0/field").asText()).isEqualTo("rate");
        assertThat(reader.get("code").asText()).isEqualTo("FORBIDDEN");
    }

    private JsonNode processedOrder(final Instant occurredAt) throws Exception {
        stubs.golden();
        final OrderCommand golden = ApplicationFixtures.command(
                OrderEvents.freshOrderId("TAX"), "EVT-" + UUID.randomUUID(), 1);
        final OrderCommand command = new OrderCommand(golden.eventId(), golden.eventVersion(),
                golden.orderId(), golden.market(), golden.currency(), golden.clientId(),
                golden.channel(), occurredAt, golden.items(), golden.reception());
        assertThat(useCase.process(command)).isInstanceOf(ProcessingOutcome.Processed.class);
        return send(get("/orders/" + command.orderId()), APPROVER, READER_ROLE, 200);
    }

    private MockHttpServletRequestBuilder propose(final String market, final String category,
                                                  final String rate, final Instant validFrom)
            throws Exception {
        return post(TAX_RATES).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("market", market,
                        "category", category, "rate", new BigDecimal(rate),
                        "validFrom", validFrom.toString(), "changeReason", "Integration test")));
    }

    private static String approvePath(final String id) {
        return TAX_RATES + "/" + id + "/approve";
    }

    private JsonNode send(final MockHttpServletRequestBuilder request, final String username,
                          final String role, final int status) throws Exception {
        final MockHttpServletResponse response = mockMvc.perform(request.with(
                        SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(token -> token.claim("preferred_username", username)
                                        .claim("realm_access", Map.of("roles", List.of(role))))
                                .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(status);
        return objectMapper.readTree(response.getContentAsString());
    }
}
