package com.grupomariposa.orders.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.grupomariposa.orders.application.ApplicationFixtures;
import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.outcome.ProcessingOutcome;
import com.grupomariposa.orders.application.port.in.ProcessOrderUseCase;
import com.grupomariposa.orders.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

class ConcurrencyIT extends IntegrationTest {

    private static final int WORKERS = 8;
    private static final long BARRIER_TIMEOUT_SECONDS = 10;
    private static final long RACE_TIMEOUT_SECONDS = 60;

    @Autowired
    private ProcessOrderUseCase useCase;

    @Autowired
    private MongoTemplate mongo;

    @Test
    void should_persist_exactly_one_effect_when_same_event_races() throws Exception {
        stubDependencies();
        final String orderId = "ORD-RACE-" + UUID.randomUUID();
        final String eventId = "EVT-" + UUID.randomUUID();

        final List<ProcessingOutcome> outcomes = race(worker -> command(orderId, eventId));

        assertThat(outcomes).filteredOn(ProcessingOutcome.Processed.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(ProcessingOutcome.Duplicate.class::isInstance)
                .hasSize(WORKERS - 1);
        assertSingleEffect(orderId);
    }

    @Test
    void should_let_first_event_win_when_versions_collide() throws Exception {
        stubDependencies();
        final String orderId = "ORD-COLLIDE-" + UUID.randomUUID();

        final List<ProcessingOutcome> outcomes =
                race(worker -> command(orderId, "EVT-" + worker + "-" + UUID.randomUUID()));

        assertThat(outcomes).filteredOn(ProcessingOutcome.Processed.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(ProcessingOutcome.VersionConflict.class::isInstance)
                .hasSize(WORKERS - 1);
        assertSingleEffect(orderId);
    }

    private List<ProcessingOutcome> race(final IntFunction<OrderCommand> commands)
            throws Exception {
        final CyclicBarrier start = new CyclicBarrier(WORKERS);
        try (ExecutorService pool = Executors.newFixedThreadPool(WORKERS)) {
            final List<Future<ProcessingOutcome>> futures = new ArrayList<>();
            for (int worker = 0; worker < WORKERS; worker++) {
                final OrderCommand command = commands.apply(worker);
                final Callable<ProcessingOutcome> task = () -> {
                    start.await(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    return useCase.process(command);
                };
                futures.add(pool.submit(task));
            }
            final List<ProcessingOutcome> outcomes = new ArrayList<>();
            for (final Future<ProcessingOutcome> future : futures) {
                outcomes.add(future.get(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return outcomes;
        }
    }

    private void assertSingleEffect(final String orderId) {
        final Query byOrder = Query.query(Criteria.where("orderId").is(orderId));
        assertThat(mongo.count(Query.query(Criteria.where("_id").is(orderId)), "orders")).isOne();
        assertThat(mongo.count(byOrder, "outbox")).isOne();
        assertThat(mongo.count(byOrder, "inbox")).isOne();
    }

    private void stubDependencies() {
        stubs.golden();
    }

    private static OrderCommand command(final String orderId, final String eventId) {
        return ApplicationFixtures.command(orderId, eventId, 1);
    }
}
