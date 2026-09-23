package com.grupomariposa.orders.infrastructure.web;

import com.grupomariposa.orders.application.command.TaxRateProposalCommand;
import com.grupomariposa.orders.application.query.TaxRateFilter;
import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRatePeriod;
import com.grupomariposa.orders.domain.model.TaxRateProposal;
import com.grupomariposa.orders.domain.model.TaxRateStatus;
import com.grupomariposa.orders.infrastructure.web.dto.TaxRateRequest;
import com.grupomariposa.orders.infrastructure.web.dto.TaxRateResponse;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class TaxRateWebMapper {

    static final String PREFERRED_USERNAME = "preferred_username";
    static final String ANONYMOUS = "anonymous";
    private static final String MARKET = "market";
    private static final String CATEGORY = "category";
    private static final String STATUS = "status";
    private static final String VALID_FROM = "validFrom";
    private static final String VALID_TO = "validTo";
    private static final String NOT_A_MARKET = "must be a two-letter market code";
    private static final String NOT_ONE_OF = "must be one of %s";
    private static final String NOT_AN_INSTANT = "must be an ISO-8601 instant";

    public TaxRateFilter filter(final String market, final String category,
                                final String status) {
        final List<FieldViolation> violations = new ArrayList<>();
        final TaxRateFilter filter = new TaxRateFilter(
                market(market, violations).orElse(null),
                enumOf(category, TaxCategory.class, CATEGORY, violations).orElse(null),
                enumOf(status, TaxRateStatus.class, STATUS, violations).orElse(null));
        failOn(violations);
        return filter;
    }

    public TaxRateProposalCommand command(final TaxRateRequest request,
                                          final Authentication proposer) {
        final List<FieldViolation> violations = new ArrayList<>();
        final TaxRateProposalCommand command = new TaxRateProposalCommand(
                market(request.market(), violations).orElse(null),
                enumOf(request.category(), TaxCategory.class, CATEGORY, violations).orElse(null),
                request.rate(),
                instant(request.validFrom(), VALID_FROM, violations).orElse(null),
                instant(request.validTo(), VALID_TO, violations).orElse(null),
                request.changeReason(), actor(proposer));
        failOn(violations);
        return command;
    }

    public TaxRateResponse toResponse(final TaxRateProposal proposal) {
        final TaxRatePeriod period = proposal.period();
        return new TaxRateResponse(proposal.id(), period.market().value(),
                period.category().name(), period.rate().value(), period.validFrom(),
                period.validTo(), proposal.status().name(), proposal.proposedBy(),
                proposal.proposedAt(),
                proposal.review() == null ? null : proposal.review().reviewer(),
                proposal.review() == null ? null : proposal.review().reviewedAt(),
                proposal.changeReason(), proposal.version());
    }

    public String actor(final Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return Optional.ofNullable(jwt.getToken().getClaimAsString(PREFERRED_USERNAME))
                    .orElseGet(() -> jwt.getToken().getSubject());
        }
        return authentication == null ? ANONYMOUS : authentication.getName();
    }

    private static Optional<MarketCode> market(final String value,
                                               final List<FieldViolation> violations) {
        return parse(value, MarketCode::parse, MARKET, NOT_A_MARKET, violations);
    }

    private static Optional<Instant> instant(final String value, final String field,
                                             final List<FieldViolation> violations) {
        return parse(value, text -> {
            try {
                return Optional.of(Instant.parse(text));
            } catch (DateTimeParseException invalid) {
                return Optional.empty();
            }
        }, field, NOT_AN_INSTANT, violations);
    }

    private static <E extends Enum<E>> Optional<E> enumOf(final String value,
                                                          final Class<E> type,
                                                          final String field,
                                                          final List<FieldViolation> violations) {
        final E[] constants = type.getEnumConstants();
        return parse(value, text -> Arrays.stream(constants)
                        .filter(constant -> constant.name().equals(text)).findFirst(),
                field, NOT_ONE_OF.formatted(Arrays.toString(constants)), violations);
    }

    private static <T> Optional<T> parse(final String value,
                                         final Function<String, Optional<T>> parser,
                                         final String field, final String message,
                                         final List<FieldViolation> violations) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        final Optional<T> parsed = parser.apply(value.trim());
        if (parsed.isEmpty()) {
            violations.add(new FieldViolation(field, message));
        }
        return parsed;
    }

    private static void failOn(final List<FieldViolation> violations) {
        if (!violations.isEmpty()) {
            throw new InvalidRequestException(violations);
        }
    }
}
