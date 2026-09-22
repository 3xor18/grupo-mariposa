package com.grupomariposa.orders.application.service;

import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.error.UnexpectedProcessingException;
import com.grupomariposa.orders.application.port.out.ClientDirectory;
import com.grupomariposa.orders.application.port.out.ProductCatalog;
import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.EvaluationInput;
import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.domain.model.ResolvedItem;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

public final class OrderEnricher {

    private static final String INTERRUPTED = "Interrupted while waiting for a lookup permit";
    private static final String LOOKUP_FAILED = "Lookup failed with a checked exception";

    private final ClientDirectory clientDirectory;
    private final ProductCatalog productCatalog;
    private final Executor executor;
    private final Semaphore permits;

    public OrderEnricher(final ClientDirectory clientDirectory,
                         final ProductCatalog productCatalog,
                         final Executor executor, final int maxConcurrentLookups) {
        this.clientDirectory = Objects.requireNonNull(clientDirectory, "clientDirectory");
        this.productCatalog = Objects.requireNonNull(productCatalog, "productCatalog");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.permits = new Semaphore(maxConcurrentLookups, true);
    }

    public EvaluationInput enrich(final OrderCommand command) {
        final CompletableFuture<Lookup<ClientProfile>> client =
                lookup(() -> clientDirectory.findClient(command.clientId()));
        final List<CompletableFuture<ResolvedItem>> items = command.items().stream()
                .map(item -> lookup(() -> new ResolvedItem(item,
                        productCatalog.findProduct(item.productId(), command.market()))))
                .toList();
        try {
            final Lookup<ClientProfile> resolvedClient = await(client);
            final List<ResolvedItem> resolvedItems =
                    items.stream().map(OrderEnricher::await).toList();
            return new EvaluationInput(command.market(), resolvedClient, resolvedItems);
        } catch (RuntimeException failure) {
            client.cancel(true);
            items.forEach(item -> item.cancel(true));
            throw failure;
        }
    }

    private <T> CompletableFuture<T> lookup(final Supplier<T> call) {
        return CompletableFuture.supplyAsync(() -> withPermit(call), executor);
    }

    private <T> T withPermit(final Supplier<T> call) {
        acquirePermit();
        try {
            return call.get();
        } finally {
            permits.release();
        }
    }

    private void acquirePermit() {
        try {
            permits.acquire();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new UnexpectedProcessingException(INTERRUPTED, interrupted);
        }
    }

    private static <T> T await(final CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException failure) {
            throw unwrap(failure);
        }
    }

    static RuntimeException unwrap(final CompletionException failure) {
        if (failure.getCause() instanceof RuntimeException runtime) {
            return runtime;
        }
        return new UnexpectedProcessingException(LOOKUP_FAILED, failure.getCause());
    }
}
