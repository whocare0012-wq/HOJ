package top.hcode.hoj.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JudgeAccessVerifierTest {

    @Test
    void acceptsOnlyTheConfiguredJudgeToken() {
        JudgeAccessVerifier verifier = new JudgeAccessVerifier();
        ReflectionTestUtils.setField(
                verifier,
                "judgeToken",
                "test-judge-token");

        assertDoesNotThrow(
                () -> verifier.requireValidToken("test-judge-token"));
        assertThrows(
                ResponseStatusException.class,
                () -> verifier.requireValidToken("wrong-token"));
        assertThrows(
                ResponseStatusException.class,
                () -> verifier.requireValidToken(null));
    }

    @Test
    void rejectsEverythingWhenTokenIsNotConfigured() {
        JudgeAccessVerifier verifier = new JudgeAccessVerifier();
        ReflectionTestUtils.setField(
                verifier,
                "judgeToken",
                JudgeAccessVerifier.UNCONFIGURED_TOKEN);

        // 公开的默认占位值不能当作有效凭证
        assertFalse(verifier.isValidToken(JudgeAccessVerifier.UNCONFIGURED_TOKEN));
        assertThrows(
                ResponseStatusException.class,
                () -> verifier.requireValidToken(JudgeAccessVerifier.UNCONFIGURED_TOKEN));

        ReflectionTestUtils.setField(verifier, "judgeToken", "  ");
        assertFalse(verifier.isValidToken("  "));
    }
}
