package com.grupomariposa.orders.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.grupomariposa.orders.application.error.ErrorCategory;
import com.grupomariposa.orders.application.error.ExternalPermanentException;
import com.grupomariposa.orders.application.error.ExternalTransientException;
import com.grupomariposa.orders.application.error.PersistenceException;
import com.grupomariposa.orders.application.query.OrderSearchCriteria;
import com.grupomariposa.orders.application.query.OrderSummary;
import com.grupomariposa.orders.application.query.PageResult;
import com.grupomariposa.orders.application.service.RelaySettings;
import com.grupomariposa.orders.domain.model.Currency;
import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.Money;
import com.grupomariposa.orders.domain.model.OrderStatus;
import com.grupomariposa.orders.domain.model.RejectionCode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ApplicationModelTest {

    @Test
    void should_classify_retryable_and_recordable_categories() {
        assertThat(ErrorCategory.EXTERNAL_TRANSIENT.isRetryable()).isTrue();
        assertThat(ErrorCategory.PERSISTENCE.isRetryable()).isTrue();
        assertThat(ErrorCategory.VALIDATION.isRetryable()).isFalse();
        assertThat(ErrorCategory.EXTERNAL_PERMANENT.recordsTechnicalFailure()).isTrue();
        assertThat(ErrorCategory.DESERIALIZATION.recordsTechnicalFailure()).isFalse();
        assertThat(ErrorCategory.VERSION_CONFLICT.recordsTechnicalFailure()).isFalse();
    }

    @Test
    void should_expose_exception_metadata() {
        final ExternalTransientException transientFailure = new ExternalTransientException(
                "clients-api", "429", Duration.ofSeconds(1), null);

        assertThat(transientFailure.dependency()).isEqualTo("clients-api");
        assertThat(transientFailure.retryAfter()).contains(Duration.ofSeconds(1));
        assertThat(new ExternalTransientException("x", "y", null).retryAfter()).isEmpty();
        assertThat(new ExternalPermanentException("products-api", "400", null).dependency())
                .isEqualTo("products-api");
        assertThat(new PersistenceException("down", null).category())
                .isEqualTo(ErrorCategory.PERSISTENCE);
    }

    @Test
    void should_expose_optional_search_filters() {
        final OrderSearchCriteria criteria =
                new OrderSearchCriteria(OrderStatus.APPROVED, Market.PE, 1, 5);

        assertThat(criteria.statusFilter()).contains(OrderStatus.APPROVED);
        assertThat(criteria.marketFilter()).contains(Market.PE);
        assertThat(new OrderSearchCriteria(null, null, 0, 1).statusFilter()).isEmpty();
    }

    @Test
    void should_carry_summary_fields() {
        final OrderSummary summary = new OrderSummary("ORD-1", OrderStatus.REJECTED, Market.MX,
                Currency.MXN, "CLI-1", 2, Money.ZERO, RejectionCode.CLIENT_NOT_FOUND,
                Instant.EPOCH);

        assertThat(summary.reason()).isEqualTo(RejectionCode.CLIENT_NOT_FOUND);
    }

    @ParameterizedTest
    @CsvSource({"-1, 20", "0, 0", "0, 101"})
    void should_reject_invalid_pages(final int page, final int size) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OrderSearchCriteria(null, null, page, size));
    }

    @ParameterizedTest
    @CsvSource({"0, 20, 0", "1, 20, 1", "20, 20, 1", "21, 20, 2"})
    void should_compute_total_pages(final long total, final int size, final int pages) {
        assertThat(new PageResult<>(List.of(), 0, size, total).totalPages()).isEqualTo(pages);
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "1, 1", "2, 2", "3, 4", "7, 60", "40, 60"})
    void should_grow_relay_backoff_exponentially_up_to_cap(final int attempts,
                                                            final long seconds) {
        final RelaySettings settings = new RelaySettings(1, Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(60), "n");

        assertThat(settings.backoffFor(attempts)).isEqualTo(Duration.ofSeconds(seconds));
    }

    @Test
    void should_reject_empty_relay_batches() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RelaySettings(0,
                Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, "n"));
    }
}
