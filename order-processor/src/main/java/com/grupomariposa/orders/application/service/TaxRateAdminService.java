package com.grupomariposa.orders.application.service;

import com.grupomariposa.orders.application.command.TaxRateProposalCommand;
import com.grupomariposa.orders.application.error.InvalidTaxRateException;
import com.grupomariposa.orders.application.port.in.TaxRateAdministration;
import com.grupomariposa.orders.application.port.out.IdGenerator;
import com.grupomariposa.orders.application.port.out.TaxRateRepository;
import com.grupomariposa.orders.application.port.out.TaxRateSource;
import com.grupomariposa.orders.application.port.out.TimeProvider;
import com.grupomariposa.orders.application.query.TaxRateFilter;
import com.grupomariposa.orders.application.validation.ValidationError;
import com.grupomariposa.orders.domain.model.MarketCatalog;
import com.grupomariposa.orders.domain.model.Rate;
import com.grupomariposa.orders.domain.model.TaxRatePeriod;
import com.grupomariposa.orders.domain.model.TaxRateProposal;
import com.grupomariposa.orders.domain.service.TaxRateApprovals;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TaxRateAdminService implements TaxRateAdministration {

    static final int MAX_RATE_SCALE = 4;
    private static final String MARKET = "market";
    private static final String CATEGORY = "category";
    private static final String RATE = "rate";
    private static final String VALID_FROM = "validFrom";
    private static final String VALID_TO = "validTo";
    private static final String CHANGE_REASON = "changeReason";
    private static final String PROPOSER = "proposedBy";
    private static final String REQUIRED = "is required";
    private static final String UNKNOWN_MARKET = "must be one of %s";
    private static final String NOT_A_FRACTION = "must be between 0 and 1";
    private static final String TOO_PRECISE = "must have at most 4 decimals";
    private static final String NOT_FUTURE = "must be in the future";
    private static final String NOT_AFTER_FROM = "must be after validFrom";

    private final TaxRateRepository repository;
    private final TaxRateSource source;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;
    private final MarketCatalog markets;
    private final boolean allowPastValidFrom;
    private final TaxRateApprovals approvals = new TaxRateApprovals();

    public TaxRateAdminService(final TaxRateRepository repository, final TaxRateSource source,
                               final IdGenerator idGenerator, final TimeProvider timeProvider,
                               final MarketCatalog markets, final boolean allowPastValidFrom) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.source = Objects.requireNonNull(source, "source");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.timeProvider = Objects.requireNonNull(timeProvider, "timeProvider");
        this.markets = Objects.requireNonNull(markets, "markets");
        this.allowPastValidFrom = allowPastValidFrom;
    }

    @Override
    public List<TaxRateProposal> list(final TaxRateFilter filter) {
        return repository.find(filter);
    }

    @Override
    public TaxRateProposal propose(final TaxRateProposalCommand command) {
        final Instant now = timeProvider.now();
        final List<ValidationError> errors = validate(command, now);
        if (!errors.isEmpty()) {
            throw new InvalidTaxRateException(errors);
        }
        final TaxRatePeriod period = new TaxRatePeriod(command.market(), command.category(),
                new Rate(command.rate()), command.validFrom(), command.validTo());
        return repository.insert(TaxRateProposal.proposed(idGenerator.newEventId(), period,
                command.proposer(), now, command.changeReason().trim()));
    }

    @Override
    public TaxRateProposal approve(final String id, final String approver) {
        final Instant now = timeProvider.now();
        final TaxRateProposal approved = repository.review(id, (target, approvedSameKey) ->
                approvals.approve(target, approvedSameKey, approver, now));
        source.refresh();
        return approved;
    }

    @Override
    public TaxRateProposal reject(final String id, final String reviewer) {
        final Instant now = timeProvider.now();
        return repository.review(id, (target, approvedSameKey) ->
                List.of(target.rejectedBy(reviewer, now)));
    }

    private List<ValidationError> validate(final TaxRateProposalCommand command,
                                           final Instant now) {
        final List<ValidationError> errors = new ArrayList<>();
        if (command.market() == null) {
            errors.add(new ValidationError(MARKET, REQUIRED));
        } else if (!markets.supports(command.market())) {
            errors.add(new ValidationError(MARKET,
                    UNKNOWN_MARKET.formatted(markets.supportedMarkets())));
        }
        if (command.category() == null) {
            errors.add(new ValidationError(CATEGORY, REQUIRED));
        }
        validateRate(command.rate(), errors);
        validatePeriod(command.validFrom(), command.validTo(), now, errors);
        if (isBlank(command.changeReason())) {
            errors.add(new ValidationError(CHANGE_REASON, REQUIRED));
        }
        if (isBlank(command.proposer())) {
            errors.add(new ValidationError(PROPOSER, REQUIRED));
        }
        return errors;
    }

    private static void validateRate(final BigDecimal rate, final List<ValidationError> errors) {
        if (rate == null) {
            errors.add(new ValidationError(RATE, REQUIRED));
        } else if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            errors.add(new ValidationError(RATE, NOT_A_FRACTION));
        } else if (rate.stripTrailingZeros().scale() > MAX_RATE_SCALE) {
            errors.add(new ValidationError(RATE, TOO_PRECISE));
        }
    }

    private void validatePeriod(final Instant validFrom, final Instant validTo,
                                final Instant now, final List<ValidationError> errors) {
        if (validFrom == null) {
            errors.add(new ValidationError(VALID_FROM, REQUIRED));
            return;
        }
        if (!allowPastValidFrom && !validFrom.isAfter(now)) {
            errors.add(new ValidationError(VALID_FROM, NOT_FUTURE));
        }
        if (validTo != null && !validTo.isAfter(validFrom)) {
            errors.add(new ValidationError(VALID_TO, NOT_AFTER_FROM));
        }
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
