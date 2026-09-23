package com.grupomariposa.orders.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class VersionedRedisStoreTest {

    private static final Duration TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final VersionedRedisStore store = new VersionedRedisStore(redis);

    @Test
    @SuppressWarnings("unchecked")
    void should_read_the_data_field_of_the_hash() {
        final HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(hashes.get("clients:CLI-1", VersionedRedisStore.DATA_FIELD)).thenReturn("{}");

        assertThat(store.read("clients:CLI-1")).contains("{}");
        assertThat(store.read("clients:CLI-2")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_report_whether_the_script_applied_the_write() {
        when(redis.execute(any(RedisScript.class), eq(List.of("k")), eq("3"), eq("{}"),
                eq("60000"))).thenReturn(1L);
        when(redis.execute(any(RedisScript.class), eq(List.of("k")), eq("4"), eq(""),
                eq("60000"))).thenReturn(0L);

        assertThat(store.put("k", 3L, "{}", TTL)).isTrue();
        assertThat(store.evict("k", 4L, TTL)).isFalse();
    }
}
