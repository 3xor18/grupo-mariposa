package com.grupomariposa.orders.infrastructure.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class VersionedTest {

    @Test
    void should_hold_value_with_non_negative_version() {
        assertThat(new Versioned<>("x", Versioned.UNKNOWN_VERSION).version()).isZero();
        assertThatIllegalArgumentException().isThrownBy(() -> new Versioned<>("x", -1L));
        assertThatNullPointerException().isThrownBy(() -> new Versioned<>(null, 1L));
    }
}
