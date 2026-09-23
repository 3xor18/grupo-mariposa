package com.grupomariposa.orders.infrastructure.kafka.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.grupomariposa.orders.infrastructure.kafka.MasterDataProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

class MasterDataErrorHandlersTest {

    private static final int ATTEMPTS = 3;

    private final List<ConsumerRecord<?, ?>> recovered = new ArrayList<>();
    private final Consumer<?, ?> consumer = mock(Consumer.class);
    private final MessageListenerContainer container = mock(MessageListenerContainer.class);
    private final ConsumerRecord<String, byte[]> consumerRecord =
            new ConsumerRecord<>("clients.changed.v1", 0, 7L, "CLI-1", new byte[0]);

    @Test
    void should_back_off_exponentially_with_a_cap_and_a_bounded_number_of_retries() {
        final BackOff backOff = MasterDataErrorHandlers.backOff(new MasterDataProperties.Retry(
                Duration.ofMillis(500), 2.0, Duration.ofSeconds(1), 4));
        final BackOffExecution execution = backOff.start();

        assertThat(List.of(execution.nextBackOff(), execution.nextBackOff(),
                execution.nextBackOff(), execution.nextBackOff()))
                .containsExactly(500L, 1000L, 1000L, BackOffExecution.STOP);
    }

    @Test
    void should_retry_cache_failures_and_recover_after_the_last_attempt() {
        final DefaultErrorHandler handler = handler();
        when(container.isRunning()).thenReturn(true);

        for (int attempt = 1; attempt < ATTEMPTS; attempt++) {
            assertThatThrownBy(() -> handle(handler, new CacheUpdateFailure("down")))
                    .isNotNull();
            assertThat(recovered).isEmpty();
        }
        handle(handler, new CacheUpdateFailure("down"));

        assertThat(recovered).containsExactly(consumerRecord);
    }

    @Test
    void should_skip_unexpected_failures_without_retrying() {
        handle(handler(), new IllegalStateException("bug"));

        assertThat(recovered).containsExactly(consumerRecord);
    }

    private DefaultErrorHandler handler() {
        return MasterDataErrorHandlers.create(new MasterDataProperties.Retry(Duration.ofMillis(1),
                1.0, Duration.ofMillis(1), ATTEMPTS), (failed, cause) -> recovered.add(failed));
    }

    private void handle(final DefaultErrorHandler handler, final RuntimeException failure) {
        handler.handleRemaining(failure, List.of(consumerRecord), consumer, container);
    }
}
