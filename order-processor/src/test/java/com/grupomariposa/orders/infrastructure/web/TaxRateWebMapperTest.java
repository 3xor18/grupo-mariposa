package com.grupomariposa.orders.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.grupomariposa.orders.application.command.TaxRateProposalCommand;
import com.grupomariposa.orders.application.query.TaxRateFilter;
import com.grupomariposa.orders.domain.Markets;
import com.grupomariposa.orders.domain.model.Rate;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRatePeriod;
import com.grupomariposa.orders.domain.model.TaxRateProposal;
import com.grupomariposa.orders.domain.model.TaxRateStatus;
import com.grupomariposa.orders.infrastructure.web.dto.TaxRateRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class TaxRateWebMapperTest {

    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");

    private final TaxRateWebMapper mapper = new TaxRateWebMapper();

    @Test
    void should_parse_filters_and_report_invalid_values() {
        assertThat(mapper.filter("MX", "STANDARD", "APPROVED")).isEqualTo(
                new TaxRateFilter(Markets.MX, TaxCategory.STANDARD, TaxRateStatus.APPROVED));
        assertThat(mapper.filter(null, " ", null)).isEqualTo(new TaxRateFilter(null, null, null));
        assertThatThrownBy(() -> mapper.filter("MEX", "LUXURY", "DONE"))
                .isInstanceOfSatisfying(InvalidRequestException.class, invalid ->
                        assertThat(invalid.violations()).extracting(FieldViolation::field)
                                .containsExactly("market", "category", "status"));
    }

    @Test
    void should_build_commands_with_the_authenticated_proposer() {
        final TaxRateProposalCommand command = mapper.command(new TaxRateRequest("MX",
                "REDUCED", new BigDecimal("0.08"), "2027-01-01T00:00:00Z", null, "why"),
                jwt("alice", "sub-1"));

        assertThat(command.market()).isEqualTo(Markets.MX);
        assertThat(command.validFrom()).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
        assertThat(command.proposer()).isEqualTo("alice");
        assertThatThrownBy(() -> mapper.command(new TaxRateRequest("MX", "REDUCED",
                BigDecimal.ONE, "tomorrow", "2027", "why"), null))
                .isInstanceOfSatisfying(InvalidRequestException.class, invalid ->
                        assertThat(invalid.violations()).extracting(FieldViolation::field)
                                .containsExactly("validFrom", "validTo"));
    }

    @Test
    void should_resolve_actors_from_jwt_or_authentication_name() {
        assertThat(mapper.actor(jwt(null, "sub-1"))).isEqualTo("sub-1");
        assertThat(mapper.actor(new TestingAuthenticationToken("local", "n/a")))
                .isEqualTo("local");
        assertThat(mapper.actor(null)).isEqualTo(TaxRateWebMapper.ANONYMOUS);
    }

    @Test
    void should_map_proposals_to_responses() {
        final TaxRateProposal proposal = TaxRateProposal.proposed("id-1", new TaxRatePeriod(
                Markets.MX, TaxCategory.STANDARD, Rate.ofPercent(17), NOW, null), "alice", NOW,
                "why");

        assertThat(mapper.toResponse(proposal).reviewedBy()).isNull();
        assertThat(mapper.toResponse(proposal.approvedBy("bob", NOW)).reviewedBy())
                .isEqualTo("bob");
    }

    private static JwtAuthenticationToken jwt(final String username, final String subject) {
        final Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none")
                .subject(subject);
        if (username != null) {
            builder.claims(claims -> claims.putAll(
                    Map.of(TaxRateWebMapper.PREFERRED_USERNAME, username)));
        }
        return new JwtAuthenticationToken(builder.build());
    }
}
