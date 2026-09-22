package com.grupomariposa.orders.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.grupomariposa.orders.domain.model.Order;
import com.grupomariposa.orders.infrastructure.persistence.PersistenceFixtures;
import com.grupomariposa.orders.support.Contracts;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class OutboxPayloadFactoryTest {

    private static final String SCHEMA = "events/orders.processed.v1.schema.json";

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private final OutboxPayloadFactory factory = new OutboxPayloadFactory(objectMapper);

    static Stream<Order> decided() {
        return Stream.of(PersistenceFixtures.approvedOrder(), PersistenceFixtures.rejectedOrder());
    }

    @ParameterizedTest
    @MethodSource("decided")
    void should_produce_payload_valid_against_published_contract(final Order order)
            throws Exception {
        final JsonNode payload = objectMapper.readTree(factory.create(order, "OUT-1"));

        assertThat(Contracts.validateEvent(SCHEMA, payload)).isEmpty();
        assertThat(payload.get("eventId").asText()).isEqualTo("OUT-1");
        assertThat(payload.get("eventVersion").asInt()).isEqualTo(order.eventVersion());
        assertThat(payload.get("occurredAt").asText()).isEqualTo("2026-09-18T15:42:12Z");
    }

    @Test
    void should_keep_two_decimals_and_null_reason_for_approvals() throws Exception {
        final String json = factory.create(PersistenceFixtures.approvedOrder(), "OUT-2");

        assertThat(json).contains("\"grandTotal\":2100.11", "\"grossSubtotal\":1836.00",
                "\"reason\":null", "\"violations\":[]");
        assertThat(json).doesNotContain("Distribuidora");
    }

    @Test
    void should_list_violations_for_rejections() throws Exception {
        final JsonNode payload = objectMapper.readTree(
                factory.create(PersistenceFixtures.rejectedOrder(), "OUT-3"));

        assertThat(payload.get("reason").asText()).isEqualTo("CLIENT_NOT_FOUND");
        assertThat(payload.at("/violations/0/code").asText()).isEqualTo("CLIENT_NOT_FOUND");
        assertThat(payload.at("/totals/grandTotal").decimalValue()).isZero();
    }

    @Test
    void should_fail_loudly_when_serialization_breaks() throws Exception {
        final ObjectMapper broken = mock(ObjectMapper.class);
        when(broken.writeValueAsString(any())).thenThrow(new JsonProcessingException("x") { });

        assertThatIllegalStateException().isThrownBy(() -> new OutboxPayloadFactory(broken)
                .create(PersistenceFixtures.approvedOrder(), "OUT-4"));
    }
}
