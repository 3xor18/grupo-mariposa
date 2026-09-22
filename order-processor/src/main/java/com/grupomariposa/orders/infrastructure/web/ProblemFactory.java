package com.grupomariposa.orders.infrastructure.web;

import com.grupomariposa.orders.infrastructure.observability.TraceContext;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class ProblemFactory {

    public static final String CODE = "code";
    public static final String TRACE_ID = "traceId";
    public static final String TIMESTAMP = "timestamp";
    public static final String ERRORS = "errors";
    private static final String HYPHEN = "-";
    private static final String EMPTY = "";
    private static final String ROOT = "/";

    private final Clock clock;
    private final String typeBase;
    private final TraceContext traceContext;

    public ProblemFactory(final Clock clock, final TraceContext traceContext,
                          final String typeBase) {
        this.typeBase = Objects.requireNonNull(typeBase, "typeBase");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.traceContext = Objects.requireNonNull(traceContext, "traceContext");
    }

    public ProblemDetail create(final HttpStatus status, final ApiErrorCode code,
                                final String detail, final String path) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(typeBase + code.slug()));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(instanceOf(path));
        problem.setProperty(CODE, code.name());
        problem.setProperty(TRACE_ID, traceContext.currentTraceId().orElseGet(
                ProblemFactory::generatedTraceId));
        problem.setProperty(TIMESTAMP, clock.instant().toString());
        return problem;
    }

    public ProblemDetail invalid(final List<FieldViolation> violations, final String detail,
                                 final String path) {
        final ProblemDetail problem = create(HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_ERROR, detail, path);
        problem.setProperty(ERRORS, violations);
        return problem;
    }

    private static URI instanceOf(final String path) {
        try {
            return URI.create(path == null ? ROOT : path);
        } catch (IllegalArgumentException invalid) {
            return URI.create(ROOT);
        }
    }

    private static String generatedTraceId() {
        return UUID.randomUUID().toString().replace(HYPHEN, EMPTY);
    }
}
