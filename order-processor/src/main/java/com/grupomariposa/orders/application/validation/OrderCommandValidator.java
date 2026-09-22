package com.grupomariposa.orders.application.validation;

import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.command.Reception;
import com.grupomariposa.orders.domain.model.Currency;
import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.MarketCurrencies;
import com.grupomariposa.orders.domain.model.RequestedItem;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

public final class OrderCommandValidator {

    public static final int DEFAULT_EVENT_VERSION = 1;
    public static final int MAX_ID_LENGTH = 64;
    public static final int MAX_CHANNEL_LENGTH = 32;
    public static final int MAX_ITEMS = 500;

    private static final String EVENT_ID = "eventId";
    private static final String EVENT_VERSION = "eventVersion";
    private static final String OCCURRED_AT = "occurredAt";
    private static final String ORDER_ID = "orderId";
    private static final String MARKET = "market";
    private static final String CURRENCY = "currency";
    private static final String CLIENT_ID = "clientId";
    private static final String CHANNEL = "channel";
    private static final String ITEMS = "items";
    private static final String ITEM_FIELD = "items[%d].%s";
    private static final String ITEM = "items[%d]";
    private static final String PRODUCT_ID = "productId";
    private static final String QUANTITY = "quantity";
    private static final String UNIT_PRICE = "unitPrice";
    private static final String ONE_OF = "must be one of %s";
    private static final String MIN_VERSION = "must be greater than or equal to 1";
    private static final String INVALID_DATE = "must be an ISO-8601 date-time with offset";
    private static final String CURRENCY_MISMATCH = "does not match market %s (expected %s)";
    private static final String EMPTY_ITEMS = "must contain at least one item";
    private static final String TOO_MANY_ITEMS = "must contain at most %d items";
    private static final String POSITIVE_QUANTITY = "must be an integer greater than 0";
    private static final String NON_NEGATIVE_PRICE = "must be greater than or equal to 0";
    private static final String DUPLICATED_PRODUCT = "is duplicated within the order";

    private final MarketCurrencies markets;

    public OrderCommandValidator(final MarketCurrencies markets) {
        this.markets = Objects.requireNonNull(markets, "markets");
    }

    public ValidationResult validate(final UnvalidatedOrder order, final Reception reception) {
        final ErrorCollector errors = new ErrorCollector();
        validateHeader(order, errors);
        validateMarketAndCurrency(order, errors);
        validateItems(order.items(), errors);
        if (!errors.isEmpty()) {
            return new ValidationResult.Invalid(errors.errors());
        }
        return new ValidationResult.Valid(toCommand(order, reception));
    }

    private static void validateHeader(final UnvalidatedOrder order, final ErrorCollector errors) {
        errors.requireText(EVENT_ID, order.eventId(), MAX_ID_LENGTH);
        errors.requireText(ORDER_ID, order.orderId(), MAX_ID_LENGTH);
        errors.requireText(CLIENT_ID, order.clientId(), MAX_ID_LENGTH);
        errors.limitLength(CHANNEL, order.channel(), MAX_CHANNEL_LENGTH);
        if (order.eventVersion() != null && order.eventVersion() < DEFAULT_EVENT_VERSION) {
            errors.add(EVENT_VERSION, MIN_VERSION);
        }
        if (order.occurredAt() != null && parseInstant(order.occurredAt()).isEmpty()) {
            errors.add(OCCURRED_AT, INVALID_DATE);
        }
    }

    private void validateMarketAndCurrency(final UnvalidatedOrder order,
                                           final ErrorCollector errors) {
        final Optional<Market> market = parseEnum(MARKET, order.market(),
                Market.fromCode(order.market()).filter(markets::supports),
                markets.supportedMarkets().toArray(Market[]::new), errors);
        final Optional<Currency> currency = parseEnum(CURRENCY, order.currency(),
                currencyOf(order.currency()), Currency.values(), errors);
        if (market.isPresent() && currency.isPresent()
                && !markets.accepts(market.get(), currency.get())) {
            errors.add(CURRENCY, CURRENCY_MISMATCH.formatted(market.get(),
                    markets.currencyOf(market.get()).orElseThrow()));
        }
    }

    private static <E extends Enum<E>> Optional<E> parseEnum(final String field,
                                                             final String raw,
                                                             final Optional<E> parsed,
                                                             final E[] allowed,
                                                             final ErrorCollector errors) {
        if (errors.requirePresent(field, raw) && parsed.isEmpty()) {
            errors.add(field, ONE_OF.formatted(Arrays.toString(allowed)));
        }
        return parsed;
    }

    private static void validateItems(final List<UnvalidatedItem> items,
                                      final ErrorCollector errors) {
        if (!errors.requirePresent(ITEMS, items)) {
            return;
        }
        if (items.isEmpty()) {
            errors.add(ITEMS, EMPTY_ITEMS);
        }
        if (items.size() > MAX_ITEMS) {
            errors.add(ITEMS, TOO_MANY_ITEMS.formatted(MAX_ITEMS));
        }
        final Set<String> seen = new HashSet<>();
        IntStream.range(0, items.size()).forEach(index ->
                validateItem(index, items.get(index), seen, errors));
    }

    private static void validateItem(final int index, final UnvalidatedItem item,
                                     final Set<String> seen, final ErrorCollector errors) {
        if (!errors.requirePresent(ITEM.formatted(index), item)) {
            return;
        }
        final String productField = ITEM_FIELD.formatted(index, PRODUCT_ID);
        errors.requireText(productField, item.productId(), MAX_ID_LENGTH);
        if (item.productId() != null && !seen.add(item.productId())) {
            errors.add(productField, DUPLICATED_PRODUCT);
        }
        validateQuantity(ITEM_FIELD.formatted(index, QUANTITY), item.quantity(), errors);
        validatePrice(ITEM_FIELD.formatted(index, UNIT_PRICE), item, errors);
    }

    private static void validateQuantity(final String field, final Long quantity,
                                         final ErrorCollector errors) {
        final boolean present = errors.requirePresent(field, quantity);
        if (present && (quantity <= 0 || quantity > Integer.MAX_VALUE)) {
            errors.add(field, POSITIVE_QUANTITY);
        }
    }

    private static void validatePrice(final String field, final UnvalidatedItem item,
                                      final ErrorCollector errors) {
        if (errors.requirePresent(field, item.unitPrice()) && item.unitPrice().signum() < 0) {
            errors.add(field, NON_NEGATIVE_PRICE);
        }
    }

    private static OrderCommand toCommand(final UnvalidatedOrder order,
                                          final Reception reception) {
        final int version = order.eventVersion() == null
                ? DEFAULT_EVENT_VERSION : order.eventVersion();
        final Instant occurredAt = order.occurredAt() == null
                ? null : parseInstant(order.occurredAt()).orElseThrow();
        final List<RequestedItem> items = order.items().stream()
                .map(item -> new RequestedItem(item.productId(), item.quantity().intValue(),
                        item.unitPrice()))
                .toList();
        return new OrderCommand(order.eventId(), version, order.orderId(),
                Market.fromCode(order.market()).orElseThrow(),
                currencyOf(order.currency()).orElseThrow(), order.clientId(), order.channel(),
                occurredAt, items, reception);
    }

    private static Optional<Currency> currencyOf(final String code) {
        return Arrays.stream(Currency.values())
                .filter(currency -> currency.name().equals(code))
                .findFirst();
    }

    private static Optional<Instant> parseInstant(final String value) {
        try {
            return Optional.of(OffsetDateTime.parse(value).toInstant());
        } catch (DateTimeParseException invalid) {
            return Optional.empty();
        }
    }
}
