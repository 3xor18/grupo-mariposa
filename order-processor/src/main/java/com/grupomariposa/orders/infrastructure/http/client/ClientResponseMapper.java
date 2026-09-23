package com.grupomariposa.orders.infrastructure.http.client;

import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.ClientSegment;
import com.grupomariposa.orders.domain.model.ClientStatus;
import com.grupomariposa.orders.domain.model.Market;
import com.grupomariposa.orders.domain.model.TaxRegime;
import com.grupomariposa.orders.infrastructure.http.Dependency;
import com.grupomariposa.orders.infrastructure.http.ResponseFields;

public final class ClientResponseMapper {

    private static final String CLIENT_ID = "clientId";
    private static final String STATUS = "status";
    private static final String SEGMENT = "segment";
    private static final String TAX_REGIME = "taxRegime";
    private static final String MARKET = "market";

    private final ResponseFields fields = new ResponseFields(Dependency.CLIENTS_API);

    public ClientProfile toProfile(final ClientResponse response) {
        return new ClientProfile(fields.requireText(CLIENT_ID, response.clientId()),
                response.name(),
                fields.parse(ClientStatus.class, STATUS, response.status()),
                fields.parse(ClientSegment.class, SEGMENT, response.segment()),
                fields.parse(TaxRegime.class, TAX_REGIME, response.taxRegime()),
                Market.fromCode(response.market()).orElseThrow(() -> fields.invalid(MARKET)));
    }
}
