package com.grupomariposa.orders.infrastructure.kafka.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.grupomariposa.orders.application.error.ErrorCategory;
import com.grupomariposa.orders.support.Contracts;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class OrderMessageReaderTest {

    private final OrderMessageReader reader = new OrderMessageReader();

    @Test
    void should_read_contract_example() {
        final OrderCreatedMessage message = reader.read(
                Contracts.bytes("examples/orders.created.v1.approved.json"));

        assertThat(message.orderId()).isEqualTo("ORD-MX-000147");
        assertThat(message.items()).hasSize(2);
        assertThat(message.items().getFirst().quantity()).isEqualTo(24L);
        assertThat(message.items().getFirst().unitPrice()).isEqualByComparingTo("35.5");
    }

    @Test
    void should_ignore_unknown_fields() {
        final OrderCreatedMessage message = reader.read(bytes(
                "{\"eventId\":\"E1\",\"orderId\":\"O1\",\"newField\":{\"a\":1},"
                        + "\"items\":[{\"productId\":\"P\",\"quantity\":1,\"unitPrice\":0.1,"
                        + "\"future\":true}]}"));

        assertThat(message.items().getFirst().unitPrice()).isEqualTo(new BigDecimal("0.1"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "fractional quantity|{'orderId':'O1','eventId':'E1','items':[{'quantity':24.5}]}"
                + "|items[0].quantity",
        "textual quantity|{'orderId':'O1','eventId':'E1','items':[{'quantity':'24'}]}"
                + "|items[0].quantity",
        "numeric identifier|{'orderId':'O1','eventId':'E1','clientId':42}|clientId",
        "fractional version|{'orderId':'O1','eventId':'E1','eventVersion':1.5}|eventVersion",
        "textual price|{'orderId':'O1','eventId':'E1','items':[{'unitPrice':'1.0'}]}"
                + "|items[0].unitPrice",
        "items not array|{'orderId':'O1','eventId':'E1','items':{}}|items"
    })
    void should_reject_schema_violations_as_deserialization(final String name,
                                                            final String json,
                                                            final String path) {
        assertThatThrownBy(() -> reader.read(bytes(json.replace('\'', '"'))))
                .isInstanceOfSatisfying(RecordProcessingFailure.class, failure -> {
                    assertThat(failure.category()).isEqualTo(ErrorCategory.DESERIALIZATION);
                    assertThat(failure.getMessage()).endsWith(path);
                    assertThat(failure.ids()).isEqualTo(new MessageIds("O1", "E1"));
                    assertThat(failure.command()).isEmpty();
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{broken", "[1,2]", "\"text\"", "{\"a\":1} trailing"})
    void should_reject_unreadable_payloads_without_ids(final String payload) {
        assertThatThrownBy(() -> reader.read(bytes(payload)))
                .isInstanceOfSatisfying(RecordProcessingFailure.class, failure -> {
                    assertThat(failure.category()).isEqualTo(ErrorCategory.DESERIALIZATION);
                    assertThat(failure.ids()).isEqualTo(MessageIds.UNKNOWN);
                });
    }

    @Test
    void should_reject_null_payload() {
        assertThatThrownBy(() -> reader.read(null))
                .isInstanceOf(RecordProcessingFailure.class)
                .hasMessage("Payload is empty");
    }

    @Test
    void should_only_extract_textual_ids() {
        assertThatThrownBy(() -> reader.read(bytes("{\"orderId\":7,\"eventId\":null}")))
                .isInstanceOfSatisfying(RecordProcessingFailure.class, failure ->
                        assertThat(failure.ids().orderId()).isNull());
    }

    private static byte[] bytes(final String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
