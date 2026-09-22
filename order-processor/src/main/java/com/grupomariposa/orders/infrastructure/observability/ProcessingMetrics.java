package com.grupomariposa.orders.infrastructure.observability;

import com.grupomariposa.orders.application.error.ErrorCategory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;

public final class ProcessingMetrics {

    public static final String LATENCY = "orders.processing.latency";
    public static final String DEAD_LETTERED = "orders.dlt";
    public static final String RETRIES = "orders.retries";
    public static final String CATEGORY = "category";
    public static final String DEPENDENCY = "dependency";
    public static final String STAGES = "orders.stages";
    public static final String STAGE = "stage";

    private final MeterRegistry registry;
    private final Timer latency;

    public ProcessingMetrics(final MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.latency = Timer.builder(LATENCY)
                .description("Time to process one orders.created.v1 record")
                .publishPercentileHistogram()
                .register(registry);
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }

    public void stop(final Timer.Sample sample) {
        sample.stop(latency);
    }

    public void deadLettered(final ErrorCategory category) {
        Counter.builder(DEAD_LETTERED).tag(CATEGORY, category.name()).register(registry)
                .increment();
    }

    public void retried(final String dependency) {
        Counter.builder(RETRIES).tag(DEPENDENCY, dependency).register(registry).increment();
    }
}
