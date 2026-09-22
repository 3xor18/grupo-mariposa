package com.grupomariposa.orders.integration;

import static com.grupomariposa.orders.support.TopicProbe.header;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupomariposa.orders.support.Contracts;
import com.grupomariposa.orders.support.IntegrationTest;
import com.grupomariposa.orders.support.KafkaTestClient;
import com.grupomariposa.orders.support.OrderEvents;
import com.grupomariposa.orders.support.TopicProbe;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

class OrderProcessingIT extends IntegrationTest {

    private static final String PROCESSED_SCHEMA = "events/orders.processed.v1.schema.json";
    private static final Duration SHORT = Duration.ofSeconds(3);

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void should_approve_golden_order_and_publish_contract_compliant_event() throws IOException {
        stubs.golden();
        final OrderEvents event = OrderEvents.goldenWithFreshIds("GOLDEN");

        publish(event);

        final Document order = awaitOrder(event.orderId(), "APPROVED");
        final Document totals = order.get("totals", Document.class);
        assertThat(totals.get("grandTotal", Decimal128.class).bigDecimalValue())
                .isEqualByComparingTo("2100.11");
        assertThat(totals.get("tax", Decimal128.class).bigDecimalValue())
                .isEqualByComparingTo("289.67");
        final String storedName = order.get("client", Document.class).getString("encryptedName");
        assertThat(storedName).startsWith("k1:").doesNotContain("Distribuidora");
        try (TopicProbe processed = probe(ORDERS_PROCESSED)) {
            final ConsumerRecord<String, byte[]> consumerRecord =
                    processed.awaitKey(event.orderId(), 1).getFirst();
            final JsonNode payload = objectMapper.readTree(consumerRecord.value());
            assertThat(Contracts.validateEvent(PROCESSED_SCHEMA, payload)).isEmpty();
            assertThat(payload.get("status").asText()).isEqualTo("APPROVED");
            assertThat(payload.get("reason").isNull()).isTrue();
            assertThat(payload.get("sourceEventId").asText()).isEqualTo(event.eventId());
            assertThat(payload.at("/totals/grandTotal").decimalValue())
                    .isEqualByComparingTo("2100.11");
            assertThat(new String(consumerRecord.value(), StandardCharsets.UTF_8))
                    .contains("\"grossSubtotal\":1836.00").contains("\"discount\":25.56")
                    .doesNotContain("Distribuidora");
            assertThat(header(consumerRecord, "eventType")).isEqualTo("OrderProcessed");
            assertThat(header(consumerRecord, "traceparent")).isNotBlank();
        }
        await().atMost(Duration.ofSeconds(10)).until(() ->
                "PUBLISHED".equals(outbox(event.orderId()).getFirst().getString("status")));
    }

    @Test
    void should_reject_with_every_violation_and_publish_rejection() throws IOException {
        stubs.client("CLI-BLOCKED1", "MX", "RETAIL", "GENERAL", "BLOCKED");
        stubs.product("PRD-001", "MX", "ACTIVE", "STANDARD");
        stubs.product("PRD-DISC1", "MX", "DISCONTINUED", "STANDARD");
        final OrderEvents event = OrderEvents.goldenWithFreshIds("REJECTED")
                .clientId("CLI-BLOCKED1").item(1, "PRD-DISC1");

        publish(event);

        final Document order = awaitOrder(event.orderId(), "REJECTED");
        assertThat(order.getString("reason")).isEqualTo("CLIENT_NOT_ACTIVE");
        try (TopicProbe processed = probe(ORDERS_PROCESSED)) {
            final JsonNode payload = objectMapper.readTree(
                    processed.awaitKey(event.orderId(), 1).getFirst().value());
            assertThat(Contracts.validateEvent(PROCESSED_SCHEMA, payload)).isEmpty();
            assertThat(payload.get("reason").asText()).isEqualTo("CLIENT_NOT_ACTIVE");
            assertThat(payload.get("violations")).extracting(node -> node.get("code").asText())
                    .containsExactly("CLIENT_NOT_ACTIVE", "PRODUCT_NOT_ACTIVE");
            assertThat(payload.at("/totals/grandTotal").decimalValue()).isZero();
        }
    }

    @Test
    void should_reject_when_product_does_not_exist_in_market() {
        stubs.golden();
        final OrderEvents event = OrderEvents.goldenWithFreshIds("NOTFOUND")
                .item(0, "PRD-MISSING1");

        publish(event);

        final Document order = awaitOrder(event.orderId(), "REJECTED");
        assertThat(order.getString("reason")).isEqualTo("PRODUCT_NOT_FOUND");
    }

