package com.grupomariposa.orders.infrastructure.persistence;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.grupomariposa.orders.application.error.TaxRateConflictException;
import com.grupomariposa.orders.application.error.TaxRateNotFoundException;
import com.grupomariposa.orders.application.port.out.TaxRateRepository;
import com.grupomariposa.orders.application.port.out.TaxRateReviewAction;
import com.grupomariposa.orders.application.query.TaxRateFilter;
import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRatePeriod;
import com.grupomariposa.orders.domain.model.TaxRateProposal;
import com.grupomariposa.orders.domain.model.TaxRateStatus;
import com.grupomariposa.orders.infrastructure.persistence.document.Fields;
import com.grupomariposa.orders.infrastructure.persistence.document.TaxRateDocument;
import com.grupomariposa.orders.infrastructure.persistence.mapping.TaxRateDocumentMapper;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public final class MongoTaxRateRepository implements TaxRateRepository {

    private static final String KEY_SEPARATOR = ":";
    private static final String CONCURRENT_CHANGE = "Tax rate %s changed concurrently; retry";

    private final MongoTemplate mongo;
    private final TransactionRunner transactions;
    private final TaxRateDocumentMapper mapper;

    public MongoTaxRateRepository(final MongoTemplate mongo, final TransactionRunner transactions,
                                  final TaxRateDocumentMapper mapper) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public List<TaxRateProposal> find(final TaxRateFilter filter) {
        final Criteria criteria = new Criteria();
        if (filter.market() != null) {
            criteria.and(Fields.MARKET).is(filter.market().value());
        }
        if (filter.category() != null) {
            criteria.and(TaxRateDocument.CATEGORY).is(filter.category().name());
        }
        if (filter.status() != null) {
            criteria.and(Fields.STATUS).is(filter.status().name());
        }
        final Query query = query(criteria).with(Sort.by(Fields.MARKET, TaxRateDocument.CATEGORY)
                .and(Sort.by(Sort.Direction.DESC, TaxRateDocument.VALID_FROM)));
        return read(() -> mongo.find(query, TaxRateDocument.class).stream()
                .map(mapper::toDomain).toList());
    }

    @Override
    public TaxRateProposal insert(final TaxRateProposal proposal) {
        return read(() -> {
            mongo.insert(mapper.toDocument(proposal));
            return proposal;
        });
    }

    @Override
    public boolean hasApproved(final MarketCode market, final TaxCategory category) {
        return read(() -> mongo.exists(approvedOf(market, category), TaxRateDocument.class));
    }

    @Override
    public boolean insertSeed(final TaxRateProposal approved) {
        try {
            mongo.insert(mapper.toDocument(approved));
            return true;
        } catch (DuplicateKeyException alreadySeeded) {
            return false;
        } catch (DataAccessException failure) {
            throw TransactionRunner.translate(failure);
        }
    }

    @Override
    public TaxRateProposal review(final String id, final TaxRateReviewAction action) {
        return transactions.inTransaction(status -> {
            final TaxRateDocument document = mongo.findById(id, TaxRateDocument.class);
            if (document == null) {
                throw new TaxRateNotFoundException(id);
            }
            final TaxRateProposal target = mapper.toDomain(document);
            final MarketCode market = target.period().market();
            final TaxCategory category = target.period().category();
            lock(market, category);
            final List<TaxRateProposal> approved = mongo.find(approvedOf(market, category)
                            .addCriteria(where(Fields.ID).ne(id)), TaxRateDocument.class)
                    .stream().map(mapper::toDomain).toList();
            final List<TaxRateProposal> changes = action.apply(target, approved);
            changes.forEach(this::replace);
            return bumped(changes.getFirst());
        });
    }

    @Override
    public List<TaxRatePeriod> approvedPeriods() {
        return read(() -> mongo.find(query(where(Fields.STATUS)
                        .is(TaxRateStatus.APPROVED.name())), TaxRateDocument.class).stream()
                .map(mapper::period).toList());
    }

    private void lock(final MarketCode market, final TaxCategory category) {
        mongo.upsert(query(where(Fields.ID).is(market.value() + KEY_SEPARATOR + category.name())),
                new Update().inc(TaxRateDocument.VERSION, 1), TaxRateDocument.GUARDS);
    }

    private void replace(final TaxRateProposal change) {
        final Query current = query(where(Fields.ID).is(change.id())
                .and(TaxRateDocument.VERSION).is(change.version()));
        if (mongo.findAndReplace(current, mapper.toDocument(bumped(change))) == null) {
            throw new TaxRateConflictException(CONCURRENT_CHANGE.formatted(change.id()));
        }
    }

    private static TaxRateProposal bumped(final TaxRateProposal change) {
        return new TaxRateProposal(change.id(), change.period(), change.status(),
                change.proposedBy(), change.proposedAt(), change.changeReason(), change.review(),
                change.version() + 1);
    }

    private static Query approvedOf(final MarketCode market, final TaxCategory category) {
        return query(where(Fields.MARKET).is(market.value())
                .and(TaxRateDocument.CATEGORY).is(category.name())
                .and(Fields.STATUS).is(TaxRateStatus.APPROVED.name()));
    }

    private static <T> T read(final Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException failure) {
            throw TransactionRunner.translate(failure);
        }
    }
}
