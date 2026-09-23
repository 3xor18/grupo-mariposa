package com.grupomariposa.orders.infrastructure.http;

import com.grupomariposa.orders.application.error.ExternalPermanentException;
import com.grupomariposa.orders.application.error.ExternalTransientException;
import com.grupomariposa.orders.domain.model.Lookup;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import org.springframework.web.client.RestClientException;

public final class LookupExchange {

    private static final Set<Integer> TRANSIENT_STATUSES = Set.of(
            HttpStatus.REQUEST_TIMEOUT.value(), HttpStatus.TOO_MANY_REQUESTS.value(),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.BAD_GATEWAY.value(), HttpStatus.SERVICE_UNAVAILABLE.value(),
            HttpStatus.GATEWAY_TIMEOUT.value());
    private static final String RESPONDED = "%s responded %d";
    private static final String UNREACHABLE = "%s unreachable or timed out";
    private static final String INVALID_BODY = "%s returned an unreadable body";
    private static final String TOKEN_FAILURE = "%s token could not be obtained";

    private final Dependency dependency;
    private final RetryAfterParser retryAfterParser;

    public LookupExchange(final Dependency dependency, final RetryAfterParser retryAfterParser) {
        this.dependency = Objects.requireNonNull(dependency, "dependency");
        this.retryAfterParser = Objects.requireNonNull(retryAfterParser, "retryAfterParser");
    }

    public <B, T> Lookup<T> fetch(final RestClient.RequestHeadersSpec<?> request,
                                  final Class<B> bodyType, final Function<B, T> mapper) {
        try {
            return request.exchange((ignored, response) ->
                    interpret(response, bodyType, mapper), true);
        } catch (ResourceAccessException unreachable) {
            throw new ExternalTransientException(dependency.id(),
                    UNREACHABLE.formatted(dependency.id()), unreachable);
        } catch (ClientAuthorizationException tokenFailure) {
            throw new ExternalTransientException(dependency.id(),
                    TOKEN_FAILURE.formatted(dependency.id()), tokenFailure);
        } catch (RestClientException unreadable) {
            throw new ExternalPermanentException(dependency.id(),
                    INVALID_BODY.formatted(dependency.id()), unreadable);
        }
    }

    private <B, T> Lookup<T> interpret(final ConvertibleClientHttpResponse response,
                                       final Class<B> bodyType,
                                       final Function<B, T> mapper) throws IOException {
        final HttpStatusCode status = response.getStatusCode();
        if (status.is2xxSuccessful()) {
            return Lookup.found(mapper.apply(readBody(response, bodyType)));
        }
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return Lookup.notFound();
        }
        throw failureFor(status, response.getHeaders());
    }

    private <B> B readBody(final ConvertibleClientHttpResponse response,
                           final Class<B> bodyType) {
        final B body = response.bodyTo(bodyType);
        if (body == null) {
            throw new ExternalPermanentException(dependency.id(),
                    INVALID_BODY.formatted(dependency.id()), null);
        }
        return body;
    }

    private RuntimeException failureFor(final HttpStatusCode status, final HttpHeaders headers) {
        final String message = RESPONDED.formatted(dependency.id(), status.value());
        if (TRANSIENT_STATUSES.contains(status.value())) {
            return new ExternalTransientException(dependency.id(), message,
                    retryAfterParser.parse(headers.getFirst(HttpHeaders.RETRY_AFTER))
                            .orElse(null), null);
        }
        return new ExternalPermanentException(dependency.id(), message, null);
    }
}
