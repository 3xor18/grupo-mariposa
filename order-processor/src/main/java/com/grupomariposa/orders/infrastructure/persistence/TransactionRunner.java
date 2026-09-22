package com.grupomariposa.orders.infrastructure.persistence;

import com.grupomariposa.orders.application.error.PersistenceException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

public final class TransactionRunner {

    private static final String EXHAUSTED = "MongoDB transaction failed after %d attempts: %s";
    private static final String FAILED = "MongoDB operation failed: %s";
    private static final String INTERRUPTED = "Interrupted while retrying a transaction";

    private final TransactionTemplate transactionTemplate;
    private final int maxAttempts;
    private final Duration backoff;

    public TransactionRunner(final TransactionTemplate transactionTemplate,
                             final int maxAttempts, final Duration backoff) {
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "template");
        this.maxAttempts = maxAttempts;
        this.backoff = Objects.requireNonNull(backoff, "backoff");
    }

    public <T> T inTransaction(final Function<TransactionStatus, T> work) {
        int attempt = 1;
        while (true) {
            try {
                return transactionTemplate.execute(work::apply);
            } catch (RuntimeException failure) {
                if (!MongoErrors.isDatabaseFailure(failure)) {
                    throw failure;
                }
                if (!MongoErrors.isTransient(failure) || attempt >= maxAttempts) {
                    throw translate(failure, attempt);
                }
                pause(attempt++);
            }
        }
    }

    static PersistenceException translate(final RuntimeException failure, final int attempts) {
        final String message = attempts > 1
                ? EXHAUSTED.formatted(attempts, MongoErrors.describe(failure))
                : FAILED.formatted(MongoErrors.describe(failure));
        return new PersistenceException(message, failure);
    }

    private void pause(final int attempt) {
        final long base = backoff.toMillis() * attempt;
        try {
            Thread.sleep(base + ThreadLocalRandom.current().nextLong(base + 1));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new PersistenceException(INTERRUPTED, interrupted);
        }
    }
}
