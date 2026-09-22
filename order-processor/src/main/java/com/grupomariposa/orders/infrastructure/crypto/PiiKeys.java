package com.grupomariposa.orders.infrastructure.crypto;

import java.util.Objects;
import java.util.Optional;

public record PiiKeys(String activeKeyId, String activeKey, String previousKeyId,
                      String previousKey) {

    public PiiKeys {
        Objects.requireNonNull(activeKeyId, "activeKeyId");
        Objects.requireNonNull(activeKey, "activeKey");
    }

    public static PiiKeys single(final String keyId, final String key) {
        return new PiiKeys(keyId, key, null, null);
    }

    public Optional<PiiKeys> previous() {
        if (isBlank(previousKeyId) || isBlank(previousKey)) {
            return Optional.empty();
        }
        return Optional.of(single(previousKeyId, previousKey));
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
