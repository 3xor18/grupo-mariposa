package com.grupomariposa.orders.infrastructure.persistence;

import com.grupomariposa.orders.infrastructure.persistence.document.Fields;
import com.grupomariposa.orders.infrastructure.persistence.document.InboxDocument;
import com.grupomariposa.orders.infrastructure.persistence.document.OrderDocument;
import com.grupomariposa.orders.infrastructure.persistence.document.OutboxDocument;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
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

    private final MongoTemplate mongo;
    private final PersistenceProperties properties;

    public IndexInitializer(final MongoTemplate mongo, final PersistenceProperties properties) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void afterSingletonsInstantiated() {
        createOrderIndexes(mongo.indexOps(OrderDocument.class));
        mongo.indexOps(InboxDocument.class).createIndex(new Index()
                .on(Fields.RECEIVED_AT, Sort.Direction.ASC)
                .expire(properties.inboxRetention())
                .named(INBOX_TTL));
        final IndexOperations outbox = mongo.indexOps(OutboxDocument.class);
        outbox.createIndex(new Index().on(Fields.STATUS, Sort.Direction.ASC)
                .on(Fields.CREATED_AT, Sort.Direction.ASC).named(OUTBOX_PENDING));
        outbox.createIndex(new Index().on(Fields.ORDER_ID, Sort.Direction.ASC)
                .on(Fields.EVENT_VERSION, Sort.Direction.ASC).named(OUTBOX_BY_ORDER));
        outbox.createIndex(new Index().on(Fields.PUBLISHED_AT, Sort.Direction.ASC)
                .expire(properties.outboxRetention()).named(OUTBOX_TTL));
        LOG.info("MongoDB indexes verified");
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
