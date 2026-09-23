package com.grupomariposa.orders.infrastructure.persistence.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.grupomariposa.orders.application.query.OrderSummary;
import com.grupomariposa.orders.domain.model.Money;
import com.grupomariposa.orders.domain.model.Order;
import com.grupomariposa.orders.domain.model.OrderStatus;
import com.grupomariposa.orders.domain.model.RejectionCode;
import com.grupomariposa.orders.infrastructure.persistence.PersistenceFixtures;
import com.grupomariposa.orders.infrastructure.persistence.document.OrderDocument;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class OrderDocumentMapperTest {

    private final OrderDocumentMapper mapper =
            new OrderDocumentMapper(PersistenceFixtures.cipher());

    static Stream<Order> orders() {
        return Stream.of(PersistenceFixtures.approvedOrder(), PersistenceFixtures.rejectedOrder(),
                PersistenceFixtures.technicalFailure());
    }

    @ParameterizedTest
    @MethodSource("orders")
    void should_round_trip_every_order_shape(final Order order) {
        assertThat(mapper.toDomain(mapper.toDocument(order))).isEqualTo(order);
    }

    @Test
    void should_store_money_as_decimal128_and_encrypt_client_name() {
        final OrderDocument document = mapper.toDocument(PersistenceFixtures.approvedOrder());

        assertThat(document.totals().grandTotal()).isEqualTo(new Decimal128(
                new BigDecimal("2100.11")));
        assertThat(document.client().encryptedName()).startsWith("k1:")
                .doesNotContain("Distribuidora");
        assertThat(document.status()).isEqualTo("APPROVED");
        assertThat(document.reason()).isNull();
    }

    @Test
    void should_map_summaries() {
        final OrderSummary summary = mapper.toSummary(mapper.toDocument(
                PersistenceFixtures.rejectedOrder()));

        assertThat(summary.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(summary.reason()).isEqualTo(RejectionCode.CLIENT_NOT_FOUND);
        assertThat(summary.grandTotal()).isEqualTo(Money.zero(2));
        assertThat(summary.clientId()).isEqualTo("CLI-99821");
    }

    @Test
    void should_map_summaries_without_reason() {
        assertThat(mapper.toSummary(mapper.toDocument(PersistenceFixtures.approvedOrder()))
                .reason()).isNull();
    }
}
