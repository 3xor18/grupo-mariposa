package com.grupomariposa.orders.application.service;

import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.port.out.TimeProvider;
import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.ClientSnapshot;
import com.grupomariposa.orders.domain.model.Decision;
import com.grupomariposa.orders.domain.model.FailureDetails;
import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.domain.model.Order;
import com.grupomariposa.orders.domain.model.OrderIdentity;
import com.grupomariposa.orders.domain.model.OrderLine;
import com.grupomariposa.orders.domain.model.OrderStatus;
import com.grupomariposa.orders.domain.model.OrderTimeline;
import com.grupomariposa.orders.domain.model.Totals;
import java.util.List;
import java.util.Objects;

public final class OrderAssembler {

    private final TimeProvider timeProvider;

    public OrderAssembler(final TimeProvider timeProvider) {
        this.timeProvider = Objects.requireNonNull(timeProvider, "timeProvider");
    }

    public Order decided(final OrderCommand command, final Lookup<ClientProfile> client,
                         final Decision decision) {
        return new Order(identity(command), decision.status(), command.market(),
                command.currency(), command.channel(),
                ClientSnapshot.of(command.clientId(), client),
                decision.lines(), decision.totals(), decision.violations(), null,
                timeline(command), command.reception().traceId());
    }

    public Order technicalFailure(final OrderCommand command, final FailureDetails failure) {
        final List<OrderLine> lines = command.items().stream().map(OrderLine::unpriced).toList();
        return new Order(identity(command), OrderStatus.TECHNICAL_FAILURE, command.market(),
                command.currency(), command.channel(),
                ClientSnapshot.unresolved(command.clientId()),
                lines, Totals.ZERO, List.of(), failure, timeline(command),
                command.reception().traceId());
    }

    private static OrderIdentity identity(final OrderCommand command) {
        return new OrderIdentity(command.orderId(), command.eventId(), command.eventVersion());
    }

    private OrderTimeline timeline(final OrderCommand command) {
        return new OrderTimeline(command.occurredAt(), command.reception().receivedAt(),
                timeProvider.now());
    }
}
