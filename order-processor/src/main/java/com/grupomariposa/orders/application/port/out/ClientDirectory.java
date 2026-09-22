package com.grupomariposa.orders.application.port.out;

import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.Lookup;

public interface ClientDirectory {

    Lookup<ClientProfile> findClient(String clientId);
}
