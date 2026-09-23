package com.grupomariposa.orders.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupomariposa.orders.domain.Markets;
import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.ClientSegment;
import com.grupomariposa.orders.domain.model.ClientStatus;
import com.grupomariposa.orders.domain.model.TaxRegime;
import com.grupomariposa.orders.infrastructure.crypto.AesGcmPiiCipher;
import com.grupomariposa.orders.infrastructure.crypto.PiiKeys;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CacheCodecsTest {

    private static final int KEY_BYTES = 32;
    private static final String NAME = "Distribuidora Central";
    private static final ClientProfile CLIENT = new ClientProfile("CLI-1", NAME,
            ClientStatus.ACTIVE, ClientSegment.WHOLESALE, TaxRegime.GENERAL, Markets.MX);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CacheCodec<ClientProfile> clients = CacheCodecs.clients(objectMapper,
            new AesGcmPiiCipher(PiiKeys.single("k1", randomKey())));

    @Test
    void should_round_trip_clients_keeping_the_name_encrypted() {
        final String payload = clients.encode(CLIENT);

        assertThat(payload).doesNotContain(NAME).contains("\"encryptedName\":\"k1:");
        assertThat(clients.decode(payload)).contains(CLIENT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"clientId\":\"CLI-1\"}",
        "{\"status\":\"ACTIVE\",\"segment\":\"RETAIL\",\"taxRegime\":\"GENERAL\"}",
        "{\"clientId\":\"CLI-1\",\"status\":\"ACTIVE\",\"segment\":\"RETAIL\"}",
        "{\"clientId\":\"CLI-1\",\"status\":\"ACTIVE\",\"taxRegime\":\"GENERAL\"}",
        "{\"clientId\":\"CLI-1\",\"segment\":\"RETAIL\",\"taxRegime\":\"GENERAL\"}",
        "{\"clientId\":\"CLI-1\",\"status\":\"ACTIVE\",\"segment\":\"RETAIL\","
                + "\"taxRegime\":\"GENERAL\",\"market\":\"mexico\"}"})
    void should_ignore_incomplete_client_entries(final String payload) {
        assertThat(clients.decode(payload)).isEmpty();
    }

    @Test
    void should_reject_names_encrypted_with_unknown_keys() {
        final String foreign = CacheCodecs.clients(objectMapper,
                new AesGcmPiiCipher(PiiKeys.single("k9", randomKey()))).encode(CLIENT);

        assertThatThrownBy(() -> clients.decode(foreign))
                .isInstanceOf(CacheCodecException.class);
    }

    @Test
    void should_wrap_serialization_failures() throws JsonProcessingException {
        final ObjectMapper broken = mock(ObjectMapper.class);
        when(broken.writeValueAsString(any())).thenThrow(new JsonProcessingException("x") {
            private static final long serialVersionUID = 1L;
        });

        assertThatThrownBy(() -> CacheCodecs.clients(broken,
                new AesGcmPiiCipher(PiiKeys.single("k1", randomKey()))).encode(CLIENT))
                .isInstanceOf(CacheCodecException.class);
    }

    private static String randomKey() {
        final byte[] key = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
