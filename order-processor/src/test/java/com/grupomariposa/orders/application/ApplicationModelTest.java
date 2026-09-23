package com.grupomariposa.orders.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.grupomariposa.orders.application.error.ErrorCategory;
import com.grupomariposa.orders.application.error.ExternalPermanentException;
import com.grupomariposa.orders.application.error.ExternalTransientException;
import com.grupomariposa.orders.application.error.PersistenceException;
import com.grupomariposa.orders.application.query.OrderSearchCriteria;
import com.grupomariposa.orders.application.query.PageResult;
import com.grupomariposa.orders.application.service.RelaySettings;
import com.grupomariposa.orders.domain.Markets;
import com.grupomariposa.orders.domain.model.OrderStatus;
import java.time.Duration;
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
                new OrderSearchCriteria(OrderStatus.APPROVED, Markets.PE, 1, 5);

        assertThat(criteria.statusFilter()).contains(OrderStatus.APPROVED);
        assertThat(criteria.marketFilter()).contains(Markets.PE);
        assertThat(new OrderSearchCriteria(null, null, 0, 1).statusFilter()).isEmpty();
    }

    @Test
    void should_reject_empty_page_size() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PageResult<>(List.of(), 0, 0, 0));
    }

    @ParameterizedTest
    @CsvSource({"-1, 20", "0, 0"})
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
        final RelaySettings settings = new RelaySettings(1, Duration.ofSeconds(2),
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(60), "n");

        assertThat(settings.backoffFor(attempts)).isEqualTo(Duration.ofSeconds(seconds));
    }

    @ParameterizedTest(name = "batch={0} lease={1}s send={2}s backoff={3}..{4}s owner={5}")
    @CsvSource({
        "0, 30, 10, 1, 60, n",
        "1, 0, 10, 1, 60, n",
        "1, 30, -1, 1, 60, n",
        "1, 10, 10, 1, 60, n",
        "1, 30, 10, 90, 60, n",
        "1, 30, 10, 0, 60, n",
        "1, 30, 10, 1, 0, n",
        "1, 30, 10, 1, 60, ' '"
    })
    void should_reject_unsafe_relay_settings(final int batch, final long lease, final long send,
                                             final long initial, final long max,
                                             final String owner) {
        assertThatIllegalArgumentException().isThrownBy(() -> new RelaySettings(batch,
                Duration.ofSeconds(lease), Duration.ofSeconds(send), Duration.ofSeconds(initial),
                Duration.ofSeconds(max), owner));
    }
}
