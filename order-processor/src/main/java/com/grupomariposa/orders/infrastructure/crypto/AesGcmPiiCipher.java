package com.grupomariposa.orders.infrastructure.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesGcmPiiCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String SEPARATOR = ":";
    private static final String INVALID_KEY = "PII key %s must be base64 of 32 bytes";
    private static final String UNKNOWN_KEY = "Ciphertext uses an unknown key id";
    private static final String MALFORMED = "Ciphertext is malformed";
    private static final String DECRYPTION_FAILED = "Ciphertext failed authentication";
    private static final String ENCRYPTION_FAILED = "Encryption failed";

    private final String activeKeyId;
    private final Map<String, SecretKey> keys;
    private final SecureRandom random = new SecureRandom();

    public AesGcmPiiCipher(final PiiKeys keys) {
        this.activeKeyId = keys.activeKeyId();
        final Map<String, SecretKey> ring = new HashMap<>();
        keys.previous().ifPresent(previous -> ring.put(previous.activeKeyId(),
                keyOf(previous.activeKeyId(), previous.activeKey())));
        ring.put(keys.activeKeyId(), keyOf(keys.activeKeyId(), keys.activeKey()));
        this.keys = Map.copyOf(ring);
    }

    public String encrypt(final String plaintext) {
        if (plaintext == null) {
            return null;
        }
        final byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            final Cipher cipher = cipher(Cipher.ENCRYPT_MODE, keys.get(activeKeyId), iv,
                    activeKeyId);
            final byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            final byte[] payload = ByteBuffer.allocate(IV_BYTES + sealed.length)
                    .put(iv).put(sealed).array();
            return activeKeyId + SEPARATOR + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException failure) {
            throw new PiiCryptoException(ENCRYPTION_FAILED, failure);
        }
    }

    public String decrypt(final String token) {
        if (token == null) {
            return null;
        }
        final int separator = token.indexOf(SEPARATOR);
        if (separator <= 0) {
            throw new PiiCryptoException(MALFORMED, null);
        }
        final String keyId = token.substring(0, separator);
        final SecretKey key = keys.get(keyId);
        if (key == null) {
            throw new PiiCryptoException(UNKNOWN_KEY, null);
        }
        return open(keyId, key, decode(token.substring(separator + 1)));
    }

    private String open(final String keyId, final SecretKey key, final byte[] payload) {
        if (payload.length <= IV_BYTES) {
            throw new PiiCryptoException(MALFORMED, null);
        }
        try {
            final ByteBuffer buffer = ByteBuffer.wrap(payload);
            final byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            final byte[] sealed = new byte[buffer.remaining()];
            buffer.get(sealed);
            final Cipher cipher = cipher(Cipher.DECRYPT_MODE, key, iv, keyId);
            return new String(cipher.doFinal(sealed), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException failure) {
            throw new PiiCryptoException(DECRYPTION_FAILED, failure);
        }
    }

    private static byte[] decode(final String encoded) {
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException invalid) {
            throw new PiiCryptoException(MALFORMED, invalid);
        }
    }

    private static Cipher cipher(final int mode, final SecretKey key, final byte[] iv,
                                 final String keyId) throws GeneralSecurityException {
        final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(keyId.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private static SecretKey keyOf(final String keyId, final String base64) {
        final byte[] material;
        try {
            material = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException(INVALID_KEY.formatted(keyId), invalid);
        }
        if (material.length != KEY_BYTES) {
            throw new IllegalStateException(INVALID_KEY.formatted(keyId));
        }
        return new SecretKeySpec(material, KEY_ALGORITHM);
    }
}
