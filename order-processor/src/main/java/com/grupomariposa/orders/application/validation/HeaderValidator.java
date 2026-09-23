package com.grupomariposa.orders.application.validation;

import com.grupomariposa.orders.domain.model.CurrencyCode;
import com.grupomariposa.orders.domain.model.MarketCatalog;
import com.grupomariposa.orders.domain.model.MarketCode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

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
    private static final String ISO_CURRENCY = "must be a three-letter ISO 4217 code";
    private static final String MIN_VERSION = "must be greater than or equal to 1";
    private static final String INVALID_DATE = "must be an ISO-8601 date-time with offset";
    private static final String CURRENCY_MISMATCH = "does not match market %s (expected %s)";

    private final MarketCatalog markets;
    private final ContractRules rules;

    HeaderValidator(final MarketCatalog markets, final ContractRules rules) {
        this.markets = Objects.requireNonNull(markets, "markets");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    ValidatedHeader validate(final UnvalidatedOrder order, final ErrorCollector errors) {
        errors.requireText(EVENT_ID, order.eventId(), OrderCommandValidator.MAX_ID_LENGTH);
        errors.requireText(ORDER_ID, order.orderId(), OrderCommandValidator.MAX_ID_LENGTH);
        errors.requireIdentifier(CLIENT_ID, order.clientId(), OrderCommandValidator.MAX_ID_LENGTH,
                rules.clientIdPattern());
        errors.limitLength(CHANNEL, order.channel(), OrderCommandValidator.MAX_CHANNEL_LENGTH);
        final MarketCode market = market(order.market(), errors);
        final CurrencyCode currency = currency(order.currency(), errors);
        if (market != null && currency != null && !markets.accepts(market, currency)) {
            errors.add(CURRENCY, CURRENCY_MISMATCH.formatted(market,
                    markets.currencyOf(market).orElseThrow()));
        }
        return new ValidatedHeader(market, currency, occurredAt(order.occurredAt(), errors),
                eventVersion(order.eventVersion(), errors));
    }

    private MarketCode market(final String raw, final ErrorCollector errors) {
        if (!errors.requirePresent(MARKET, raw)) {
            return null;
        }
        final Optional<MarketCode> market = MarketCode.parse(raw).filter(markets::supports);
        if (market.isEmpty()) {
            errors.add(MARKET, ONE_OF.formatted(markets.supportedMarkets()));
        }
        return market.orElse(null);
    }

    private static CurrencyCode currency(final String raw, final ErrorCollector errors) {
        if (!errors.requirePresent(CURRENCY, raw)) {
            return null;
        }
        final Optional<CurrencyCode> currency = CurrencyCode.parse(raw);
        if (currency.isEmpty()) {
            errors.add(CURRENCY, ISO_CURRENCY);
        }
        return currency.orElse(null);
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
