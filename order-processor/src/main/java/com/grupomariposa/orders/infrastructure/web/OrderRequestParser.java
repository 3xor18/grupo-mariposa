package com.grupomariposa.orders.infrastructure.web;

import com.grupomariposa.orders.application.query.OrderSearchCriteria;
import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.OrderStatus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class OrderRequestParser {

    private static final Pattern ORDER_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$");
    private static final Pattern DIGITS = Pattern.compile("^\\d{1,9}$");
    private static final String ORDER_ID_FIELD = "orderId";
    private static final String STATUS = "status";
    private static final String MARKET = "market";
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
        final List<FieldViolation> violations = new ArrayList<>();
        final OrderStatus parsedStatus = enumValue(OrderStatus.class, STATUS, status, violations);
        final Market parsedMarket = enumValue(Market.class, MARKET, market, violations);
        final int parsedPage = number(page, FIRST_PAGE)
                .filter(value -> value >= 0).orElseGet(() -> invalid(PAGE, MIN_PAGE, violations));
        final int parsedSize = number(size, limits.defaultPageSize())
                .filter(value -> value >= 1 && value <= limits.maxPageSize())
                .orElseGet(() -> invalid(SIZE, SIZE_RANGE.formatted(limits.maxPageSize()),
                        violations));
        if (violations.isEmpty() && (long) parsedPage * parsedSize > limits.maxOffset()) {
            violations.add(new FieldViolation(PAGE, OFFSET_TOO_DEEP.formatted(limits.maxOffset())));
        }
        if (!violations.isEmpty()) {
            throw new InvalidRequestException(violations);
        }
        return new OrderSearchCriteria(parsedStatus, parsedMarket, parsedPage, parsedSize);
    }

    private static <E extends Enum<E>> E enumValue(final Class<E> type, final String field,
                                                   final String raw,
                                                   final List<FieldViolation> violations) {
        if (raw == null) {
            return null;
        }
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.name().equals(raw))
                .findFirst()
                .orElseGet(() -> {
                    violations.add(new FieldViolation(field,
                            ONE_OF.formatted(Arrays.toString(type.getEnumConstants()))));
                    return null;
                });
    }

    private static Optional<Integer> number(final String raw, final int fallback) {
        if (raw == null) {
            return Optional.of(fallback);
        }
        return DIGITS.matcher(raw).matches()
                ? Optional.of(Integer.parseInt(raw)) : Optional.empty();
    }

    private static int invalid(final String field, final String message,
                               final List<FieldViolation> violations) {
        violations.add(new FieldViolation(field, message));
        return 0;
    }
}
