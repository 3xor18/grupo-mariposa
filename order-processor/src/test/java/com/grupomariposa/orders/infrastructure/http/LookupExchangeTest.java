package com.grupomariposa.orders.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.grupomariposa.orders.application.error.ExternalTransientException;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.web.client.RestClient;

class LookupExchangeTest {

    @Test
    void should_treat_token_acquisition_failures_as_transient() {
        final RestClient failingAuth = RestClient.builder()
                .baseUrl("http://localhost:1")
                .requestInterceptor((request, body, execution) -> {
                    throw new ClientAuthorizationException(new OAuth2Error("server_error"),
                            "order-processor");
                })
                .build();
        final LookupExchange exchange = new LookupExchange(Dependency.CLIENTS_API,
                new RetryAfterParser(Clock.systemUTC()));

        assertThatThrownBy(() -> exchange.fetch(failingAuth.get().uri("/clients/CLI-1"),
                String.class, body -> body))
                .isInstanceOf(ExternalTransientException.class)
                .hasMessage("clients-api token could not be obtained");
    }
}
