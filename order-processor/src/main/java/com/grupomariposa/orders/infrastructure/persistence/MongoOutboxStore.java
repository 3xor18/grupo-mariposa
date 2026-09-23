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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    private static final int SINGLE_DOCUMENT = 1;

    private final MongoTemplate mongo;

    public MongoOutboxStore(final MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public List<PendingEvent> claim(final int limit, final Instant now, final Instant leaseUntil,
                                    final String owner) {
        final List<PendingEvent> claimed = new ArrayList<>();
        final Set<String> ordersInBatch = new HashSet<>();
        for (final OutboxDocument candidate : candidates(limit, now)) {
            if (claimed.size() >= limit) {
                break;
            }
            if (!ordersInBatch.contains(candidate.orderId()) && !hasOlderUnpublished(candidate)) {
                lease(candidate, now, leaseUntil, owner).ifPresent(document -> {
                    claimed.add(toPending(document));
                    ordersInBatch.add(document.orderId());
                });
            }
        }
        return claimed;
    }

    @Override
    public boolean markPublished(final String eventId, final String owner,
                                 final Instant publishedAt) {
        return mongo.updateFirst(heldBy(eventId, owner), new Update()
                .set(Fields.STATUS, OutboxStatus.PUBLISHED.name())
                .set(Fields.PUBLISHED_AT, publishedAt)
                .unset(Fields.LEASE_UNTIL)
                .unset(Fields.LEASE_OWNER), OutboxDocument.class)
                .getModifiedCount() == SINGLE_DOCUMENT;
    }

    @Override
    public boolean release(final String eventId, final String owner, final int attempts,
                           final Instant availableAt) {
        return mongo.updateFirst(heldBy(eventId, owner), new Update()
                .set(Fields.STATUS, OutboxStatus.PENDING.name())
                .set(Fields.ATTEMPTS, attempts)
                .set(Fields.AVAILABLE_AT, availableAt)
                .unset(Fields.LEASE_UNTIL)
                .unset(Fields.LEASE_OWNER), OutboxDocument.class)
                .getModifiedCount() == SINGLE_DOCUMENT;
    }

    public long countUnpublished() {
        return mongo.count(query(where(Fields.STATUS).in(UNPUBLISHED)), OutboxDocument.class);
    }

    public Optional<Instant> oldestUnpublishedCreatedAt() {
        final Query oldest = query(where(Fields.STATUS).in(UNPUBLISHED)).with(OLDEST_FIRST);
        return Optional.ofNullable(mongo.findOne(oldest, OutboxDocument.class))
                .map(OutboxDocument::createdAt);
    }

    private List<OutboxDocument> candidates(final int limit, final Instant now) {
        return mongo.find(new Query(claimable(now)).with(OLDEST_FIRST).limit(limit),
                OutboxDocument.class);
    }

    private boolean hasOlderUnpublished(final OutboxDocument candidate) {
        return mongo.exists(query(where(Fields.ORDER_ID).is(candidate.orderId())
                .and(Fields.EVENT_VERSION).lt(candidate.eventVersion())
                .and(Fields.STATUS).in(UNPUBLISHED)), OutboxDocument.class);
    }

    private Optional<OutboxDocument> lease(final OutboxDocument candidate, final Instant now,
                                           final Instant leaseUntil, final String owner) {
        final Query stillClaimable = new Query(new Criteria().andOperator(
                where(Fields.ID).is(candidate.id()), claimable(now)));
        final Update lease = new Update()
                .set(Fields.STATUS, OutboxStatus.IN_FLIGHT.name())
                .set(Fields.LEASE_UNTIL, leaseUntil)
                .set(Fields.LEASE_OWNER, owner);
        return Optional.ofNullable(mongo.findAndModify(stillClaimable, lease,
                FindAndModifyOptions.options().returnNew(true), OutboxDocument.class));
    }

    private static Criteria claimable(final Instant now) {
        return new Criteria().orOperator(
                where(Fields.STATUS).is(OutboxStatus.PENDING.name())
                        .and(Fields.AVAILABLE_AT).lte(now),
                where(Fields.STATUS).is(OutboxStatus.IN_FLIGHT.name())
                        .and(Fields.LEASE_UNTIL).lt(now));
    }

    private static Query heldBy(final String eventId, final String owner) {
        return query(where(Fields.ID).is(eventId)
                .and(Fields.LEASE_OWNER).is(owner)
                .and(Fields.STATUS).is(OutboxStatus.IN_FLIGHT.name()));
    }

    private static PendingEvent toPending(final OutboxDocument document) {
        return new PendingEvent(document.id(), document.orderId(), document.topic(),
                document.key(), document.payload(), document.attempts());
    }
}
