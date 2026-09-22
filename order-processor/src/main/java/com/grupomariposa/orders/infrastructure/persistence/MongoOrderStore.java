package com.grupomariposa.orders.infrastructure.persistence;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.grupomariposa.orders.application.error.PersistenceException;
import com.grupomariposa.orders.application.port.out.InboxEntry;
import com.grupomariposa.orders.application.port.out.InboxOutcome;
import com.grupomariposa.orders.application.port.out.OrderStore;
import com.grupomariposa.orders.application.port.out.SaveResult;
import com.grupomariposa.orders.application.port.out.StoredOrderState;
import com.grupomariposa.orders.domain.model.Order;
import com.grupomariposa.orders.domain.model.OrderStatus;
import com.grupomariposa.orders.infrastructure.persistence.document.Fields;
import com.grupomariposa.orders.infrastructure.persistence.document.InboxDocument;
import com.grupomariposa.orders.infrastructure.persistence.document.OrderDocument;
import com.grupomariposa.orders.infrastructure.persistence.document.OutboxDocument;
import com.grupomariposa.orders.infrastructure.persistence.document.OutboxStatus;
import com.grupomariposa.orders.infrastructure.persistence.mapping.OrderDocumentMapper;
import com.grupomariposa.orders.infrastructure.messaging.OutboxPayloadFactory;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.bson.Document;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.TransactionStatus;

public final class MongoOrderStore implements OrderStore {

    private static final String READ_FAILED = "MongoDB read failed: %s";

    private final MongoTemplate mongo;
    private final TransactionRunner transactions;
    private final OrderDocumentMapper mapper;
    private final OutboxPayloadFactory payloads;
    private final String outputTopic;

    public MongoOrderStore(final MongoTemplate mongo, final TransactionRunner transactions,
                           final OrderDocumentMapper mapper, final OutboxPayloadFactory payloads,
                           final String outputTopic) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.payloads = Objects.requireNonNull(payloads, "payloads");
        this.outputTopic = Objects.requireNonNull(outputTopic, "outputTopic");
    }

    @Override
    public boolean inboxContains(final String eventId) {
        return read(() -> mongo.exists(byId(eventId), InboxDocument.class));
    }

    @Override
    public Optional<StoredOrderState> findState(final String orderId) {
        final Query query = byId(orderId);
        query.fields().include(Fields.EVENT_VERSION, Fields.SOURCE_EVENT_ID, Fields.STATUS);
        return read(() -> Optional.ofNullable(
                mongo.findOne(query, Document.class, OrderDocument.COLLECTION))
                .map(MongoOrderStore::stateOf));
    }

    @Override
    public SaveResult save(final Order order, final String outboxEventId) {
        final WriteOutcome outcome = transactions.inTransaction(status ->
                writeDecision(order, outboxEventId, status));
        return switch (outcome) {
            case WRITTEN -> new SaveResult.Saved();
            case DUPLICATE_EVENT -> new SaveResult.DuplicateEvent();
            case SUPERSEDED -> new SaveResult.Superseded(currentState(order.orderId()));
        };
    }

    @Override
    public boolean saveTechnicalFailure(final Order order) {
        final Query guard = query(where(Fields.ID).is(order.orderId()).orOperator(
                where(Fields.EVENT_VERSION).lt(order.eventVersion()),
                where(Fields.EVENT_VERSION).is(order.eventVersion())
                        .and(Fields.SOURCE_EVENT_ID).is(order.sourceEventId())
                        .and(Fields.STATUS).is(OrderStatus.TECHNICAL_FAILURE.name())));
        try {
            mongo.findAndReplace(guard, mapper.toDocument(order),
                    FindAndReplaceOptions.options().upsert());
            return true;
        } catch (DuplicateKeyException terminalResultExists) {
            return false;
        } catch (DataAccessException failure) {
            throw TransactionRunner.translate(failure, 1);
        }
    }

    @Override
    public void recordInbox(final InboxEntry entry) {
        try {
            mongo.insert(new InboxDocument(entry.eventId(), entry.orderId(),
                    entry.eventVersion(), entry.outcome().name(), entry.receivedAt()));
        } catch (DuplicateKeyException alreadyRecorded) {
            return;
        } catch (DataAccessException failure) {
            throw TransactionRunner.translate(failure, 1);
        }
    }

    private WriteOutcome writeDecision(final Order order, final String outboxEventId,
                                       final TransactionStatus status) {
        try {
            mongo.insert(new InboxDocument(order.sourceEventId(), order.orderId(),
                    order.eventVersion(), InboxOutcome.valueOf(order.status().name()).name(),
                    order.timeline().receivedAt()));
        } catch (DuplicateKeyException duplicate) {
            status.setRollbackOnly();
            return WriteOutcome.DUPLICATE_EVENT;
        }
        try {
            mongo.findAndReplace(decisionGuard(order), mapper.toDocument(order),
                    FindAndReplaceOptions.options().upsert());
        } catch (DuplicateKeyException superseded) {
            status.setRollbackOnly();
            return WriteOutcome.SUPERSEDED;
        }
        mongo.insert(outboxDocument(order, outboxEventId));
        return WriteOutcome.WRITTEN;
    }

    private static Query decisionGuard(final Order order) {
        return query(where(Fields.ID).is(order.orderId()).orOperator(
                where(Fields.EVENT_VERSION).lt(order.eventVersion()),
                new Criteria().andOperator(
                        where(Fields.EVENT_VERSION).is(order.eventVersion()),
                        where(Fields.SOURCE_EVENT_ID).is(order.sourceEventId()),
                        where(Fields.STATUS).is(OrderStatus.TECHNICAL_FAILURE.name()))));
    }

    private OutboxDocument outboxDocument(final Order order, final String eventId) {
        return new OutboxDocument(eventId, order.orderId(), order.eventVersion(), outputTopic,
                order.orderId(), payloads.create(order, eventId), OutboxStatus.PENDING, 0, null,
                null, order.processedAt(), order.processedAt(), null);
    }

    private StoredOrderState currentState(final String orderId) {
        return findState(orderId).orElseThrow(() ->
                new PersistenceException("Order disappeared while classifying a conflict", null));
    }

    private static StoredOrderState stateOf(final Document document) {
        return new StoredOrderState(document.getInteger(Fields.EVENT_VERSION),
                document.getString(Fields.SOURCE_EVENT_ID),
                OrderStatus.valueOf(document.getString(Fields.STATUS)));
    }

    private static Query byId(final String id) {
        return query(where(Fields.ID).is(id));
    }

    private static <T> T read(final Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException failure) {
            throw new PersistenceException(READ_FAILED.formatted(MongoErrors.describe(failure)),
                    failure);
        }
    }

    private enum WriteOutcome {
        WRITTEN,
        DUPLICATE_EVENT,
        SUPERSEDED
    }
}
