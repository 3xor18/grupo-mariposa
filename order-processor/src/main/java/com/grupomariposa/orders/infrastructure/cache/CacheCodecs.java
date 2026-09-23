package com.grupomariposa.orders.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.infrastructure.crypto.AesGcmPiiCipher;
import com.grupomariposa.orders.infrastructure.crypto.PiiCryptoException;
import java.util.Optional;
import java.util.function.UnaryOperator;

public final class CacheCodecs {

    private CacheCodecs() {
    }

    public static CacheCodec<ProductProfile> products(final ObjectMapper objectMapper) {
        return new JsonCacheCodec<>(objectMapper, CachedProduct.class, CachedProduct::from,
                cached -> Optional.of(cached).filter(CachedProduct::isComplete)
                        .map(CachedProduct::toProfile));
    }

    public static CacheCodec<ClientProfile> clients(final ObjectMapper objectMapper,
                                                    final AesGcmPiiCipher cipher) {
        final UnaryOperator<String> encrypt = guarded(cipher::encrypt);
        final UnaryOperator<String> decrypt = guarded(cipher::decrypt);
        return new JsonCacheCodec<>(objectMapper, CachedClient.class,
                profile -> CachedClient.from(profile, encrypt),
                cached -> cached.toProfile(decrypt));
    }

    private static UnaryOperator<String> guarded(final UnaryOperator<String> operation) {
        return value -> {
            try {
                return operation.apply(value);
            } catch (PiiCryptoException failure) {
                throw new CacheCodecException(failure);
            }
        };
    }
}
