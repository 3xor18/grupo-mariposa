package com.grupomariposa.orders.application.validation;

import com.grupomariposa.orders.domain.model.Currency;
import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.MarketCurrencies;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Objects;

final class HeaderValidator {

    private static final String EVENT_ID = "eventId";
    private static final String EVENT_VERSION = "eventVersion";
    private static final String OCCURRED_AT = "occurredAt";
    private static final String ORDER_ID = "orderId";
    private static final String MARKET = "market";
    private static final String CURRENCY = "currency";
    private static final String CLIENT_ID = "clientId";
    private static final String CHANNEL = "channel";
    private static final String ONE_OF = "must be one of %s";
    private static final String MIN_VERSION = "must be greater than or equal to 1";
    private static final String INVALID_DATE = "must be an ISO-8601 date-time with offset";
    private static final String CURRENCY_MISMATCH = "does not match market %s (expected %s)";

    private final MarketCurrencies markets;
    private final ContractRules rules;

    HeaderValidator(final MarketCurrencies markets, final ContractRules rules) {
        this.markets = Objects.requireNonNull(markets, "markets");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    ValidatedHeader validate(final UnvalidatedOrder order, final ErrorCollector errors) {
        errors.requireText(EVENT_ID, order.eventId(), OrderCommandValidator.MAX_ID_LENGTH);
        errors.requireText(ORDER_ID, order.orderId(), OrderCommandValidator.MAX_ID_LENGTH);
        errors.requireIdentifier(CLIENT_ID, order.clientId(), OrderCommandValidator.MAX_ID_LENGTH,
                rules.clientIdPattern());
        errors.limitLength(CHANNEL, order.channel(), OrderCommandValidator.MAX_CHANNEL_LENGTH);
        final Market market = market(order.market(), errors);
        final Currency currency = currency(order.currency(), errors);
        if (market != null && currency != null && !markets.accepts(market, currency)) {
            errors.add(CURRENCY, CURRENCY_MISMATCH.formatted(market,
                    markets.currencyOf(market).orElseThrow()));
        }
        return new ValidatedHeader(market, currency, occurredAt(order.occurredAt(), errors),
                eventVersion(order.eventVersion(), errors));
    }

    private Market market(final String raw, final ErrorCollector errors) {
        final Market market = Market.fromCode(raw).filter(markets::supports).orElse(null);
        rejectUnknown(MARKET, raw, market, markets.supportedMarkets().toArray(), errors);
        return market;
    }

    private static Currency currency(final String raw, final ErrorCollector errors) {
        final Currency currency = Arrays.stream(Currency.values())
                .filter(candidate -> candidate.name().equals(raw))
                .findFirst()
                .orElse(null);
        rejectUnknown(CURRENCY, raw, currency, Currency.values(), errors);
        return currency;
    }

    private static void rejectUnknown(final String field, final String raw, final Enum<?> parsed,
                                      final Object[] allowed, final ErrorCollector errors) {
        if (errors.requirePresent(field, raw) && parsed == null) {
            errors.add(field, ONE_OF.formatted(Arrays.toString(allowed)));
        }
    }

    private static int eventVersion(final Integer raw, final ErrorCollector errors) {
        if (raw == null) {
            return OrderCommandValidator.DEFAULT_EVENT_VERSION;
        }
        if (raw < OrderCommandValidator.DEFAULT_EVENT_VERSION) {
            errors.add(EVENT_VERSION, MIN_VERSION);
        }
        return raw;
    }

    private static Instant occurredAt(final String raw, final ErrorCollector errors) {
        if (raw == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw).toInstant();
        } catch (DateTimeParseException invalid) {
            errors.add(OCCURRED_AT, INVALID_DATE);
            return null;
        }
    }
}
