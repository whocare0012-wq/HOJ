package top.hcode.hoj.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
}
