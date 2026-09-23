package com.grupomariposa.orders.application.service;

import com.grupomariposa.orders.application.port.out.IdGenerator;
import com.grupomariposa.orders.application.port.out.TaxRateRepository;
import com.grupomariposa.orders.application.port.out.TimeProvider;
import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRatePeriod;
import com.grupomariposa.orders.domain.model.TaxRateProposal;
import com.grupomariposa.orders.domain.model.TaxRateSchedule;
import com.grupomariposa.orders.domain.model.TaxRateTable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class TaxRateSeedService {

    public static final String SEED_ACTOR = "system-seed";
    static final String SEED_REASON = "Initial rate from the platform configuration";

    private final TaxRateRepository repository;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    public TaxRateSeedService(final TaxRateRepository repository, final IdGenerator idGenerator,
                              final TimeProvider timeProvider) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.timeProvider = Objects.requireNonNull(timeProvider, "timeProvider");
    }

    public static TaxRateSchedule scheduleOf(final TaxRateTable configured,
                                             final Collection<MarketCode> markets,
                                             final Instant from) {
        final List<TaxRatePeriod> periods = new ArrayList<>();
        for (final MarketCode market : markets) {
            for (final TaxCategory category : TaxCategory.values()) {
                periods.add(new TaxRatePeriod(market, category,
                        configured.rateFor(market, category), from, null));
            }
        }
        return new TaxRateSchedule(periods);
    }

    public int seed(final TaxRateTable configured, final Collection<MarketCode> markets,
                    final Instant from) {
        final Instant now = timeProvider.now();
        int inserted = 0;
        for (final TaxRatePeriod period : scheduleOf(configured, markets, from).periods()) {
            if (!repository.hasApproved(period.market(), period.category())
                    && repository.insertSeed(TaxRateProposal.preApproved(
                            idGenerator.newEventId(), period, SEED_ACTOR, now, SEED_REASON))) {
                inserted++;
            }
        }
        return inserted;
    }
}
