package com.grupomariposa.orders.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grupomariposa.orders.application.error.PersistenceException;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;
import java.time.Duration;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class TransactionRunnerTest {

    private final TransactionTemplate template = mock(TransactionTemplate.class);
    private final TransactionRunner runner = new TransactionRunner(template, 3, Duration.ZERO);

    @Test
    void should_return_result_of_successful_transaction() {
        when(template.execute(any())).thenReturn("done");

        assertThat(runner.<String>inTransaction(status -> "ignored")).isEqualTo("done");
    }

    @Test
    void should_retry_transient_write_conflicts_until_success() {
        when(template.execute(any()))
                .thenThrow(writeConflict())
                .thenThrow(writeConflict())
                .thenReturn("done");

        assertThat(runner.<String>inTransaction(status -> "ignored")).isEqualTo("done");
        verify(template, times(3)).execute(any());
    }

    @Test
    void should_give_up_after_bounded_attempts() {
        when(template.execute(any())).thenThrow(writeConflict());

        assertThatThrownBy(() -> runner.<String>inTransaction(status -> "ignored"))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("after 3 attempts");
    }

    @Test
    void should_not_retry_non_transient_failures() {
        when(template.execute(any()))
                .thenThrow(new DataAccessResourceFailureException("mongo down"));

        assertThatThrownBy(() -> runner.<String>inTransaction(status -> "ignored"))
                .isInstanceOf(PersistenceException.class)
                .hasMessage("MongoDB operation failed: DataAccessResourceFailureException");
        verify(template).execute(any());
    }

    @Test
    void should_not_translate_non_database_failures() {
        final IllegalStateException original = new IllegalStateException("serializer");
        when(template.execute(any())).thenThrow(original);

        assertThatThrownBy(() -> runner.<String>inTransaction(status -> "ignored"))
                .isSameAs(original);
    }

    @Test
    void should_execute_callback_inside_template() {
        when(template.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<String>>getArgument(0).doInTransaction(null));

        assertThat(runner.<String>inTransaction(status -> "inside")).isEqualTo("inside");
    }

    @Test
    void should_recognise_transient_labels() {
        final MongoException labelled = new MongoException("tx");
        labelled.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);

        assertThat(MongoErrors.isTransient(new RuntimeException(labelled))).isTrue();
        assertThat(MongoErrors.isTransient(new MongoException("plain"))).isFalse();
        assertThat(MongoErrors.isTransient(new IllegalStateException())).isFalse();
        assertThat(MongoErrors.isDatabaseFailure(new RuntimeException(labelled))).isTrue();
        assertThat(MongoErrors.isDatabaseFailure(
                new TransactionSystemException("commit")))
                .isTrue();
        assertThat(MongoErrors.isDatabaseFailure(new IllegalStateException())).isFalse();
    }

    private static RuntimeException writeConflict() {
        final BsonDocument response = new BsonDocument("ok", new BsonInt32(0))
                .append("code", new BsonInt32(112))
                .append("errmsg", new BsonString("WriteConflict"));
        return new UncategorizedMongoDbException("conflict",
                new MongoCommandException(response, new ServerAddress()));
    }
}
