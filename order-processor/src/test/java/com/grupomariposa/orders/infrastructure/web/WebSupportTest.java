package com.grupomariposa.orders.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.grupomariposa.orders.application.query.OrderSummary;
import com.grupomariposa.orders.application.query.PageResult;
import com.grupomariposa.orders.domain.model.Currency;
import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.Money;
import com.grupomariposa.orders.domain.model.OrderStatus;
import com.grupomariposa.orders.domain.model.RejectionCode;
import com.grupomariposa.orders.infrastructure.observability.TraceContext;
import com.grupomariposa.orders.infrastructure.persistence.PersistenceFixtures;
import com.grupomariposa.orders.infrastructure.web.dto.OrderPageResponse;
import com.grupomariposa.orders.infrastructure.web.dto.OrderResponse;
import com.grupomariposa.orders.infrastructure.web.security.RealmRoleConverter;
import com.grupomariposa.orders.infrastructure.web.security.SecurityModeGuard;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class WebSupportTest {

    @Test
    void should_map_technical_failures_with_failure_block_and_null_amounts() {
        final OrderResponse response = new OrderResponseMapper().toResponse(
                PersistenceFixtures.technicalFailure());

        assertThat(response.failure().attempts()).isEqualTo(4);
        assertThat(response.lines()).allSatisfy(line ->
                assertThat(line.grossSubtotal()).isNull());
        assertThat(response.client().name()).isNull();
    }

    @Test
    void should_map_pages_with_reasons() {
        final OrderPageResponse page = new OrderResponseMapper().toPage(new PageResult<>(List.of(
                new OrderSummary("ORD-1", OrderStatus.REJECTED, Market.MX, Currency.MXN, "C",
                        1, Money.ZERO, RejectionCode.PRODUCT_NOT_FOUND, Instant.EPOCH)),
                0, 20, 1));

        assertThat(page.items().getFirst().reason()).isEqualTo("PRODUCT_NOT_FOUND");
        assertThat(page.totalPages()).isOne();
    }

    @Test
    void should_convert_realm_roles_to_authorities() {
        final Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("user-1")
                .claim("preferred_username", "ana")
                .claim("realm_access", Map.of("roles", List.of("orders-reader", 7)))
                .build();

        assertThat(new RealmRoleConverter().convert(jwt).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_orders-reader");
        assertThat(new RealmRoleConverter().convert(jwt).getName()).isEqualTo("ana");
    }

    @Test
    void should_tolerate_tokens_without_realm_roles() {
        final Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("svc")
                .claim("realm_access", "invalid").build();

        assertThat(new RealmRoleConverter().convert(jwt).getAuthorities()).isEmpty();
        assertThat(new RealmRoleConverter().convert(jwt).getName()).isEqualTo("svc");
    }

    @Test
    void should_only_allow_disabled_security_in_local_profile() {
        final MockEnvironment production = new MockEnvironment();
        final MockEnvironment local = new MockEnvironment();
        local.setActiveProfiles(SecurityModeGuard.LOCAL_PROFILE);

        assertThatIllegalStateException().isThrownBy(() ->
                SecurityModeGuard.requireLocalWhenDisabled(false, production));
        SecurityModeGuard.requireLocalWhenDisabled(false, local);
        SecurityModeGuard.requireLocalWhenDisabled(true, production);
    }

    @Test
    void should_build_problem_with_fallback_instance_and_generated_trace() {
        final TraceContext traces = mock(TraceContext.class);
        when(traces.currentTraceId()).thenReturn(Optional.empty());
        final ProblemFactory factory = new ProblemFactory(Clock.systemUTC(), traces,
                "https://contracts.grupomariposa.dev/problems/");

        final ProblemDetail problem = factory.create(HttpStatus.NOT_FOUND,
                ApiErrorCode.ORDER_NOT_FOUND, "missing", "bad path with spaces");

        assertThat(problem.getInstance()).hasToString("/");
        assertThat(problem.getType()).hasToString(
                "https://contracts.grupomariposa.dev/problems/order-not-found");
        assertThat((String) problem.getProperties().get(ProblemFactory.TRACE_ID)).hasSize(32);
        assertThat(factory.create(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, "x", null)
                .getInstance()).hasToString("/");
    }
}
