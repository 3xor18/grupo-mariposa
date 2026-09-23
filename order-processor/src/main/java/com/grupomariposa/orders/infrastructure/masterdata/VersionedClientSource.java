package com.grupomariposa.orders.infrastructure.masterdata;

import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.Lookup;

public interface VersionedClientSource {

    Lookup<Versioned<ClientProfile>> findVersionedClient(String clientId);
}
