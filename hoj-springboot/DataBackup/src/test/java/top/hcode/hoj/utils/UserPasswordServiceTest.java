package top.hcode.hoj.utils;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.BCrypt;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPasswordServiceTest {

    private final UserPasswordService service = new UserPasswordService();

    @Test
    void readsLegacyMd5AndBcryptHashes() {
        String rawPassword = "compatible-password";

        assertTrue(service.matches(rawPassword, SecureUtil.md5(rawPassword)));
        assertTrue(service.matches(rawPassword, BCrypt.hashpw(rawPassword)));
        assertFalse(service.matches("wrong-password", SecureUtil.md5(rawPassword)));
        assertFalse(service.matches("wrong-password", BCrypt.hashpw(rawPassword)));
    }

    @Test
    void writeFormatIsControlledByRolloutSwitch() {
        String rawPassword = "rollout-password";

        ReflectionTestUtils.setField(service, "bcryptWriteEnabled", false);
        assertTrue(service.encode(rawPassword).equals(SecureUtil.md5(rawPassword)));

        ReflectionTestUtils.setField(service, "bcryptWriteEnabled", true);
        assertTrue(service.matches(rawPassword, service.encode(rawPassword)));
    }
}
