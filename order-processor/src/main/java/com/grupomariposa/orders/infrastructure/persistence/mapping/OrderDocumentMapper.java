package com.grupomariposa.orders.infrastructure.persistence.mapping;

import static com.grupomariposa.orders.infrastructure.support.Enums.nameOf;
import static com.grupomariposa.orders.infrastructure.support.Enums.parseNullable;

import com.grupomariposa.orders.application.query.OrderSummary;
import com.grupomariposa.orders.domain.model.ClientSegment;
import com.grupomariposa.orders.domain.model.ClientSnapshot;
import com.grupomariposa.orders.domain.model.ClientStatus;
import com.grupomariposa.orders.domain.model.Currency;
import com.grupomariposa.orders.domain.model.FailureDetails;
import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.Order;
import com.grupomariposa.orders.domain.model.OrderIdentity;
import com.grupomariposa.orders.domain.model.OrderStatus;
import com.grupomariposa.orders.domain.model.OrderTimeline;
import com.grupomariposa.orders.domain.model.RejectionCode;
import com.grupomariposa.orders.domain.model.TaxRegime;
import com.grupomariposa.orders.domain.model.Totals;
import com.grupomariposa.orders.domain.model.Violation;
import com.grupomariposa.orders.infrastructure.crypto.AesGcmPiiCipher;
import com.grupomariposa.orders.infrastructure.persistence.document.ClientDocument;
import com.grupomariposa.orders.infrastructure.persistence.document.FailureDocument;
import com.grupomariposa.orders.infrastructure.persistence.document.OrderDocument;
import com.grupomariposa.orders.infrastructure.persistence.document.TotalsDocument;
import com.grupomariposa.orders.infrastructure.persistence.document.ViolationDocument;
import java.util.List;
import java.util.Objects;

public final class OrderDocumentMapper {

    private static final String MISSING_FIELD = "Order document %s has no %s";
    private static final String CLIENT = "client";
    private static final String TOTALS = "totals";

    private final AesGcmPiiCipher cipher;
    private final LineDocumentMapper lineMapper = new LineDocumentMapper();

    public OrderDocumentMapper(final AesGcmPiiCipher cipher) {
        this.cipher = Objects.requireNonNull(cipher, "cipher");
    }

    public OrderDocument toDocument(final Order order) {
        return new OrderDocument(order.orderId(), order.sourceEventId(), order.eventVersion(),
                order.status().name(), order.market().name(), order.currency().name(),
                order.channel(), clientDocument(order.client()),
                order.lines().stream().map(lineMapper::toDocument).toList(),
                totalsDocument(order.totals()),
                order.reason().map(RejectionCode::name).orElse(null),
                order.violations().stream().map(OrderDocumentMapper::violationDocument).toList(),
                failureDocument(order.failure()),
                order.timeline().occurredAt(), order.timeline().receivedAt(),
                order.processedAt(), order.traceId());
    }

    public Order toDomain(final OrderDocument document) {
        return new Order(
                new OrderIdentity(document.id(), document.sourceEventId(),
                        document.eventVersion()),
                OrderStatus.valueOf(document.status()), Market.valueOf(document.market()),
                Currency.valueOf(document.currency()), document.channel(),
                clientSnapshot(required(document.client(), CLIENT, document.id())),
                orEmpty(document.lines()).stream().map(lineMapper::toDomain).toList(),
                totals(required(document.totals(), TOTALS, document.id())),
                orEmpty(document.violations()).stream().map(OrderDocumentMapper::violation)
                        .toList(),
                failure(document.failure()),
                new OrderTimeline(document.occurredAt(), document.receivedAt(),
                        document.processedAt()),
                document.traceId());
    }

    public OrderSummary toSummary(final OrderDocument document) {
        return new OrderSummary(document.id(), OrderStatus.valueOf(document.status()),
                Market.valueOf(document.market()), Currency.valueOf(document.currency()),
                document.client().clientId(), document.eventVersion(),
                Decimals.toMoney(document.totals().grandTotal()),
                parseNullable(document.reason(), RejectionCode::valueOf), document.processedAt());
    }

    private static <T> T required(final T value, final String field, final String orderId) {
        if (value == null) {
            throw new IllegalStateException(MISSING_FIELD.formatted(orderId, field));
        }
        return value;
    }

    private static <T> List<T> orEmpty(final List<T> values) {
        return values == null ? List.of() : values;
    }

    private ClientDocument clientDocument(final ClientSnapshot client) {
        return new ClientDocument(client.clientId(), cipher.encrypt(client.name()),
                nameOf(client.status()), nameOf(client.segment()), nameOf(client.taxRegime()),
                nameOf(client.market()));
    }

    private ClientSnapshot clientSnapshot(final ClientDocument client) {
        return new ClientSnapshot(client.clientId(), cipher.decrypt(client.encryptedName()),
                parseNullable(client.status(), ClientStatus::valueOf),
                parseNullable(client.segment(), ClientSegment::valueOf),
                parseNullable(client.taxRegime(), TaxRegime::valueOf),
                parseNullable(client.market(), Market::valueOf));
    }

    private static TotalsDocument totalsDocument(final Totals totals) {
        return new TotalsDocument(Decimals.of(totals.grossSubtotal()),
                Decimals.of(totals.discount()), Decimals.of(totals.netSubtotal()),
                Decimals.of(totals.tax()), Decimals.of(totals.grandTotal()));
    }

    private static Totals totals(final TotalsDocument totals) {
        return new Totals(Decimals.toMoney(totals.grossSubtotal()),
                Decimals.toMoney(totals.discount()), Decimals.toMoney(totals.netSubtotal()),
                Decimals.toMoney(totals.tax()), Decimals.toMoney(totals.grandTotal()));
    }

    private static ViolationDocument violationDocument(final Violation violation) {
        return new ViolationDocument(violation.code().name(), violation.message(),
                violation.productId());
    }

    private static Violation violation(final ViolationDocument document) {
        return new Violation(RejectionCode.valueOf(document.code()), document.message(),
                document.productId());
    }

    private static FailureDocument failureDocument(final FailureDetails failure) {
        return failure == null ? null
                : new FailureDocument(failure.category(), failure.cause(), failure.attempts());
    }

    private static FailureDetails failure(final FailureDocument document) {
        return document == null ? null
                : new FailureDetails(document.category(), document.cause(), document.attempts());
    }
}
