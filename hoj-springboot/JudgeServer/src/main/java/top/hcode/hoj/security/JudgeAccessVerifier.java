package top.hcode.hoj.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@RefreshScope
@Slf4j(topic = "hoj")
public class JudgeAccessVerifier {

    /**
     * 未配置 hoj.judge.token 时的占位值。它是公开的，不能当作有效凭证，
     * 否则任何能访问判题机端口的人都可以直接调用判题、编译接口。
     */
    static final String UNCONFIGURED_TOKEN = "no_judge_token";

    @Value("${hoj.judge.token:" + UNCONFIGURED_TOKEN + "}")
    private String judgeToken;

    public boolean isValidToken(String suppliedToken) {
        if (judgeToken == null
                || judgeToken.trim().isEmpty()
                || UNCONFIGURED_TOKEN.equals(judgeToken)) {
            log.warn("[Judge] hoj.judge.token is not configured, all judge requests are rejected");
            return false;
        }
        byte[] expected = judgeToken.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = suppliedToken == null
                ? new byte[0]
                : suppliedToken.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, supplied);
    }

    public void requireValidToken(String suppliedToken) {
        if (!isValidToken(suppliedToken)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Judge service credential is invalid");
        }
    }
}
