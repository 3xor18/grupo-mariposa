package com.grupomariposa.orders.infrastructure.http.client;

import com.grupomariposa.orders.application.error.ExternalPermanentException;
import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.ClientSegment;
import com.grupomariposa.orders.domain.model.ClientStatus;
import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.TaxRegime;
import com.grupomariposa.orders.infrastructure.http.Dependency;
import com.grupomariposa.orders.infrastructure.http.EnumParser;

public final class ClientResponseMapper {

    private static final String MISSING_ID = "clients-api returned a client without id";
    private static final String STATUS = "status";
    private static final String SEGMENT = "segment";
    private static final String TAX_REGIME = "taxRegime";
    private static final String MARKET = "market";

    private final EnumParser parser = new EnumParser(Dependency.CLIENTS_API);

    public ClientProfile toProfile(final ClientResponse response) {
        if (response.clientId() == null || response.clientId().isBlank()) {
            throw new ExternalPermanentException(Dependency.CLIENTS_API.id(), MISSING_ID, null);
        }
        return new ClientProfile(response.clientId(), response.name(),
                parser.parse(ClientStatus.class, STATUS, response.status()),
                parser.parse(ClientSegment.class, SEGMENT, response.segment()),
                parser.parse(TaxRegime.class, TAX_REGIME, response.taxRegime()),
                Market.fromCode(response.market()).orElseThrow(() ->
                        parser.invalid(MARKET)));
    }
}
