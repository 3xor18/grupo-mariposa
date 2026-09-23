package com.grupomariposa.orders.infrastructure.kafka.masterdata;

import com.grupomariposa.orders.infrastructure.kafka.MasterDataProperties;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

public final class MasterDataErrorHandlers {

    private MasterDataErrorHandlers() {
    }

    public static DefaultErrorHandler create(final MasterDataProperties.Retry retry,
                                             final ConsumerRecordRecoverer recoverer) {
        final DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff(retry));
        handler.defaultFalse();
        handler.addRetryableExceptions(CacheUpdateFailure.class);
        return handler;
    }

    static ExponentialBackOffWithMaxRetries backOff(final MasterDataProperties.Retry retry) {
        final ExponentialBackOffWithMaxRetries backOff =
                new ExponentialBackOffWithMaxRetries(retry.maxAttempts() - 1);
        backOff.setInitialInterval(retry.initialInterval().toMillis());
        backOff.setMultiplier(retry.multiplier());
        backOff.setMaxInterval(retry.maxInterval().toMillis());
        return backOff;
    }
}