    @Test
    void should_send_contract_violations_to_dlt_with_original_bytes_and_persist_nothing() {
        final OrderEvents event = OrderEvents.goldenWithFreshIds("INVALID").currency("PEN");

        publish(event);

        try (TopicProbe dlt = probe(DLT)) {
            final ConsumerRecord<String, byte[]> consumerRecord = dlt.awaitKey(event.orderId(), 1)
                    .getFirst();
            assertThat(consumerRecord.value()).isEqualTo(event.bytes());
            assertThat(header(consumerRecord, "x-error-category")).isEqualTo("VALIDATION");
            assertThat(header(consumerRecord, "x-error-cause")).contains("currency")
                    .hasSizeLessThan(257);
            assertThat(header(consumerRecord, "x-attempts")).isEqualTo("1");
            assertThat(header(consumerRecord, "x-component")).isEqualTo("order-processor");
            assertThat(header(consumerRecord, "x-order-id")).isEqualTo(event.orderId());
            assertThat(header(consumerRecord, "x-event-id")).isEqualTo(event.eventId());
            assertThat(Instant.parse(header(consumerRecord, "x-failed-at"))).isNotNull();
            assertThat(header(consumerRecord, "kafka_dlt-original-topic"))
                    .isEqualTo(ORDERS_CREATED);
            assertThat(header(consumerRecord, "kafka_dlt-original-offset")).isNotNull();
            assertThat(header(consumerRecord, "kafka_deliveryAttempt")).isNull();
        }
        assertThat(order(event.orderId())).isNull();
    }

    @Test
    void should_send_unreadable_payload_to_dlt_without_poison_pill_loop() {
        final byte[] garbage = "{not-json".getBytes(StandardCharsets.UTF_8);
        final String key = OrderEvents.freshOrderId("GARBAGE");

        try (KafkaTestClient kafka = kafka()) {
            kafka.send(ORDERS_CREATED, key, garbage);
        }

        try (TopicProbe dlt = probe(DLT)) {
            final List<ConsumerRecord<String, byte[]>> records =
                    dlt.awaitKey(key, 1);
            assertThat(records).hasSize(1);
            assertThat(records.getFirst().value()).isEqualTo(garbage);
            assertThat(header(records.getFirst(), "x-error-category"))
                    .isEqualTo("DESERIALIZATION");
            assertThat(header(records.getFirst(), "x-order-id")).isNull();
        }
    }

    @Test
    void should_reject_fractional_quantity_as_deserialization_error() {
        final OrderEvents event = OrderEvents.goldenWithFreshIds("FRACTION").fractionalQuantity(0);

        publish(event);

        try (TopicProbe dlt = probe(DLT)) {
            final ConsumerRecord<String, byte[]> consumerRecord = dlt.awaitKey(event.orderId(), 1)
                    .getFirst();
            assertThat(header(consumerRecord, "x-error-category")).isEqualTo("DESERIALIZATION");
            assertThat(header(consumerRecord, "x-error-cause")).contains("items[0].quantity");
            assertThat(header(consumerRecord, "x-event-id")).isEqualTo(event.eventId());
        }
        assertThat(order(event.orderId())).isNull();
    }

    @Test
    void should_produce_single_effect_when_same_event_is_delivered_twice() {
        stubs.golden();
        final OrderEvents event = OrderEvents.goldenWithFreshIds("DUPLICATE");

        publish(event);
        publish(event);

        awaitOrder(event.orderId(), "APPROVED");
        try (TopicProbe processed = probe(ORDERS_PROCESSED)) {
            assertThat(processed.await(received -> event.orderId().equals(received.key()), 2,
                    Duration.ofSeconds(8), SHORT)).hasSize(1);
        }
        assertThat(outbox(event.orderId())).hasSize(1);
        assertThat(mongo.count(Query.query(Criteria.where("_id").is(event.eventId())), "inbox"))
                .isOne();
    }

    @Test
    void should_keep_first_result_and_dead_letter_conflicting_event_of_same_version() {
        stubs.golden();
        final OrderEvents first = OrderEvents.goldenWithFreshIds("CONFLICT");
        final OrderEvents second = OrderEvents.golden().orderId(first.orderId())
                .eventId(first.eventId() + "-OTHER");

        publish(first);
        awaitOrder(first.orderId(), "APPROVED");
        publish(second);

        try (TopicProbe dlt = probe(DLT)) {
            final ConsumerRecord<String, byte[]> consumerRecord = dlt.awaitKey(first.orderId(), 1)
                    .getFirst();
            assertThat(header(consumerRecord, "x-error-category")).isEqualTo("VERSION_CONFLICT");
            assertThat(header(consumerRecord, "x-event-id")).isEqualTo(second.eventId());
        }
        assertThat(order(first.orderId()).getString("sourceEventId")).isEqualTo(first.eventId());
        assertThat(outbox(first.orderId())).hasSize(1);
    }

