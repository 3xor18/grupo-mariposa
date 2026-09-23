package com.grupomariposa.orders.infrastructure.persistence;

import com.mongodb.MongoException;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.mongodb.TransientClientSessionException;
import org.springframework.data.mongodb.TransientMongoDbException;
import org.springframework.transaction.TransactionException;

final class MongoErrors {

    private static final int WRITE_CONFLICT = 112;
    private static final Set<String> TRANSIENT_LABELS = Set.of(
            MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL,
            MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);

    private MongoErrors() {
    }

    static boolean isDatabaseFailure(final Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof DataAccessException || cause instanceof MongoException
                    || cause instanceof TransactionException) {
                return true;
            }
        }
        return false;
    }

    static boolean isTransient(final Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (isTransientCause(cause)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTransientCause(final Throwable cause) {
        if (cause instanceof TransientMongoDbException
                || cause instanceof TransientClientSessionException
                || cause instanceof TransientDataAccessException) {
            return true;
        }
        return cause instanceof MongoException mongo && (mongo.getCode() == WRITE_CONFLICT
                || mongo.getErrorLabels().stream().anyMatch(TRANSIENT_LABELS::contains));
    }

    static String describe(final Throwable failure) {
        return failure.getClass().getSimpleName();
    }
}
