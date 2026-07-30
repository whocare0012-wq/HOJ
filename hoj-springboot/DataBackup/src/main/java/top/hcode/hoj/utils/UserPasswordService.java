package top.hcode.hoj.utils;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.hcode.hoj.dao.user.UserInfoEntityService;
import top.hcode.hoj.pojo.entity.user.UserInfo;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Password codec that keeps legacy MD5 records readable while BCrypt is rolled out gradually.
 *
 * Both rollout switches default to false so a newly deployed version continues to write MD5
 * hashes until every application instance (and the rollback version) can read BCrypt hashes.
 */
@Component
@Slf4j(topic = "hoj")
public class UserPasswordService {

    private static final String BCRYPT_PREFIX = "$2";

    @Value("${password-bcrypt-write-enabled:false}")
    private boolean bcryptWriteEnabled;

    @Value("${password-upgrade-on-login-enabled:false}")
    private boolean upgradeOnLoginEnabled;

    @Resource
    private UserInfoEntityService userInfoEntityService;

    public String encode(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("Password must not be null");
        }
        return bcryptWriteEnabled ? BCrypt.hashpw(rawPassword) : SecureUtil.md5(rawPassword);
    }

    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (isBcrypt(storedPassword)) {
            try {
                return BCrypt.checkpw(rawPassword, storedPassword);
            } catch (RuntimeException exception) {
                log.warn("Rejected an invalid BCrypt password hash");
                return false;
            }
        }

        byte[] calculated = SecureUtil.md5(rawPassword).getBytes(StandardCharsets.UTF_8);
        byte[] stored = storedPassword.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(calculated, stored);
    }

    /**
     * Upgrades a legacy hash using a compare-and-set update. Migration failures never block a
     * successful login and the stored password is not exposed in logs.
     */
    public void upgradeAfterSuccessfulLogin(String uid, String rawPassword, String storedPassword) {
        if (!bcryptWriteEnabled || !upgradeOnLoginEnabled || uid == null
                || rawPassword == null || storedPassword == null || isBcrypt(storedPassword)) {
            return;
        }

        try {
            UpdateWrapper<UserInfo> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("uuid", uid)
                    .eq("password", storedPassword)
                    .set("password", BCrypt.hashpw(rawPassword));
            userInfoEntityService.update(updateWrapper);
        } catch (RuntimeException exception) {
            log.warn("Unable to upgrade the password hash for uid={}", uid);
        }
    }

    private boolean isBcrypt(String password) {
        return password.startsWith(BCRYPT_PREFIX);
    }
}
