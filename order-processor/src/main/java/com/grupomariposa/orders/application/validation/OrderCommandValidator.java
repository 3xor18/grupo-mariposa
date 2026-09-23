package com.grupomariposa.orders.application.validation;

import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.command.Reception;
import com.grupomariposa.orders.domain.model.MarketCatalog;
import com.grupomariposa.orders.domain.model.RequestedItem;
import java.util.List;

public final class OrderCommandValidator {

    public static final int DEFAULT_EVENT_VERSION = 1;
    public static final int MAX_ID_LENGTH = 64;
    public static final int MAX_CHANNEL_LENGTH = 32;
    public static final int MAX_ITEMS = 500;

    private final HeaderValidator headerValidator;
    private final ItemValidator itemValidator;

    public OrderCommandValidator(final MarketCatalog markets, final ContractRules rules) {
        this.headerValidator = new HeaderValidator(markets, rules);
        this.itemValidator = new ItemValidator(rules);
    }

    public ValidationResult validate(final UnvalidatedOrder order, final Reception reception) {
        final ErrorCollector errors = new ErrorCollector();
        final ValidatedHeader header = headerValidator.validate(order, errors);
        final List<RequestedItem> items = itemValidator.validate(order.items(), errors);
        if (!errors.isEmpty()) {
            return new ValidationResult.Invalid(errors.errors());
        }
        return new ValidationResult.Valid(new OrderCommand(order.eventId(),
                header.eventVersion(), order.orderId(), header.market(), header.currency(),
                order.clientId(), order.channel(), header.occurredAt(), items, reception));
    }
}
