package com.meridian.common.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/**
 * Verifies AES-256-GCM converter round-trip and ciphertext opacity.
 */
class AesAttributeConverterTest {

    // 32-byte key encoded as base64 (same placeholder as application.yml default)
    private static final String TEST_KEY_BASE64 =
            Base64.getEncoder().encodeToString(new byte[32]);

    private final AesAttributeConverter converter = new AesAttributeConverter(TEST_KEY_BASE64);

    @Test
    void nullRoundTripReturnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"ip\":\"192.168.1.1\"}",
            "sensitive audit payload with special chars: <>&\"'",
            "unicode: 日本語テスト",
    })
    void encryptThenDecryptRestoresPlaintext(String plaintext) {
        String ciphertext = converter.convertToDatabaseColumn(plaintext);
        assertThat(ciphertext).isNotNull().isNotEqualTo(plaintext);

        String restored = converter.convertToEntityAttribute(ciphertext);
        assertThat(restored).isEqualTo(plaintext);
    }

    @Test
    void eachEncryptionProducesDifferentCiphertext() {
        String plaintext = "{\"action\":\"LOGIN_SUCCESS\"}";
        String c1 = converter.convertToDatabaseColumn(plaintext);
        String c2 = converter.convertToDatabaseColumn(plaintext);
        // GCM uses a random IV per encryption — ciphertexts must differ
        assertThat(c1).isNotEqualTo(c2);
        assertThat(converter.convertToEntityAttribute(c1)).isEqualTo(plaintext);
        assertThat(converter.convertToEntityAttribute(c2)).isEqualTo(plaintext);
    }

    @Test
    void ciphertextDoesNotContainPlaintext() {
        String plaintext = "supersecret";
        String ciphertext = converter.convertToDatabaseColumn(plaintext);
        assertThat(ciphertext).doesNotContain(plaintext);
    }

    @Test
    void tamperingCiphertextThrowsException() {
        String ciphertext = converter.convertToDatabaseColumn("original");
        // Flip a byte to simulate tampering
        byte[] bytes = Base64.getDecoder().decode(ciphertext);
        bytes[bytes.length - 1] ^= 0xFF;
        String tampered = Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(IllegalStateException.class);
    }
}
