package com.grupomariposa.orders.infrastructure.web;

import static com.grupomariposa.orders.infrastructure.support.Enums.nameOf;

import com.grupomariposa.orders.application.query.OrderSummary;
import com.grupomariposa.orders.application.query.PageResult;
import com.grupomariposa.orders.domain.model.ClientSnapshot;
import com.grupomariposa.orders.domain.model.LineAmounts;
import com.grupomariposa.orders.domain.model.Money;
import com.grupomariposa.orders.domain.model.Order;
import com.grupomariposa.orders.domain.model.OrderLine;
import com.grupomariposa.orders.domain.model.Rate;
import com.grupomariposa.orders.domain.model.RejectionCode;
import com.grupomariposa.orders.domain.model.Totals;
import com.grupomariposa.orders.infrastructure.web.dto.ClientSnapshotResponse;
import com.grupomariposa.orders.infrastructure.web.dto.FailureResponse;
import com.grupomariposa.orders.infrastructure.web.dto.OrderLineResponse;
import com.grupomariposa.orders.infrastructure.web.dto.OrderPageResponse;
import com.grupomariposa.orders.infrastructure.web.dto.OrderResponse;
import com.grupomariposa.orders.infrastructure.web.dto.OrderSummaryResponse;
import com.grupomariposa.orders.infrastructure.web.dto.TotalsResponse;
import com.grupomariposa.orders.infrastructure.web.dto.ViolationResponse;
import java.math.BigDecimal;
import java.util.function.Function;

public final class OrderResponseMapper {

    public OrderResponse toResponse(final Order order) {
        return new OrderResponse(order.orderId(), order.sourceEventId(), order.eventVersion(),
                order.status().name(), order.market().name(), order.currency().name(),
                order.channel(), client(order.client()),
                order.lines().stream().map(OrderResponseMapper::line).toList(),
                totals(order.totals()), order.reason().map(RejectionCode::name).orElse(null),
                order.violations().stream().map(violation -> new ViolationResponse(
                        violation.code().name(), violation.message(), violation.productId()))
                        .toList(),
                order.failureDetails().map(failure -> new FailureResponse(failure.category(),
                        failure.cause(), failure.attempts())).orElse(null),
                order.timeline().occurredAt(), order.timeline().receivedAt(),
                order.processedAt(), order.traceId());
    }

    public OrderPageResponse toPage(final PageResult<OrderSummary> page) {
        return new OrderPageResponse(page.items().stream().map(OrderResponseMapper::summary)
                .toList(), page.page(), page.size(), page.totalElements(), page.totalPages());
    }

    private static OrderSummaryResponse summary(final OrderSummary summary) {
        return new OrderSummaryResponse(summary.orderId(), summary.status().name(),
                summary.market().name(), summary.currency().name(), summary.clientId(),
                summary.eventVersion(), summary.grandTotal().amount(),
                nameOf(summary.reason()), summary.processedAt());
    }

    private static ClientSnapshotResponse client(final ClientSnapshot client) {
        return new ClientSnapshotResponse(client.clientId(), client.name(),
                nameOf(client.status()), nameOf(client.segment()), nameOf(client.taxRegime()),
                nameOf(client.market()));
    }

    private static OrderLineResponse line(final OrderLine line) {
        final LineAmounts amounts = line.amounts();
        return new OrderLineResponse(line.productId(), line.name(), line.sku(),
                nameOf(line.taxCategory()), line.quantity(), line.unitPrice(),
                money(amounts, LineAmounts::grossSubtotal),
                rate(amounts, LineAmounts::discountRate),
                money(amounts, LineAmounts::discount), money(amounts, LineAmounts::netSubtotal),
                rate(amounts, LineAmounts::taxRate), money(amounts, LineAmounts::taxAmount),
                money(amounts, LineAmounts::lineTotal));
    }

    private static TotalsResponse totals(final Totals totals) {
        return new TotalsResponse(totals.grossSubtotal().amount(), totals.discount().amount(),
                totals.netSubtotal().amount(), totals.tax().amount(),
                totals.grandTotal().amount());
    }

    private static BigDecimal money(final LineAmounts amounts,
                                    final Function<LineAmounts, Money> field) {
        return amounts == null ? null : field.apply(amounts).amount();
    }

    private static BigDecimal rate(final LineAmounts amounts,
                                   final Function<LineAmounts, Rate> field) {
        return amounts == null ? null : field.apply(amounts).value();
    }
}
