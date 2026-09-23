package com.grupomariposa.orders.infrastructure.web;

import com.grupomariposa.orders.application.error.InvalidTaxRateException;
import com.grupomariposa.orders.application.error.PersistenceException;
import com.grupomariposa.orders.application.error.TaxRateConflictException;
import com.grupomariposa.orders.application.error.TaxRateNotFoundException;
import com.grupomariposa.orders.domain.model.TaxRateRule;
import com.grupomariposa.orders.domain.model.TaxRateRuleViolation;
import com.grupomariposa.orders.infrastructure.observability.CauseSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String NOT_FOUND = "Resource does not exist";
    private static final String NOT_ALLOWED = "HTTP method is not supported for this resource";
    private static final String INTERNAL = "Unexpected error while processing the request";
    private static final String UNAVAILABLE = "Orders are temporarily unavailable";
    private static final String UNREADABLE = "Request body is missing or is not valid JSON";

    private final ProblemFactory problems;
    private final CauseSanitizer sanitizer;

    public ApiExceptionHandler(final ProblemFactory problems, final CauseSanitizer sanitizer) {
        this.problems = Objects.requireNonNull(problems, "problems");
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ProblemDetail> orderNotFound(final OrderNotFoundException failure,
                                                      final HttpServletRequest request) {
        return respond(problems.create(HttpStatus.NOT_FOUND, ApiErrorCode.ORDER_NOT_FOUND,
                failure.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ProblemDetail> invalid(final InvalidRequestException failure,
                                                 final HttpServletRequest request) {
        return respond(problems.invalid(failure.violations(), failure.getMessage(),
                request.getRequestURI()));
    }

    @ExceptionHandler(TaxRateNotFoundException.class)
    public ResponseEntity<ProblemDetail> taxRateNotFound(final TaxRateNotFoundException failure,
                                                         final HttpServletRequest request) {
        return respond(problems.create(HttpStatus.NOT_FOUND, ApiErrorCode.TAX_RATE_NOT_FOUND,
                failure.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidTaxRateException.class)
    public ResponseEntity<ProblemDetail> invalidTaxRate(final InvalidTaxRateException failure,
                                                        final HttpServletRequest request) {
        return respond(problems.invalid(failure.errors().stream()
                        .map(error -> new FieldViolation(error.field(), error.message()))
                        .toList(), failure.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(TaxRateRuleViolation.class)
    public ResponseEntity<ProblemDetail> taxRateRule(final TaxRateRuleViolation failure,
                                                     final HttpServletRequest request) {
        if (failure.rule() == TaxRateRule.FOUR_EYES_REQUIRED) {
            return respond(problems.create(HttpStatus.FORBIDDEN,
                    ApiErrorCode.FOUR_EYES_REQUIRED, failure.getMessage(),
                    request.getRequestURI()));
        }
        return respond(problems.create(HttpStatus.CONFLICT, ApiErrorCode.TAX_RATE_CONFLICT,
                failure.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(TaxRateConflictException.class)
    public ResponseEntity<ProblemDetail> taxRateConflict(final TaxRateConflictException failure,
                                                         final HttpServletRequest request) {
        return respond(problems.create(HttpStatus.CONFLICT, ApiErrorCode.TAX_RATE_CONFLICT,
                failure.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> unreadable(final HttpServletRequest request) {
        return respond(problems.invalid(List.of(), UNREADABLE, request.getRequestURI()));
    }

    @ExceptionHandler(PersistenceException.class)
    public ResponseEntity<ProblemDetail> unavailable(final PersistenceException failure,
                                                     final HttpServletRequest request) {
        LOG.warn("Order store unavailable serving {}", request.getRequestURI());
        return respond(problems.create(HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.SERVICE_UNAVAILABLE, UNAVAILABLE, request.getRequestURI()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> noResource(final HttpServletRequest request) {
        return respond(problems.create(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, NOT_FOUND,
                request.getRequestURI()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> notAllowed(final HttpServletRequest request) {
        return respond(problems.create(HttpStatus.METHOD_NOT_ALLOWED,
                ApiErrorCode.METHOD_NOT_ALLOWED, NOT_ALLOWED, request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(final Exception failure,
                                                    final HttpServletRequest request) {
        LOG.error("Unhandled error serving {}: {}", request.getRequestURI(),
                sanitizer.describe(failure));
        return respond(problems.create(HttpStatus.INTERNAL_SERVER_ERROR,
                ApiErrorCode.INTERNAL_ERROR, INTERNAL, request.getRequestURI()));
    }

    private static ResponseEntity<ProblemDetail> respond(final ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
