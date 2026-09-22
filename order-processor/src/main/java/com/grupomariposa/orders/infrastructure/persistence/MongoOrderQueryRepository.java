package com.grupomariposa.orders.infrastructure.persistence;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.grupomariposa.orders.application.port.out.OrderQueryRepository;
import com.grupomariposa.orders.application.query.OrderSearchCriteria;
import com.grupomariposa.orders.application.query.OrderSummary;
import com.grupomariposa.orders.application.query.PageResult;
import com.grupomariposa.orders.domain.model.Order;
import com.grupomariposa.orders.infrastructure.persistence.document.Fields;
import com.grupomariposa.orders.infrastructure.persistence.document.OrderDocument;
import com.grupomariposa.orders.infrastructure.persistence.mapping.OrderDocumentMapper;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public final class MongoOrderQueryRepository implements OrderQueryRepository {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, Fields.PROCESSED_AT);

    private final MongoTemplate mongo;
    private final OrderDocumentMapper mapper;

    public MongoOrderQueryRepository(final MongoTemplate mongo, final OrderDocumentMapper mapper) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public Optional<Order> findById(final String orderId) {
        return read(() -> Optional.ofNullable(mongo.findOne(query(where(Fields.ID).is(orderId)),
                OrderDocument.class)).map(mapper::toDomain));
    }

    @Override
    public PageResult<OrderSummary> search(final OrderSearchCriteria criteria) {
        final Criteria filter = filterOf(criteria);
        return read(() -> {
            final long total = mongo.count(new Query(filter), OrderDocument.class);
            final Query page = new Query(filter)
                    .with(PageRequest.of(criteria.page(), criteria.size(), NEWEST_FIRST));
            final List<OrderSummary> items = mongo.find(page, OrderDocument.class).stream()
                    .map(mapper::toSummary)
                    .toList();
            return new PageResult<>(items, criteria.page(), criteria.size(), total);
        });
    }

    private static <T> T read(final Supplier<T> query) {
        try {
            return query.get();
        } catch (DataAccessException failure) {
            throw TransactionRunner.translate(failure, 1);
        }
    }

    private static Criteria filterOf(final OrderSearchCriteria criteria) {
        final Criteria filter = new Criteria();
        criteria.statusFilter().ifPresent(status -> filter.and(Fields.STATUS).is(status.name()));
        criteria.marketFilter().ifPresent(market -> filter.and(Fields.MARKET).is(market.name()));
        return filter;
    }
}
