package com.grupomariposa.orders.infrastructure.persistence.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.grupomariposa.orders.domain.Markets;
import com.grupomariposa.orders.domain.model.Rate;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRatePeriod;
import com.grupomariposa.orders.domain.model.TaxRateProposal;
import com.grupomariposa.orders.infrastructure.persistence.document.TaxRateDocument;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TaxRateDocumentMapperTest {

    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");
    private static final Instant JAN_2027 = Instant.parse("2027-01-01T00:00:00Z");
    private static final TaxRateProposal PROPOSED = TaxRateProposal.proposed("id-1",
            new TaxRatePeriod(Markets.CL, TaxCategory.REDUCED, Rate.ofPercent(19), JAN_2027,
                    null), "alice", NOW, "IVA");

    private final TaxRateDocumentMapper mapper = new TaxRateDocumentMapper();

    @Test
    void should_round_trip_every_review_state() {
        final TaxRateProposal approved = PROPOSED.approvedBy("bob", NOW);
        final TaxRateProposal rejected = PROPOSED.rejectedBy("bob", NOW);

        assertThat(mapper.toDomain(mapper.toDocument(PROPOSED))).isEqualTo(PROPOSED);
        assertThat(mapper.toDomain(mapper.toDocument(approved))).isEqualTo(approved);
        assertThat(mapper.toDomain(mapper.toDocument(rejected))).isEqualTo(rejected);
        final TaxRateDocument document = mapper.toDocument(approved);
        assertThat(document.approvedBy()).isEqualTo("bob");
        assertThat(document.rejectedBy()).isNull();
        assertThat(mapper.toDocument(rejected).rejectedAt()).isEqualTo(NOW);
        assertThat(mapper.period(document)).isEqualTo(approved.period());
    }
}
