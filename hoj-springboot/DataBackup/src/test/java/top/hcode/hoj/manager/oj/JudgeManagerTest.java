package top.hcode.hoj.manager.oj;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JudgeManagerTest {

    @Test
    void limitsOnlineTestJudgeToOneSecondWhenSubmissionRateLimitIsEnabled() {
        assertEquals(1, JudgeManager.resolveTestJudgeInterval(8));
    }

    @Test
    void keepsOnlineTestJudgeRateLimitDisabledWhenSubmissionRateLimitIsDisabled() {
        assertEquals(0, JudgeManager.resolveTestJudgeInterval(0));
        assertEquals(0, JudgeManager.resolveTestJudgeInterval(null));
    }
}
