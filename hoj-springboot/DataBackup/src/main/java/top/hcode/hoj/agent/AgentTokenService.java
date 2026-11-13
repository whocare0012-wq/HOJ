package top.hcode.hoj.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import top.hcode.hoj.shiro.AccountProfile;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 数据接口访问令牌：只能由 root 生成；数据库只保存 SHA-256 摘要，明文只在生成时返回一次。
 * 每次请求都会确认令牌创建者仍是启用状态的 root 账号，root 权限被收回后其令牌随即失效。
 */
@Service
@Slf4j(topic = "hoj")
public class AgentTokenService {

    public static final String TOKEN_PREFIX = "hoj_at_";
    /** role 表中超级管理员（root）的角色 ID */
    static final long ROOT_ROLE_ID = 1000L;
    private static final int MAX_NAME_LENGTH = 80;
    private static final int MAX_EXPIRE_DAYS = 3650;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Value("${hoj.agent-api.zone-id:Asia/Shanghai}")
    private String zoneId = "Asia/Shanghai";

    /** 是否已执行 hoj-agent-manage-update.sql（agent_access_token.scopes 字段）；未执行时全部令牌按只读处理 */
    private volatile boolean scopesColumnPresent;
    private volatile long scopesColumnCheckedAt;

    public static String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 从 Authorization 头中取出令牌；格式不对时返回 null */
    public static String extractToken(String authorization) {
        if (authorization == null) {
            return null;
        }
        String value = authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            value = value.substring(7).trim();
        }
        return value.startsWith(TOKEN_PREFIX) && value.length() > TOKEN_PREFIX.length() ? value : null;
    }

    public Map<String, Object> create(String name, Integer expiresInDays, String creatorUid) {
        return create(name, expiresInDays, creatorUid, Collections.singletonList(AgentScope.READ));
    }

    public Map<String, Object> create(String name, Integer expiresInDays, String creatorUid,
                                      Collection<String> scopeList) {
        String tokenName = name == null ? "" : name.trim();
        if (tokenName.isEmpty() || tokenName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("令牌名称不能为空，且不超过 " + MAX_NAME_LENGTH + " 个字符");
        }
        if (expiresInDays != null && (expiresInDays < 1 || expiresInDays > MAX_EXPIRE_DAYS)) {
            throw new IllegalArgumentException("有效期需为 1 ~ " + MAX_EXPIRE_DAYS + " 天，或选择永不过期");
        }
        String scopes = AgentScope.normalize(scopeList);
        boolean withScopes = hasScopesColumn();
        if (!withScopes && !AgentScope.READ.equals(scopes)) {
            throw new IllegalArgumentException("数据库尚未升级，无法生成带管理权限的令牌："
                    + "请先执行 sqlAndsetting/hoj-agent-manage-update.sql");
        }
        String rawToken = generateRawToken();
        String displayPrefix = rawToken.substring(0, TOKEN_PREFIX.length() + 6);
        Timestamp expiresAt = expiresInDays == null ? null
                : new Timestamp(System.currentTimeMillis() + expiresInDays * 86_400_000L);

        Timestamp now = now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO agent_access_token "
                            + "(token_name, token_prefix, token_hash, expires_at, created_by, gmt_create"
                            + (withScopes ? ", scopes) VALUES (?, ?, ?, ?, ?, ?, ?)" : ") VALUES (?, ?, ?, ?, ?, ?)"),
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, tokenName);
            statement.setString(2, displayPrefix);
            statement.setString(3, hash(rawToken));
            statement.setTimestamp(4, expiresAt);
            statement.setString(5, creatorUid);
            statement.setTimestamp(6, now);
            if (withScopes) {
                statement.setString(7, scopes);
            }
            return statement;
        }, keyHolder);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", keyHolder.getKey() == null ? null : keyHolder.getKey().longValue());
        result.put("tokenName", tokenName);
        result.put("tokenPrefix", displayPrefix);
        result.put("expiresAt", expiresAt);
        result.put("scopes", scopes);
        // 明文只在此处返回一次
        result.put("token", rawToken);
        return result;
    }

    public List<Map<String, Object>> list() {
        return toInstants(jdbcTemplate.queryForList(
                "SELECT t.id, t.token_name AS tokenName, t.token_prefix AS tokenPrefix, "
                        + (hasScopesColumn() ? "t.scopes, " : "'read' AS scopes, ")
                        + "t.expires_at AS expiresAt, t.revoked, t.created_by AS createdBy, "
                        + "u.username AS createdByUsername, t.last_used_at AS lastUsedAt, "
                        + "t.last_used_ip AS lastUsedIp, t.gmt_create AS gmtCreate, "
                        + "CASE WHEN t.revoked = 1 THEN 'REVOKED' "
                        + "WHEN t.expires_at IS NOT NULL AND t.expires_at <= ? THEN 'EXPIRED' "
                        + "ELSE 'ACTIVE' END AS status "
                        + "FROM agent_access_token t LEFT JOIN user_info u ON u.uuid = t.created_by "
                        + "ORDER BY t.revoked ASC, t.id DESC",
                now()));
    }

    public boolean revoke(Long id) {
        return id != null && jdbcTemplate.update(
                "UPDATE agent_access_token SET revoked = 1 WHERE id = ? AND revoked = 0", id) > 0;
    }

    public List<Map<String, Object>> recentLogs(int limit) {
        return toInstants(jdbcTemplate.queryForList(
                "SELECT l.id, l.token_id AS tokenId, t.token_name AS tokenName, l.endpoint, "
                        + "l.query_string AS queryString, l.result_count AS resultCount, "
                        + "l.status_code AS statusCode, l.ip, l.gmt_create AS gmtCreate "
                        + "FROM agent_access_log l LEFT JOIN agent_access_token t ON t.id = l.token_id "
                        + "ORDER BY l.id DESC LIMIT ?",
                limit));
    }

    /**
     * Connector/J 8 把 DATETIME 读成不带时区的 LocalDateTime，序列化后前端 utcToLocal 会误当成 UTC。
     * 这里按站点时区换算成确定的时间点（java.util.Date），与实体类日期字段的序列化方式一致。
     */
    public List<Map<String, Object>> toInstants(List<Map<String, Object>> rows) {
        ZoneId zone = ZoneId.of(zoneId);
        for (Map<String, Object> row : rows) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof LocalDateTime) {
                    entry.setValue(Date.from(((LocalDateTime) value).atZone(zone).toInstant()));
                } else if (value instanceof Timestamp) {
                    entry.setValue(new Date(((Timestamp) value).getTime()));
                }
            }
        }
        return rows;
    }

    /**
     * 校验令牌，成功时返回令牌信息；失败时抛出带 401/403 的异常。
     */
    public AgentPrincipal authenticate(String authorization) {
        String rawToken = extractToken(authorization);
        if (rawToken == null) {
            throw new AgentApiException(401, "missing_token",
                    "请在请求头中携带 Authorization: Bearer hoj_at_...（在后台「AI 数据接口」页面由 root 生成）");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT t.id, t.token_name AS tokenName, t.revoked, t.created_by AS createdBy, "
                        + (hasScopesColumn() ? "t.scopes, " : "")
                        + "(t.expires_at IS NOT NULL AND t.expires_at <= ?) AS expired, "
                        + "EXISTS(SELECT 1 FROM user_role ur JOIN user_info u ON u.uuid = ur.uid "
                        + "WHERE ur.uid = t.created_by AND ur.role_id = ? AND u.status = 0) AS creatorIsRoot "
                        + "FROM agent_access_token t WHERE t.token_hash = ?",
                now(), ROOT_ROLE_ID, hash(rawToken));
        if (rows.isEmpty()) {
            throw new AgentApiException(401, "invalid_token", "令牌无效");
        }
        Map<String, Object> row = rows.get(0);
        if (isTrue(row.get("revoked"))) {
            throw new AgentApiException(401, "token_revoked", "令牌已被吊销");
        }
        if (isTrue(row.get("expired"))) {
            throw new AgentApiException(401, "token_expired", "令牌已过期，请让 root 重新生成");
        }
        if (!isTrue(row.get("creatorIsRoot"))) {
            throw new AgentApiException(403, "creator_not_root", "令牌创建者已不是启用的 root 账号，令牌不可用");
        }
        Object createdBy = row.get("createdBy");
        return new AgentPrincipal(((Number) row.get("id")).longValue(), String.valueOf(row.get("tokenName")),
                AgentScope.parse((String) row.get("scopes")), createdBy == null ? null : String.valueOf(createdBy));
    }

    /** 读取令牌创建者的账号信息，管理接口以该身份执行后台操作（权限校验、修改人记录） */
    public AccountProfile loadCreatorProfile(String uid) {
        List<Map<String, Object>> rows = uid == null ? Collections.emptyList() : jdbcTemplate.queryForList(
                "SELECT uuid, username, nickname, realname, title_name AS titleName, "
                        + "title_color AS titleColor, avatar, status FROM user_info WHERE uuid = ?", uid);
        if (rows.isEmpty()) {
            throw new AgentApiException(403, "creator_not_root", "令牌创建者账号不存在，令牌不可用");
        }
        Map<String, Object> row = rows.get(0);
        AccountProfile profile = new AccountProfile();
        profile.setUid(String.valueOf(row.get("uuid")));
        profile.setUsername(asString(row.get("username")));
        profile.setNickname(asString(row.get("nickname")));
        profile.setRealname(asString(row.get("realname")));
        profile.setTitleName(asString(row.get("titleName")));
        profile.setTitleColor(asString(row.get("titleColor")));
        profile.setAvatar(asString(row.get("avatar")));
        Object status = row.get("status");
        profile.setStatus(status instanceof Number ? ((Number) status).intValue() : 0);
        return profile;
    }

    /**
     * 检测 scopes 字段是否存在。存在后结果永久缓存；不存在时每分钟最多重新检测一次，
     * 执行升级 SQL 后无需重启即可生效。
     */
    boolean hasScopesColumn() {
        if (scopesColumnPresent) {
            return true;
        }
        long nowMillis = System.currentTimeMillis();
        if (scopesColumnCheckedAt != 0 && nowMillis - scopesColumnCheckedAt < 60_000L) {
            return false;
        }
        scopesColumnCheckedAt = nowMillis;
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                            + "AND table_name = 'agent_access_token' AND column_name = 'scopes'",
                    Integer.class);
            scopesColumnPresent = count != null && count > 0;
        } catch (DataAccessException e) {
            log.warn("[Agent API] Failed to detect agent_access_token.scopes column", e);
        }
        if (!scopesColumnPresent) {
            log.warn("[Agent API] agent_access_token.scopes is missing; all tokens are read-only. "
                    + "Run sqlAndsetting/hoj-agent-manage-update.sql to enable the management API.");
        }
        return scopesColumnPresent;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public void touch(long tokenId, String ip) {
        try {
            jdbcTemplate.update(
                    "UPDATE agent_access_token SET last_used_at = ?, last_used_ip = ? WHERE id = ?",
                    now(), truncate(ip, 64), tokenId);
        } catch (DataAccessException e) {
            log.warn("[Agent API] Failed to update token usage, id={}", tokenId, e);
        }
    }

    public void log(long tokenId, String endpoint, String queryString, Integer resultCount, int status, String ip) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO agent_access_log "
                            + "(token_id, endpoint, query_string, result_count, status_code, ip, gmt_create) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    tokenId, truncate(endpoint, 200), truncate(queryString, 2000), resultCount, status,
                    truncate(ip, 64), now());
        } catch (DataAccessException e) {
            log.warn("[Agent API] Failed to write access log, tokenId={}", tokenId, e);
        }
    }

    /**
     * 所有时间都由应用写入/比较（经驱动按连接时区转换），不使用数据库的 NOW()，
     * 避免 MySQL 服务器时区与应用时区不一致时，过期判断与显示时间相差数小时。
     */
    private static Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }

    private static boolean isTrue(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value instanceof Number && ((Number) value).intValue() != 0;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public static final class AgentPrincipal {
        private final long tokenId;
        private final String tokenName;
        private final Set<String> scopes;
        private final String creatorUid;

        public AgentPrincipal(long tokenId, String tokenName) {
            this(tokenId, tokenName, AgentScope.parse(null), null);
        }

        public AgentPrincipal(long tokenId, String tokenName, Set<String> scopes, String creatorUid) {
            this.tokenId = tokenId;
            this.tokenName = tokenName;
            this.scopes = scopes;
            this.creatorUid = creatorUid;
        }

        public Set<String> getScopes() {
            return scopes;
        }

        public boolean hasScope(String scope) {
            return scopes.contains(scope);
        }

        public String getCreatorUid() {
            return creatorUid;
        }

        public long getTokenId() {
            return tokenId;
        }

        public String getTokenName() {
            return tokenName;
        }
    }
}
