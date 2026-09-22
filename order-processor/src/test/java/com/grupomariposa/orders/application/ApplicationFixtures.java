package com.grupomariposa.orders.application;

import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.command.Reception;
import com.grupomariposa.orders.application.validation.ContractRules;
import com.grupomariposa.orders.application.validation.OrderCommandValidator;
import com.grupomariposa.orders.application.validation.UnvalidatedItem;
import com.grupomariposa.orders.application.validation.UnvalidatedOrder;
import com.grupomariposa.orders.domain.DomainFixtures;
import com.grupomariposa.orders.domain.model.Currency;
import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.RequestedItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

public final class ApplicationFixtures {

    public static final String EVENT_ID = "01J8ZP6M5E4RH0K7Y2N9A3TQWX";
    public static final String ORDER_ID = "ORD-MX-000147";
    public static final String CLIENT_ID = "CLI-99821";
    public static final Instant RECEIVED_AT = Instant.parse("2026-09-18T15:42:11Z");
    public static final Instant OCCURRED_AT = Instant.parse("2026-09-18T15:42:10Z");
    public static final Reception RECEPTION = new Reception(RECEIVED_AT, "trace-1");
    public static final ContractRules CONTRACT_RULES = new ContractRules(
            Pattern.compile("^PRD-[A-Z0-9]{1,20}$"), Pattern.compile("^CLI-[A-Z0-9]{1,20}$"),
            18, 4, new BigDecimal("1000000000000"));

    public static OrderCommandValidator validator() {
        return new OrderCommandValidator(DomainFixtures.MARKETS, CONTRACT_RULES);
    }

    private ApplicationFixtures() {
    }

    public static OrderCommand goldenCommand() {
        return command(EVENT_ID, 1);
    }

    public static OrderCommand command(final String eventId, final int version) {
        return command(ORDER_ID, eventId, version);
    }

    public static OrderCommand command(final String orderId, final String eventId,
                                       final int version) {
        return new OrderCommand(eventId, version, orderId, Market.MX, Currency.MXN, CLIENT_ID,
                "C1", OCCURRED_AT, List.of(
                new RequestedItem("PRD-001", 24, new BigDecimal("35.5")),
                new RequestedItem("PRD-008", 12, new BigDecimal("82.0"))), RECEPTION);
    }

    public static UnvalidatedOrder goldenSubmission() {
        return new UnvalidatedOrder(EVENT_ID, 1, "2026-09-18T15:42:10Z", ORDER_ID, "MX", "MXN",
                CLIENT_ID, "C1", List.of(
                new UnvalidatedItem("PRD-001", 24L, new BigDecimal("35.5")),
                new UnvalidatedItem("PRD-008", 12L, new BigDecimal("82.0"))));
    }
}
