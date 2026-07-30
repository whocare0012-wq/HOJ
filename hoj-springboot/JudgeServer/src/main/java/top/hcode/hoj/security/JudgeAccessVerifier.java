package top.hcode.hoj.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@RefreshScope
public class JudgeAccessVerifier {

    @Value("${hoj.judge.token:no_judge_token}")
    private String judgeToken;

    public void requireValidToken(String suppliedToken) {
        byte[] expected = judgeToken == null
                ? new byte[0]
                : judgeToken.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = suppliedToken == null
                ? new byte[0]
                : suppliedToken.getBytes(StandardCharsets.UTF_8);
        if (expected.length == 0
                || !MessageDigest.isEqual(expected, supplied)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Judge service credential is invalid");
        }
    }
}
