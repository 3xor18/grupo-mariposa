package com.grupomariposa.orders.infrastructure.cache;

import com.grupomariposa.orders.application.port.out.ClientDirectory;
import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.infrastructure.masterdata.VersionedClientSource;
import java.util.Objects;

public final class CachingClientDirectory implements ClientDirectory {

    private final VersionedClientSource source;
    private final VersionedCache<ClientProfile> cache;

    public CachingClientDirectory(final VersionedClientSource source,
                                  final VersionedCache<ClientProfile> cache) {
        this.source = Objects.requireNonNull(source, "source");
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    @Override
    public Lookup<ClientProfile> findClient(final String clientId) {
        return cache.readThrough(clientId, () -> source.findVersionedClient(clientId));
    }
}
