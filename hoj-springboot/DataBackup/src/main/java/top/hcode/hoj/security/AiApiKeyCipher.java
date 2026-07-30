package top.hcode.hoj.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Encrypts AI provider credentials before they are stored in the database.
 */
@Component
public class AiApiKeyCipher {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final String encryptionSecret;
    private final SecureRandom secureRandom = new SecureRandom();

    public AiApiKeyCipher(
            @Value("${ai-assistant-key-encryption-secret:}") String encryptionSecret) {
        this.encryptionSecret =
                encryptionSecret == null ? "" : encryptionSecret.trim();
    }

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            throw new IllegalArgumentException("API Key 值不能为空");
        }
        requireConfigured();
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    deriveKey(),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(
                    plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("无法加密 AI API Key", exception);
        }
    }

    public String decrypt(String storedValue) {
        if (!isEncrypted(storedValue)) {
            throw new IllegalStateException("检测到未加密的 AI API Key，请在管理端重新录入");
        }
        requireConfigured();
        try {
            byte[] payload = Base64.getDecoder().decode(
                    storedValue.substring(PREFIX.length()));
            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException("Encrypted API Key payload is invalid");
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(
                    payload,
                    IV_LENGTH,
                    payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    deriveKey(),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(
                    cipher.doFinal(encrypted),
                    StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("无法解密 AI API Key，请检查加密密钥", exception);
        }
    }

    public String maskStoredValue(String storedValue) {
        if (!isEncrypted(storedValue)) {
            return "********";
        }
        String plainText = decrypt(storedValue);
        if (plainText.length() <= 8) {
            return "********";
        }
        return plainText.substring(0, 4)
                + "********"
                + plainText.substring(plainText.length() - 4);
    }

    public boolean isEncrypted(String value) {
        return StringUtils.hasText(value) && value.startsWith(PREFIX);
    }

    private SecretKeySpec deriveKey() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return new SecretKeySpec(
                digest.digest(encryptionSecret.getBytes(StandardCharsets.UTF_8)),
                "AES");
    }

    private void requireConfigured() {
        if (encryptionSecret.length() < 32) {
            throw new IllegalStateException(
                    "AI_ASSISTANT_KEY_ENCRYPTION_SECRET 必须至少包含 32 个字符");
        }
    }
}
