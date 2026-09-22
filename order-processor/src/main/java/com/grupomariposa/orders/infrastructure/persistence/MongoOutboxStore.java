package com.grupomariposa.orders.infrastructure.persistence;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.grupomariposa.orders.application.port.out.OutboxStore;
import com.grupomariposa.orders.application.port.out.PendingEvent;
import com.grupomariposa.orders.infrastructure.persistence.document.Fields;
import com.grupomariposa.orders.infrastructure.persistence.document.OutboxDocument;
import com.grupomariposa.orders.infrastructure.persistence.document.OutboxStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public final class MongoOutboxStore implements OutboxStore {

    private static final Sort OLDEST_FIRST = Sort.by(Sort.Direction.ASC, Fields.CREATED_AT);
    private static final List<String> UNPUBLISHED =
            List.of(OutboxStatus.PENDING.name(), OutboxStatus.IN_FLIGHT.name());

    private final MongoTemplate mongo;

    public MongoOutboxStore(final MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public List<PendingEvent> claim(final int limit, final Instant now, final Instant leaseUntil,
                                    final String owner) {
        final List<PendingEvent> claimed = new ArrayList<>();
        Optional<OutboxDocument> next = claimOne(now, leaseUntil, owner);
        while (next.isPresent()) {
            claimed.add(toPending(next.get()));
            next = claimed.size() < limit ? claimOne(now, leaseUntil, owner) : Optional.empty();
        }
        return claimed;
    }

    @Override
    public void markPublished(final String eventId, final Instant publishedAt) {
        mongo.updateFirst(query(where(Fields.ID).is(eventId)), new Update()
                .set(Fields.STATUS, OutboxStatus.PUBLISHED.name())
                .set(Fields.PUBLISHED_AT, publishedAt)
                .unset(Fields.LEASE_UNTIL)
                .unset(Fields.LEASE_OWNER), OutboxDocument.class);
    }

    @Override
    public void release(final String eventId, final int attempts, final Instant availableAt) {
        mongo.updateFirst(query(where(Fields.ID).is(eventId)), new Update()
                .set(Fields.STATUS, OutboxStatus.PENDING.name())
                .set(Fields.ATTEMPTS, attempts)
                .set(Fields.AVAILABLE_AT, availableAt)
                .unset(Fields.LEASE_UNTIL)
                .unset(Fields.LEASE_OWNER), OutboxDocument.class);
    }

    public long countUnpublished() {
        return mongo.count(query(where(Fields.STATUS).in(UNPUBLISHED)), OutboxDocument.class);
    }

    public Optional<Instant> oldestUnpublishedCreatedAt() {
        final Query oldest = query(where(Fields.STATUS).in(UNPUBLISHED)).with(OLDEST_FIRST);
        return Optional.ofNullable(mongo.findOne(oldest, OutboxDocument.class))
                .map(OutboxDocument::createdAt);
    }

    private Optional<OutboxDocument> claimOne(final Instant now, final Instant leaseUntil,
                                              final String owner) {
        final Query claimable = new Query(new Criteria().orOperator(
                where(Fields.STATUS).is(OutboxStatus.PENDING.name())
                        .and(Fields.AVAILABLE_AT).lte(now),
                where(Fields.STATUS).is(OutboxStatus.IN_FLIGHT.name())
                        .and(Fields.LEASE_UNTIL).lt(now)))
                .with(OLDEST_FIRST);
        final Update lease = new Update()
                .set(Fields.STATUS, OutboxStatus.IN_FLIGHT.name())
                .set(Fields.LEASE_UNTIL, leaseUntil)
                .set(Fields.LEASE_OWNER, owner);
        return Optional.ofNullable(mongo.findAndModify(claimable, lease,
                FindAndModifyOptions.options().returnNew(true), OutboxDocument.class));
    }

    private static PendingEvent toPending(final OutboxDocument document) {
        return new PendingEvent(document.id(), document.orderId(), document.topic(),
                document.key(), document.payload(), document.attempts());
    }
}
