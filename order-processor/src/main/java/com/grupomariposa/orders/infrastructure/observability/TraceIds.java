package com.grupomariposa.orders.infrastructure.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.util.Objects;
import java.util.Optional;

public final class TraceIds {

    private final Tracer tracer;

    public TraceIds(final Tracer tracer) {
        this.tracer = Objects.requireNonNull(tracer, "tracer");
    }

    public Optional<String> currentTraceId() {
        return Optional.ofNullable(tracer.currentSpan())
                .map(Span::context)
                .map(TraceContext::traceId)
                .filter(traceId -> !traceId.isBlank());
    }
}
