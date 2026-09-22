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
import static org.mockito.Mockito.when;

import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.error.ErrorCategory;
import com.grupomariposa.orders.application.error.ExternalTransientException;
import com.grupomariposa.orders.application.error.ProcessingException;
import com.grupomariposa.orders.application.error.UnexpectedProcessingException;
import com.grupomariposa.orders.application.port.out.ClientDirectory;
import com.grupomariposa.orders.application.port.out.ProductCatalog;
import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.EvaluationInput;
import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.domain.model.RequestedItem;
import com.grupomariposa.orders.domain.model.ResolvedItem;
import com.grupomariposa.orders.domain.model.TaxCategory;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OrderEnricherTest {

    private final ClientDirectory clients = mock(ClientDirectory.class);
    private final ProductCatalog products = mock(ProductCatalog.class);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void should_resolve_client_and_every_product_in_item_order() {
        final Lookup<ClientProfile> client = Lookup.found(wholesaleClient(Market.MX));
        when(clients.findClient(CLIENT_ID)).thenReturn(client);
        when(products.findProduct("PRD-001", Market.MX))
                .thenReturn(Lookup.found(product("PRD-001", TaxCategory.STANDARD)));
        when(products.findProduct("PRD-008", Market.MX)).thenReturn(Lookup.notFound());

        final EvaluationInput input = enricher(4).enrich(goldenCommand());

        assertThat(input.client()).isEqualTo(client);
        assertThat(input.market()).isEqualTo(Market.MX);
        assertThat(input.items()).extracting(ResolvedItem::product).containsExactly(
                Lookup.found(product("PRD-001", TaxCategory.STANDARD)), Lookup.notFound());
    }

    @Test
    void should_propagate_typed_lookup_failures() {
        when(clients.findClient(CLIENT_ID)).thenReturn(Lookup.notFound());
        when(products.findProduct(anyString(), eq(Market.MX)))
                .thenThrow(new ExternalTransientException("products-api", "503", null));

        assertThatThrownBy(() -> enricher(4).enrich(goldenCommand()))
                .isInstanceOf(ExternalTransientException.class);
    }

    @Test
    void should_wrap_checked_lookup_failures_as_unexpected() {
        final IOException checked = new IOException("boom");

        final RuntimeException unwrapped =
                OrderEnricher.unwrap(new CompletionException(checked));

        assertThat(unwrapped).isInstanceOf(UnexpectedProcessingException.class)
                .hasCause(checked);
        assertThat(((ProcessingException) unwrapped).category())
                .isEqualTo(ErrorCategory.UNEXPECTED);
    }

    @Test
    void should_fail_as_unexpected_when_interrupted_waiting_for_permit() {
        final OrderEnricher direct = new OrderEnricher(clients, products, Runnable::run, 1);
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> direct.enrich(goldenCommand()))
                .isInstanceOf(UnexpectedProcessingException.class);
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void should_bound_concurrent_lookups() {
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();
        when(clients.findClient(CLIENT_ID)).thenReturn(Lookup.notFound());
        when(products.findProduct(anyString(), eq(Market.MX))).thenAnswer(invocation -> {
            peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            Thread.sleep(20);
            inFlight.decrementAndGet();
            return Lookup.<ProductProfile>notFound();
        });

        enricher(2).enrich(commandWithItems(12));

        assertThat(peak.get()).isBetween(1, 2);
    }

    private OrderEnricher enricher(final int permits) {
        return new OrderEnricher(clients, products, executor, permits);
    }

    private static OrderCommand commandWithItems(final int count) {
        final OrderCommand golden = goldenCommand();
        return new OrderCommand(golden.eventId(),
                1, golden.orderId(), Market.MX, golden.currency(), CLIENT_ID, null, null,
                IntStream.range(0, count)
                        .mapToObj(index -> new RequestedItem("PRD-" + index, 1, BigDecimal.ONE))
                        .toList(), golden.reception());
    }
}
