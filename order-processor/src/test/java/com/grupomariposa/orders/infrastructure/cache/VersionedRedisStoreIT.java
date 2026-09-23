package com.grupomariposa.orders.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class VersionedRedisStoreIT {

    private static final int REDIS_PORT = 6379;
    private static final String KEY = "clients:CLI-LUA1";
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(REDIS_PORT);

    private static LettuceConnectionFactory connections;
    private static StringRedisTemplate redis;
    private static VersionedRedisStore store;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connections = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT)));
        connections.afterPropertiesSet();
        connections.start();
        redis = new StringRedisTemplate(connections);
        store = new VersionedRedisStore(redis);
    }

    @AfterAll
    static void stopRedis() {
        connections.destroy();
        REDIS.stop();
    }

    @BeforeEach
    void clean() {
        redis.delete(KEY);
    }

    @Test
    void should_compare_versions_numerically_not_lexicographically() {
        assertThat(store.put(KEY, 9, "nine", TTL)).isTrue();
        assertThat(store.put(KEY, 10, "ten", TTL)).isTrue();
        assertThat(store.put(KEY, 9, "nine-again", TTL)).isFalse();

        assertThat(store.read(KEY)).contains("ten");
        assertThat(version()).isEqualTo("10");
    }

    @Test
    void should_not_rewrite_an_entry_with_the_same_version() {
        assertThat(store.put(KEY, 5, "first", TTL)).isTrue();
        assertThat(store.put(KEY, 5, "second", TTL)).isFalse();

        assertThat(store.read(KEY)).contains("first");
    }

    @Test
    void should_keep_the_eviction_version_as_a_floor() {
        assertThat(store.put(KEY, 6, "six", TTL)).isTrue();
        assertThat(store.evict(KEY, 7, TTL)).isTrue();
        assertThat(store.read(KEY)).isEmpty();
        assertThat(version()).isEqualTo("7");

        assertThat(store.put(KEY, 6, "older", TTL)).isFalse();
        assertThat(store.read(KEY)).isEmpty();
        assertThat(store.put(KEY, 7, "same", TTL)).isTrue();
        assertThat(store.read(KEY)).contains("same");
        assertThat(store.evict(KEY, 7, TTL)).isFalse();
        assertThat(store.evict(KEY, 8, TTL)).isTrue();
        assertThat(store.read(KEY)).isEmpty();
        assertThat(store.put(KEY, 9, "newer", TTL)).isTrue();
        assertThat(store.read(KEY)).contains("newer");
    }

    @Test
    void should_set_the_ttl_on_every_applied_write() {
        store.put(KEY, 1, "one", TTL);
        assertThat(redis.getExpire(KEY, TimeUnit.MILLISECONDS)).isPositive()
                .isLessThanOrEqualTo(TTL.toMillis());

        store.evict(KEY, 2, TTL);
        assertThat(redis.getExpire(KEY, TimeUnit.MILLISECONDS)).isPositive();
    }

    @Test
    void should_replace_legacy_non_hash_entries() {
        redis.opsForValue().set(KEY, "{\"productId\":\"PRD-001\"}");

        assertThat(store.put(KEY, 1, "fresh", TTL)).isTrue();

        assertThat(redis.type(KEY)).isEqualTo(DataType.HASH);
        assertThat(store.read(KEY)).contains("fresh");
    }

    private String version() {
        return redis.<String, String>opsForHash().get(KEY, "v");
    }
}
