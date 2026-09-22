package com.grupomariposa.orders.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AesGcmPiiCipherTest {

    private static final String NAME = "Distribuidora Central";
    private final String key = randomKey();
    private final AesGcmPiiCipher cipher = new AesGcmPiiCipher(
            new PiiProperties(key, "k1", null, null));

    @Test
    void should_round_trip_with_key_id_prefix_and_random_iv() {
        final String first = cipher.encrypt(NAME);
        final String second = cipher.encrypt(NAME);

        assertThat(first).startsWith("k1:").doesNotContain(NAME).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo(NAME);
        assertThat(cipher.decrypt(second)).isEqualTo(NAME);
    }

    @Test
    void should_pass_nulls_through() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    void should_detect_tampering() {
        final String sealed = cipher.encrypt(NAME);
        final byte[] payload = Base64.getDecoder().decode(sealed.substring(3));
        payload[payload.length - 1] ^= 1;
        final String tampered = "k1:" + Base64.getEncoder().encodeToString(payload);

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(PiiCryptoException.class)
                .hasMessage("Ciphertext failed authentication");
    }

    @Test
    void should_reject_ciphertext_from_another_key() {
        final AesGcmPiiCipher other = new AesGcmPiiCipher(
                new PiiProperties(randomKey(), "k1", null, null));

        assertThatThrownBy(() -> other.decrypt(cipher.encrypt(NAME)))
                .isInstanceOf(PiiCryptoException.class);
    }

    @Test
    void should_decrypt_values_sealed_with_previous_key_after_rotation() {
        final String legacy = cipher.encrypt(NAME);
        final AesGcmPiiCipher rotated = new AesGcmPiiCipher(
                new PiiProperties(randomKey(), "k2", key, "k1"));

        assertThat(rotated.decrypt(legacy)).isEqualTo(NAME);
        assertThat(rotated.encrypt(NAME)).startsWith("k2:");
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-separator", ":missing-key", "k1:%%%", "k1:AAAA"})
    void should_reject_malformed_ciphertext(final String token) {
        assertThatThrownBy(() -> cipher.decrypt(token)).isInstanceOf(PiiCryptoException.class);
    }

    @Test
    void should_reject_unknown_key_ids() {
        assertThatThrownBy(() -> cipher.decrypt("k9:" + cipher.encrypt(NAME).substring(3)))
                .isInstanceOf(PiiCryptoException.class)
                .hasMessage("Ciphertext uses an unknown key id");
    }

    @Test
    void should_fail_fast_on_invalid_key_material() {
        assertThatIllegalStateException().isThrownBy(() -> new AesGcmPiiCipher(
                new PiiProperties("not-base64!", "k1", null, null)));
        assertThatIllegalStateException().isThrownBy(() -> new AesGcmPiiCipher(
                new PiiProperties(Base64.getEncoder().encodeToString(new byte[16]), "k1",
                        " ", " ")));
    }

    private static String randomKey() {
        final byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