    @Test
    void should_ignore_lower_version_after_higher_one() {
        stubs.golden();
        final OrderEvents newer = OrderEvents.goldenWithFreshIds("STALE").version(2);
        final OrderEvents older = OrderEvents.golden().orderId(newer.orderId())
                .eventId(newer.eventId() + "-V1").version(1);

        publish(newer);
        awaitOrder(newer.orderId(), "APPROVED");
        publish(older);

        await().atMost(Duration.ofSeconds(30)).until(() -> "STALE".equals(
                inbox(older.eventId()) == null ? null : inbox(older.eventId())
                        .getString("outcome")));
        final Document stored = order(newer.orderId());
        assertThat(stored.getInteger("eventVersion")).isEqualTo(2);
        assertThat(stored.getString("sourceEventId")).isEqualTo(newer.eventId());
        assertThat(outbox(newer.orderId())).hasSize(1);
    }

    @Test
    void should_approve_after_transient_failures_are_retried() {
        stubs.goldenClient("CLI-99821");
        stubs.productFailsThenRecovers("PRD-FLAKY1", "MX", 2, 503);
        final OrderEvents event = OrderEvents.goldenWithFreshIds("TRANSIENT")
                .singleItem("PRD-FLAKY1", 10, 10.0);

        publish(event);

        awaitOrder(event.orderId(), "APPROVED");
    }

    @Test
    void should_approve_after_a_timeout_is_retried() {
        stubs.goldenClient("CLI-99821");
        stubs.productTimesOutOnce("PRD-SLOW1", "MX", 1500);
        final OrderEvents event = OrderEvents.goldenWithFreshIds("TIMEOUT")
                .singleItem("PRD-SLOW1", 1, 5.0);

        publish(event);

        awaitOrder(event.orderId(), "APPROVED");
    }

    @Test
    void should_record_technical_failure_and_recover_on_replay() {
        stubs.goldenClient("CLI-99821");
        stubs.productStatus("PRD-DOWN1", 503);
        final OrderEvents event = OrderEvents.goldenWithFreshIds("PERMANENT503")
                .singleItem("PRD-DOWN1", 2, 3.0);

        publish(event);

        final Document failed = awaitOrder(event.orderId(), "TECHNICAL_FAILURE");
        final Document failure = failed.get("failure", Document.class);
        assertThat(failure.getString("category")).isEqualTo("EXTERNAL_TRANSIENT");
        assertThat(failure.getInteger("attempts")).isEqualTo(4);
        try (TopicProbe dlt = probe(DLT)) {
            final ConsumerRecord<String, byte[]> consumerRecord = dlt.awaitKey(event.orderId(), 1)
                    .getFirst();
            assertThat(header(consumerRecord, "x-error-category")).isEqualTo("EXTERNAL_TRANSIENT");
            assertThat(header(consumerRecord, "x-attempts")).isEqualTo("4");
            assertThat(consumerRecord.value()).isEqualTo(event.bytes());
        }
        assertThat(inbox(event.eventId())).isNull();
        assertThat(outbox(event.orderId())).isEmpty();

        WIREMOCK.resetAll();
        stubs.token();
        stubs.goldenClient("CLI-99821");
        stubs.product("PRD-DOWN1", "MX", "ACTIVE", "STANDARD");
        publish(event);

        awaitOrder(event.orderId(), "APPROVED");
        assertThat(outbox(event.orderId())).hasSize(1);
    }

    @Test
    void should_dead_letter_permanent_dependency_errors_without_retrying() {
        stubs.goldenClient("CLI-99821");
        stubs.productStatus("PRD-BAD1", 400);
        final OrderEvents event = OrderEvents.goldenWithFreshIds("PERMANENT400")
                .singleItem("PRD-BAD1", 1, 1.0);

        publish(event);

        final Document failed = awaitOrder(event.orderId(), "TECHNICAL_FAILURE");
        assertThat(failed.get("failure", Document.class).getString("category"))
                .isEqualTo("EXTERNAL_PERMANENT");
        try (TopicProbe dlt = probe(DLT)) {
            final ConsumerRecord<String, byte[]> consumerRecord = dlt.awaitKey(event.orderId(), 1)
                    .getFirst();
            assertThat(header(consumerRecord, "x-error-category")).isEqualTo("EXTERNAL_PERMANENT");
            assertThat(header(consumerRecord, "x-attempts")).isEqualTo("1");
        }
    }

    private void publish(final OrderEvents event) {
        try (KafkaTestClient kafka = kafka()) {
            kafka.send(ORDERS_CREATED, event.orderId(), event.bytes());
        }
    }

    private TopicProbe probe(final String topic) {
        return new TopicProbe(KAFKA.getBootstrapServers(), topic);
    }

    private Document awaitOrder(final String orderId, final String status) {
        await().atMost(Duration.ofSeconds(45)).pollInterval(Duration.ofMillis(200))
                .until(() -> order(orderId) != null
                        && status.equals(order(orderId).getString("status")));
        return order(orderId);
    }

    private Document order(final String orderId) {
        return mongo.findById(orderId, Document.class, "orders");
    }

    private Document inbox(final String eventId) {
        return mongo.findById(eventId, Document.class, "inbox");
    }

    private List<Document> outbox(final String orderId) {
        return mongo.find(Query.query(Criteria.where("orderId").is(orderId)), Document.class,
                "outbox");
    }
}
