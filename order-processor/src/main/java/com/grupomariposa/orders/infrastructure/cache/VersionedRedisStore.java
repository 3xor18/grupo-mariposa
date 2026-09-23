package com.grupomariposa.orders.infrastructure.cache;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

public final class VersionedRedisStore {

    static final String SCRIPT_LOCATION = "redis/set-if-newer.lua";
    static final String DATA_FIELD = "d";
    private static final String NO_PAYLOAD = "";
    private static final Long APPLIED = 1L;

    private final StringRedisTemplate redis;
    private final RedisScript<Long> setIfNewer;

    public VersionedRedisStore(final StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.setIfNewer = RedisScript.of(new ClassPathResource(SCRIPT_LOCATION), Long.class);
    }

    public Optional<String> read(final String key) {
        return Optional.ofNullable(redis.<String, String>opsForHash().get(key, DATA_FIELD));
    }

    public boolean put(final String key, final long version, final String payload,
                       final Duration ttl) {
        return execute(key, version, Objects.requireNonNull(payload, "payload"), ttl);
    }

    public boolean evict(final String key, final long version, final Duration ttl) {
        return execute(key, version, NO_PAYLOAD, ttl);
    }

    private boolean execute(final String key, final long version, final String payload,
                            final Duration ttl) {
        return APPLIED.equals(redis.execute(setIfNewer, List.of(key), Long.toString(version),
                payload, Long.toString(ttl.toMillis())));
    }
}
