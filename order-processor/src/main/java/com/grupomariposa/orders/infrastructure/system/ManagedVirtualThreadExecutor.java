package com.grupomariposa.orders.infrastructure.system;

import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextSnapshotFactory;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ManagedVirtualThreadExecutor implements AutoCloseable {

    private final ExecutorService delegate;

    public ManagedVirtualThreadExecutor() {
        final ContextSnapshotFactory snapshots = ContextSnapshotFactory.builder().build();
        this.delegate = ContextExecutorService.wrap(Executors.newVirtualThreadPerTaskExecutor(),
                snapshots::captureAll);
    }

    public Executor executor() {
        return delegate;
    }

    @Override
    public void close() {
        delegate.shutdownNow();
    }
}
