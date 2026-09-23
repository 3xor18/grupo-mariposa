package com.grupomariposa.orders.infrastructure.system;

import com.grupomariposa.orders.application.port.out.IdGenerator;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

public final class UuidV7Generator implements IdGenerator {

    private static final int TIMESTAMP_SHIFT = 16;
    private static final long VERSION_BITS = 0x7000L;
    private static final long RANDOM_A_MASK = 0x0FFFL;
    private static final long VARIANT_BITS = 0x8000000000000000L;
    private static final long RANDOM_B_MASK = 0x3FFFFFFFFFFFFFFFL;

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public UuidV7Generator(final Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String newEventId() {
        final long millis = clock.millis();
        final long high = (millis << TIMESTAMP_SHIFT) | VERSION_BITS
                | (random.nextLong() & RANDOM_A_MASK);
        final long low = VARIANT_BITS | (random.nextLong() & RANDOM_B_MASK);
        return new UUID(high, low).toString();
    }
}
