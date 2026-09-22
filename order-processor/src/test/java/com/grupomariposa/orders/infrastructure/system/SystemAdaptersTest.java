package com.grupomariposa.orders.infrastructure.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class SystemAdaptersTest {

    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");

    @Test
    void should_generate_unique_time_ordered_uuid_v7() {
        final UuidV7Generator generator =
                new UuidV7Generator(Clock.fixed(NOW, ZoneOffset.UTC));
        final Set<String> ids = new HashSet<>();

        for (int index = 0; index < 1000; index++) {
            ids.add(generator.newEventId());
        }

        final UUID sample = UUID.fromString(ids.iterator().next());
        assertThat(ids).hasSize(1000);
        assertThat(sample.version()).isEqualTo(7);
        assertThat(sample.variant()).isEqualTo(2);
        assertThat(sample.getMostSignificantBits() >>> 16).isEqualTo(NOW.toEpochMilli());
    }

    @Test
    void should_read_time_from_clock() {
        assertThat(new SystemTimeProvider(Clock.fixed(NOW, ZoneOffset.UTC)).now()).isEqualTo(NOW);
    }

    @Test
    void should_run_lookups_on_virtual_threads() throws Exception {
        try (ManagedVirtualThreadExecutor executor = new ManagedVirtualThreadExecutor()) {
            final CompletableFuture<Boolean> virtual = CompletableFuture.supplyAsync(
                    () -> Thread.currentThread().isVirtual(), executor.executor());

            assertThat(virtual.get()).isTrue();
        }
    }
}
