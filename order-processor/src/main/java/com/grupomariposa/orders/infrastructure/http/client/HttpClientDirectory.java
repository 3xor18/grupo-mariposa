package com.grupomariposa.orders.infrastructure.http.client;

import com.grupomariposa.orders.application.port.out.ClientDirectory;
import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.infrastructure.http.LookupExchange;
import com.grupomariposa.orders.infrastructure.http.ResilientExecutor;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public final class HttpClientDirectory implements ClientDirectory {

    private static final String CLIENT_PATH = "/clients/{clientId}";

    private final RestClient restClient;
    private final LookupExchange exchange;
    private final ResilientExecutor resilience;
    private final ClientResponseMapper mapper;

    public HttpClientDirectory(final RestClient restClient, final LookupExchange exchange,
                               final ResilientExecutor resilience,
                               final ClientResponseMapper mapper) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.resilience = Objects.requireNonNull(resilience, "resilience");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public Lookup<ClientProfile> findClient(final String clientId) {
        return resilience.execute(() -> exchange.fetch(
                restClient.get().uri(CLIENT_PATH, clientId).accept(MediaType.APPLICATION_JSON),
                ClientResponse.class, mapper::toProfile));
    }
}
