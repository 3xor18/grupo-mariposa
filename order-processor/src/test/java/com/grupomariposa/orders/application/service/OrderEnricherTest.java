package com.grupomariposa.orders.application.service;

import static com.grupomariposa.orders.application.ApplicationFixtures.CLIENT_ID;
import static com.grupomariposa.orders.application.ApplicationFixtures.goldenCommand;
import static com.grupomariposa.orders.domain.DomainFixtures.product;
import static com.grupomariposa.orders.domain.DomainFixtures.wholesaleClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.error.ErrorCategory;
import com.grupomariposa.orders.application.error.ExternalTransientException;
import com.grupomariposa.orders.application.error.UnexpectedProcessingException;
import com.grupomariposa.orders.application.port.out.ClientDirectory;
import com.grupomariposa.orders.application.port.out.ProductCatalog;
import com.grupomariposa.orders.domain.DomainFixtures;
import com.grupomariposa.orders.domain.Markets;
import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.EvaluationInput;
import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.domain.model.RequestedItem;
import com.grupomariposa.orders.domain.model.ResolvedItem;
import com.grupomariposa.orders.domain.model.TaxCategory;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OrderEnricherTest {

    private final ClientDirectory clients = mock(ClientDirectory.class);
    private final ProductCatalog products = mock(ProductCatalog.class);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean clientStarted = new AtomicBoolean();

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void should_resolve_client_and_every_product_in_item_order() {
        final Lookup<ClientProfile> client = Lookup.found(wholesaleClient(Markets.MX));
        when(clients.findClient(CLIENT_ID)).thenReturn(client);
        when(products.findProduct("PRD-001", Markets.MX))
                .thenReturn(Lookup.found(product("PRD-001", TaxCategory.STANDARD)));
        when(products.findProduct("PRD-008", Markets.MX)).thenReturn(Lookup.notFound());

        final EvaluationInput input = enricher(4).enrich(goldenCommand());

        assertThat(input.client()).isEqualTo(client);
        assertThat(input.market()).isEqualTo(Markets.MX);
        assertThat(input.items()).extracting(ResolvedItem::product).containsExactly(
                Lookup.found(product("PRD-001", TaxCategory.STANDARD)), Lookup.notFound());
    }

    @Test
    void should_propagate_typed_lookup_failures() {
        when(clients.findClient(CLIENT_ID)).thenReturn(Lookup.notFound());
        when(products.findProduct(anyString(), eq(Markets.MX)))
                .thenThrow(new ExternalTransientException("products-api", "503", null));

        assertThatThrownBy(() -> enricher(4).enrich(goldenCommand()))
                .isInstanceOf(ExternalTransientException.class);
    }

    @Test
    void should_wrap_checked_lookup_failures_as_unexpected() {
        final IOException checked = new IOException("boom");
        when(clients.findClient(CLIENT_ID)).thenAnswer(invocation -> {
            throw checked;
        });
        when(products.findProduct(anyString(), eq(Markets.MX))).thenReturn(Lookup.notFound());

        assertThatThrownBy(() -> enricher(4).enrich(goldenCommand()))
                .isInstanceOfSatisfying(UnexpectedProcessingException.class, unexpected -> {
                    assertThat(unexpected).hasCause(checked);
                    assertThat(unexpected.category()).isEqualTo(ErrorCategory.UNEXPECTED);
                });
    }

    @Test
    void should_fail_as_unexpected_when_interrupted_waiting_for_permit() {
        final OrderEnricher direct = new OrderEnricher(clients, products, Runnable::run, 1,
                DomainFixtures.CURRENCIES);
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> direct.enrich(goldenCommand()))
                .isInstanceOf(UnexpectedProcessingException.class);
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void should_bound_concurrent_lookups() throws Exception {
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();
        final CountDownLatch saturated = new CountDownLatch(2);
        final CountDownLatch release = new CountDownLatch(1);
        when(clients.findClient(CLIENT_ID)).thenReturn(Lookup.notFound());
        when(products.findProduct(anyString(), eq(Markets.MX))).thenAnswer(invocation -> {
            peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            saturated.countDown();
            release.await(5, TimeUnit.SECONDS);
            inFlight.decrementAndGet();
            return Lookup.<ProductProfile>notFound();
        });

        final CompletableFuture<EvaluationInput> running = CompletableFuture.supplyAsync(
                () -> enricher(2).enrich(commandWithItems(12)), executor);
        assertThat(saturated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(inFlight.get()).isEqualTo(2);
        release.countDown();

        assertThat(running.get(5, TimeUnit.SECONDS).items()).hasSize(12);
        assertThat(peak.get()).isEqualTo(2);
    }

    @Test
    void should_cancel_pending_lookups_when_one_fails() {
        final Queue<Runnable> deferred = new ArrayDeque<>();
        final Executor clientFirst = task -> {
            if (deferred.isEmpty() && !clientStarted.getAndSet(true)) {
                task.run();
            } else {
                deferred.add(task);
            }
        };
        when(clients.findClient(CLIENT_ID))
                .thenThrow(new ExternalTransientException("clients-api", "503", null));

        assertThatThrownBy(() -> new OrderEnricher(clients, products, clientFirst, 4,
                DomainFixtures.CURRENCIES)
                .enrich(goldenCommand())).isInstanceOf(ExternalTransientException.class);
        deferred.forEach(Runnable::run);

        verifyNoInteractions(products);
    }

    private OrderEnricher enricher(final int permits) {
        return new OrderEnricher(clients, products, executor, permits, DomainFixtures.CURRENCIES);
    }

    private static OrderCommand commandWithItems(final int count) {
        final OrderCommand golden = goldenCommand();
        return new OrderCommand(golden.eventId(),
                1, golden.orderId(), Markets.MX, golden.currency(), CLIENT_ID, null, null,
                IntStream.range(0, count)
                        .mapToObj(index -> new RequestedItem("PRD-" + index, 1, BigDecimal.ONE))
                        .toList(), golden.reception());
    }
}
