package com.grupomariposa.orders.infrastructure.cache;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.ClientSegment;
import com.grupomariposa.orders.domain.model.ClientStatus;
import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.TaxRegime;
import java.util.Optional;
import java.util.function.UnaryOperator;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CachedClient(String clientId, String encryptedName, ClientStatus status,
                           ClientSegment segment, TaxRegime taxRegime, String market) {

    public static CachedClient from(final ClientProfile profile,
                                    final UnaryOperator<String> encryptName) {
        return new CachedClient(profile.clientId(), encryptName.apply(profile.name()),
                profile.status(), profile.segment(), profile.taxRegime(),
                profile.market().value());
    }

    public Optional<ClientProfile> toProfile(final UnaryOperator<String> decryptName) {
        if (clientId == null || status == null || segment == null || taxRegime == null) {
            return Optional.empty();
        }
        return MarketCode.parse(market).map(code -> new ClientProfile(clientId,
                decryptName.apply(encryptedName), status, segment, taxRegime, code));
    }
}
