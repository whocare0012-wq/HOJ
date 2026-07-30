package top.hcode.hoj.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiApiKeyCipherTest {

    private static final String SECRET =
            "test-only-secret-with-at-least-32-characters";

    @Test
    void encryptsWithRandomizedAuthenticatedEncryption() {
        AiApiKeyCipher cipher = new AiApiKeyCipher(SECRET);

        String first = cipher.encrypt("sk-example-123456789");
        String second = cipher.encrypt("sk-example-123456789");

        assertTrue(cipher.isEncrypted(first));
        assertFalse(first.equals(second));
        assertEquals("sk-example-123456789", cipher.decrypt(first));
    }

    @Test
    void rejectsPlaintextAndWrongSecrets() {
        AiApiKeyCipher cipher = new AiApiKeyCipher(SECRET);
        assertThrows(
                IllegalStateException.class,
                () -> cipher.decrypt("sk-plaintext"));

        String encrypted = cipher.encrypt("sk-example-123456789");
        AiApiKeyCipher wrongCipher = new AiApiKeyCipher(
                "another-test-secret-with-at-least-32-chars");
        assertThrows(
                IllegalStateException.class,
                () -> wrongCipher.decrypt(encrypted));
    }

    @Test
    void requiresAProductionStrengthConfigurationSecret() {
        AiApiKeyCipher cipher = new AiApiKeyCipher("too-short");
        assertThrows(
                IllegalStateException.class,
                () -> cipher.encrypt("sk-example-123456789"));
    }
}
