package com.grupomariposa.orders.application.validation;

import static com.grupomariposa.orders.application.ApplicationFixtures.RECEPTION;
import static com.grupomariposa.orders.application.ApplicationFixtures.goldenCommand;
import static com.grupomariposa.orders.application.ApplicationFixtures.goldenSubmission;
import static org.assertj.core.api.Assertions.assertThat;

import com.grupomariposa.orders.domain.DomainFixtures;
import com.grupomariposa.orders.domain.model.Currency;
import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.MarketCurrencies;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OrderCommandValidatorTest {

    private static final String REQUIRED = "is required";

    private final OrderCommandValidator validator =
            new OrderCommandValidator(DomainFixtures.MARKETS);

    @Test
    void should_build_command_when_submission_is_valid() {
        assertThat(validator.validate(goldenSubmission(), RECEPTION))
                .isEqualTo(new ValidationResult.Valid(goldenCommand()));
    }

    @Test
    void should_default_event_version_and_accept_missing_optional_fields() {
        final ValidationResult result = validator.validate(with(order ->
                new UnvalidatedOrder(order.eventId(), null, null, order.orderId(), "CO", "COP",
                        order.clientId(), null, order.items())), RECEPTION);

        assertThat(result).isInstanceOfSatisfying(ValidationResult.Valid.class, valid -> {
            assertThat(valid.command().eventVersion())
                    .isEqualTo(OrderCommandValidator.DEFAULT_EVENT_VERSION);
            assertThat(valid.command().occurredAt()).isNull();
            assertThat(valid.command().channel()).isNull();
        });
    }

    @Test
    void should_accept_zero_unit_price() {
        assertThat(validator.validate(withItems(List.of(
                new UnvalidatedItem("PRD-1", 1L, BigDecimal.ZERO))), RECEPTION))
                .isInstanceOf(ValidationResult.Valid.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void should_require_identifiers(final String blank) {
        final ValidationResult result = validator.validate(with(order ->
                new UnvalidatedOrder(blank, 1, null, blank, "MX", "MXN", blank, null,
                        order.items())), RECEPTION);

        assertThat(errors(result)).containsExactly(
                new ValidationError("eventId", REQUIRED),
                new ValidationError("orderId", REQUIRED),
                new ValidationError("clientId", REQUIRED));
    }

    @Test
    void should_limit_identifier_and_channel_lengths() {
        final String longId = "X".repeat(OrderCommandValidator.MAX_ID_LENGTH + 1);
        final String longChannel = "C".repeat(OrderCommandValidator.MAX_CHANNEL_LENGTH + 1);

        final ValidationResult result = validator.validate(with(order ->
                new UnvalidatedOrder(longId, 1, null, longId, "MX", "MXN", longId, longChannel,
                        order.items())), RECEPTION);

        assertThat(fields(result)).containsExactly("eventId", "orderId", "clientId", "channel");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void should_reject_event_version_below_one(final int version) {
        assertThat(fields(validator.validate(with(order -> new UnvalidatedOrder(order.eventId(),
                version, null, order.orderId(), "MX", "MXN", order.clientId(), null,
                order.items())), RECEPTION))).containsExactly("eventVersion");
    }

    @ParameterizedTest
    @ValueSource(strings = {"yesterday", "2026-09-18", "2026-09-18T15:42:10"})
    void should_reject_malformed_occurred_at(final String occurredAt) {
        assertThat(fields(validator.validate(with(order -> new UnvalidatedOrder(order.eventId(),
                1, occurredAt, order.orderId(), "MX", "MXN", order.clientId(), null,
                order.items())), RECEPTION))).containsExactly("occurredAt");
    }

    @Test
    void should_require_market_and_currency() {
        assertThat(errors(validator.validate(marketAndCurrency(null, null), RECEPTION)))
                .containsExactly(new ValidationError("market", REQUIRED),
                        new ValidationError("currency", REQUIRED));
    }

    @Test
    void should_reject_unsupported_market_and_currency() {
        assertThat(errors(validator.validate(marketAndCurrency("AR", "ARS"), RECEPTION)))
                .containsExactly(new ValidationError("market", "must be one of [MX, CO, PE]"),
                        new ValidationError("currency", "must be one of [MXN, COP, PEN]"));
    }

    @Test
    void should_reject_currency_that_does_not_match_market() {
        assertThat(errors(validator.validate(marketAndCurrency("PE", "MXN"), RECEPTION)))
                .containsExactly(new ValidationError("currency",
                        "does not match market PE (expected PEN)"));
    }

    @Test
    void should_only_accept_configured_markets_and_currencies() {
        final OrderCommandValidator mexicoOnly = new OrderCommandValidator(
                new MarketCurrencies(Map.of(Market.MX, Currency.MXN)));

        assertThat(errors(mexicoOnly.validate(marketAndCurrency("CO", "COP"), RECEPTION)))
                .containsExactly(new ValidationError("market", "must be one of [MX]"));
        assertThat(mexicoOnly.validate(goldenSubmission(), RECEPTION))
                .isInstanceOf(ValidationResult.Valid.class);
    }

    @Test
    void should_require_items() {
        assertThat(errors(validator.validate(withItems(null), RECEPTION)))
                .containsExactly(new ValidationError("items", REQUIRED));
    }

    @Test
    void should_require_at_least_one_item() {
        assertThat(errors(validator.validate(withItems(List.of()), RECEPTION)))
                .containsExactly(new ValidationError("items", "must contain at least one item"));
    }

    @Test
    void should_limit_item_count() {
        final List<UnvalidatedItem> items = IntStream
                .rangeClosed(0, OrderCommandValidator.MAX_ITEMS)
                .mapToObj(index -> new UnvalidatedItem("PRD-" + index, 1L, BigDecimal.ONE))
                .toList();

        assertThat(errors(validator.validate(withItems(items), RECEPTION)))
                .containsExactly(new ValidationError("items", "must contain at most 500 items"));
    }

    @Test
    void should_reject_null_item() {
        assertThat(errors(validator.validate(withItems(Collections.singletonList(null)),
                RECEPTION))).containsExactly(new ValidationError("items[0]", REQUIRED));
    }

    @Test
    void should_reject_duplicated_product() {
        assertThat(errors(validator.validate(withItems(List.of(
                new UnvalidatedItem("PRD-1", 1L, BigDecimal.ONE),
                new UnvalidatedItem("PRD-1", 2L, BigDecimal.ONE))), RECEPTION)))
                .containsExactly(new ValidationError("items[1].productId",
                        "is duplicated within the order"));
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, 2_147_483_648L})
    void should_reject_non_positive_or_oversized_quantity(final long quantity) {
        assertThat(errors(validator.validate(withItems(List.of(
                new UnvalidatedItem("PRD-1", quantity, BigDecimal.ONE))), RECEPTION)))
                .containsExactly(new ValidationError("items[0].quantity",
                        "must be an integer greater than 0"));
    }

    @Test
    void should_reject_negative_unit_price() {
        assertThat(errors(validator.validate(withItems(List.of(
                new UnvalidatedItem("PRD-1", 1L, new BigDecimal("-0.01")))), RECEPTION)))
                .containsExactly(new ValidationError("items[0].unitPrice",
                        "must be greater than or equal to 0"));
    }

    @Test
    void should_collect_every_item_error() {
        final List<UnvalidatedItem> items = new ArrayList<>();
        items.add(new UnvalidatedItem(null, null, null));
        items.add(new UnvalidatedItem(" ", 3L, BigDecimal.TEN));

        final ValidationResult result = validator.validate(withItems(items), RECEPTION);

        assertThat(fields(result)).containsExactly("items[0].productId", "items[0].quantity",
                "items[0].unitPrice", "items[1].productId");
        assertThat(((ValidationResult.Invalid) result).summary()).isEqualTo(
                "items[0].productId: is required; items[0].quantity: is required; "
                        + "items[0].unitPrice: is required; items[1].productId: is required");
    }

    private static UnvalidatedOrder with(final UnaryOperator<UnvalidatedOrder> change) {
        return change.apply(goldenSubmission());
    }

    private static UnvalidatedOrder withItems(final List<UnvalidatedItem> items) {
        return with(order -> new UnvalidatedOrder(order.eventId(), order.eventVersion(),
                order.occurredAt(), order.orderId(), order.market(), order.currency(),
                order.clientId(), order.channel(), items));
    }

    private static UnvalidatedOrder marketAndCurrency(final String market, final String currency) {
        return with(order -> new UnvalidatedOrder(order.eventId(), 1, null, order.orderId(),
                market, currency, order.clientId(), null, order.items()));
    }

    private static List<ValidationError> errors(final ValidationResult result) {
        assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
        return ((ValidationResult.Invalid) result).errors();
    }

    private static List<String> fields(final ValidationResult result) {
        return errors(result).stream().map(ValidationError::field).toList();
    }
}
