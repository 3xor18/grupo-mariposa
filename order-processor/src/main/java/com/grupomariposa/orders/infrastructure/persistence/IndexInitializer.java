package com.grupomariposa.orders.infrastructure.persistence;

import com.grupomariposa.orders.infrastructure.persistence.document.Fields;
import com.grupomariposa.orders.infrastructure.persistence.document.InboxDocument;
import com.grupomariposa.orders.infrastructure.persistence.document.OrderDocument;
import com.grupomariposa.orders.infrastructure.persistence.document.OutboxDocument;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;

public final class IndexInitializer implements SmartInitializingSingleton {

    private static final Logger LOG = LoggerFactory.getLogger(IndexInitializer.class);
    private static final String INBOX_TTL = "inbox_received_ttl";
    private static final String OUTBOX_PENDING = "outbox_status_created";
    private static final String OUTBOX_TTL = "outbox_published_ttl";
    private static final String OUTBOX_BY_ORDER = "outbox_order_version";
    private static final String ORDERS_BY_STATUS = "orders_status_processed";
    private static final String ORDERS_BY_MARKET = "orders_market_processed";
    private static final String ORDERS_BY_CLIENT = "orders_client";
    private static final String ORDERS_BY_PROCESSED = "orders_processed";
    private static final String COLL_MOD = "collMod";
    private static final String INDEX = "index";
    private static final String NAME = "name";
    private static final String EXPIRE_AFTER_SECONDS = "expireAfterSeconds";

    private final MongoTemplate mongo;
    private final PersistenceProperties properties;

    public IndexInitializer(final MongoTemplate mongo, final PersistenceProperties properties) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void afterSingletonsInstantiated() {
        createOrderIndexes(mongo.indexOps(OrderDocument.class));
        ensureTtl(InboxDocument.COLLECTION, INBOX_TTL, Fields.RECEIVED_AT,
                properties.inboxRetention());
        final IndexOperations outbox = mongo.indexOps(OutboxDocument.class);
        outbox.createIndex(new Index().on(Fields.STATUS, Sort.Direction.ASC)
                .on(Fields.CREATED_AT, Sort.Direction.ASC).named(OUTBOX_PENDING));
        outbox.createIndex(new Index().on(Fields.ORDER_ID, Sort.Direction.ASC)
                .on(Fields.EVENT_VERSION, Sort.Direction.ASC).named(OUTBOX_BY_ORDER));
        ensureTtl(OutboxDocument.COLLECTION, OUTBOX_TTL, Fields.PUBLISHED_AT,
                properties.outboxRetention());
        LOG.info("MongoDB indexes verified");
    }

    private void ensureTtl(final String collection, final String name, final String field,
                           final Duration retention) {
        final Optional<IndexInfo> existing = mongo.indexOps(collection).getIndexInfo().stream()
                .filter(index -> name.equals(index.getName()))
                .findFirst();
        if (existing.isEmpty()) {
            mongo.indexOps(collection).createIndex(new Index().on(field, Sort.Direction.ASC)
                    .expire(retention).named(name));
        } else if (!existing.get().getExpireAfter().filter(retention::equals).isPresent()) {
            mongo.executeCommand(new Document(COLL_MOD, collection).append(INDEX,
                    new Document(NAME, name).append(EXPIRE_AFTER_SECONDS, retention.toSeconds())));
            LOG.info("TTL index {} changed to {}", name, retention);
        }
    }

    private static void createOrderIndexes(final IndexOperations orders) {
        orders.createIndex(new Index().on(Fields.STATUS, Sort.Direction.ASC)
                .on(Fields.PROCESSED_AT, Sort.Direction.DESC).named(ORDERS_BY_STATUS));
        orders.createIndex(new Index().on(Fields.MARKET, Sort.Direction.ASC)
                .on(Fields.PROCESSED_AT, Sort.Direction.DESC).named(ORDERS_BY_MARKET));
        orders.createIndex(new Index().on(Fields.CLIENT_ID, Sort.Direction.ASC)
                .named(ORDERS_BY_CLIENT));
        orders.createIndex(new Index().on(Fields.PROCESSED_AT, Sort.Direction.DESC)
                .named(ORDERS_BY_PROCESSED));
    }
}
