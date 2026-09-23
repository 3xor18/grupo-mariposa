package com.grupomariposa.orders.infrastructure.web;

import com.grupomariposa.orders.application.query.OrderSearchCriteria;
import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.OrderStatus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class OrderRequestParser {

    private static final Pattern ORDER_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$");
    private static final Pattern DIGITS = Pattern.compile("^\\d{1,9}$");
    private static final String ORDER_ID_FIELD = "orderId";
    private static final String STATUS = "status";
    private static final String MARKET = "market";
    private static final String MARKET_FORMAT = "must be a two-letter market code";
    private static final String PAGE = "page";
    private static final String SIZE = "size";
    private static final String INVALID_ORDER_ID =
            "must be 1-64 characters: letters, digits, '.', '_', ':' or '-'";
    private static final String ONE_OF = "must be one of %s";
    private static final String MIN_PAGE = "must be an integer greater than or equal to 0";
    private static final String SIZE_RANGE = "must be an integer between 1 and %d";
    private static final String OFFSET_TOO_DEEP = "page * size must not exceed %d";
    private static final int FIRST_PAGE = 0;

    private final OrdersApiProperties limits;

    public OrderRequestParser(final OrdersApiProperties limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public String orderId(final String raw) {
        if (raw == null || !ORDER_ID.matcher(raw).matches()) {
            throw new InvalidRequestException(List.of(
                    new FieldViolation(ORDER_ID_FIELD, INVALID_ORDER_ID)));
        }
        return raw;
    }

    public OrderSearchCriteria criteria(final String status, final String market,
                                        final String page, final String size) {
        final Parsed<OrderStatus> parsedStatus = enumValue(OrderStatus.class, STATUS, status);
        final Parsed<MarketCode> parsedMarket = marketCode(market);
        final Parsed<Integer> parsedPage = number(PAGE, page, FIRST_PAGE,
                value -> value >= FIRST_PAGE, MIN_PAGE);
        final Parsed<Integer> parsedSize = number(SIZE, size, limits.defaultPageSize(),
                value -> value >= 1 && value <= limits.maxPageSize(),
                SIZE_RANGE.formatted(limits.maxPageSize()));
        final List<FieldViolation> violations = new ArrayList<>(Parsed.violations(
                parsedStatus, parsedMarket, parsedPage, parsedSize));
        if (violations.isEmpty()
                && (long) parsedPage.value() * parsedSize.value() > limits.maxOffset()) {
            violations.add(new FieldViolation(PAGE, OFFSET_TOO_DEEP.formatted(limits.maxOffset())));
        }
        if (!violations.isEmpty()) {
            throw new InvalidRequestException(violations);
        }
        return new OrderSearchCriteria(parsedStatus.value(), parsedMarket.value(),
                parsedPage.value(), parsedSize.value());
    }

    private static Parsed<MarketCode> marketCode(final String raw) {
        if (raw == null) {
            return Parsed.valid(null);
        }
        return MarketCode.parse(raw).map(Parsed::valid).orElseGet(() ->
                Parsed.invalid(new FieldViolation(MARKET, MARKET_FORMAT)));
    }

    private static <E extends Enum<E>> Parsed<E> enumValue(final Class<E> type,
                                                           final String field,
                                                           final String raw) {
        if (raw == null) {
            return Parsed.valid(null);
        }
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.name().equals(raw))
                .findFirst()
                .map(Parsed::valid)
                .orElseGet(() -> Parsed.invalid(new FieldViolation(field,
                        ONE_OF.formatted(Arrays.toString(type.getEnumConstants())))));
    }

    private static Parsed<Integer> number(final String field, final String raw,
                                          final int fallback, final IntPredicate accepted,
                                          final String message) {
        if (raw == null) {
            return Parsed.valid(fallback);
        }
        if (DIGITS.matcher(raw).matches() && accepted.test(Integer.parseInt(raw))) {
            return Parsed.valid(Integer.parseInt(raw));
        }
        return Parsed.invalid(new FieldViolation(field, message));
    }

    private record Parsed<T>(T value, FieldViolation violation) {

        static <T> Parsed<T> valid(final T value) {
            return new Parsed<>(value, null);
        }

        static <T> Parsed<T> invalid(final FieldViolation violation) {
            return new Parsed<>(null, violation);
        }

        static List<FieldViolation> violations(final Parsed<?>... parsed) {
            return Stream.of(parsed).map(Parsed::violation).filter(Objects::nonNull).toList();
        }
    }
}
