package top.hcode.hoj.agent.manage;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import top.hcode.hoj.agent.AgentJson;
import top.hcode.hoj.agent.AgentAuditPage;
import top.hcode.hoj.agent.AgentTokenService;
import top.hcode.hoj.shiro.AccountProfile;

import javax.annotation.Resource;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * 管理接口的变更审计：每次实际写入（非 dry_run）记录操作、对象以及变更前后的内容，
 * 在后台「AI 数据接口」页面可查看，便于核对与人工回滚。
 */
@Service
@Slf4j(topic = "hoj")
public class AgentChangeLogService {

    /** 单条记录中 before/after 的最大字符数，超出截断（测试数据等大字段不入日志） */
    static final int MAX_JSON_CHARS = 200_000;
    /** 后台列表中每条 before/after 最多返回的字符数，避免一次加载过大 */
    static final int LIST_JSON_CHARS = 20_000;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private AgentTokenService tokenService;

    public void record(long tokenId, AccountProfile operator, String action, String targetType, String targetId,
                       String summary, Object before, Object after) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO agent_change_log (token_id, operator_uid, operator_username, action, target_type, "
                            + "target_id, summary, before_json, after_json, gmt_create) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    tokenId,
                    operator == null ? null : operator.getUid(),
                    operator == null ? null : truncate(operator.getUsername(), 100),
                    action, targetType, truncate(targetId, 64), truncate(summary, 500),
                    toJson(before), toJson(after), new Timestamp(System.currentTimeMillis()));
        } catch (DataAccessException e) {
            log.error("[Agent API] Failed to write change log: action={}, target={}:{}. "
                    + "Run sqlAndsetting/hoj-agent-manage-update.sql if agent_change_log is missing.",
                    action, targetType, targetId, e);
        }
    }

    public List<Map<String, Object>> recent(int limit) {
        try {
            return queryChanges(limit, 0);
        } catch (DataAccessException e) {
            log.warn("[Agent API] agent_change_log is not readable (not migrated yet?)", e);
            return java.util.Collections.emptyList();
        }
    }

    public Map<String, Object> page(int currentPage, int pageSize) {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_change_log", Long.class);
        AgentAuditPage page = new AgentAuditPage(currentPage, pageSize, total == null ? 0 : total);
        return page.result(queryChanges(page.getPageSize(), page.getOffset()));
    }

    private List<Map<String, Object>> queryChanges(int limit, long offset) {
        return tokenService.toInstants(jdbcTemplate.queryForList(
                    "SELECT c.id, c.token_id AS tokenId, t.token_name AS tokenName, "
                            + "c.operator_username AS operatorUsername, c.action, c.target_type AS targetType, "
                            + "c.target_id AS targetId, c.summary, LEFT(c.before_json, ?) AS beforeJson, "
                            + "LEFT(c.after_json, ?) AS afterJson, "
                            + "(CHAR_LENGTH(c.before_json) > ? OR CHAR_LENGTH(c.after_json) > ?) AS truncated, "
                            + "c.gmt_create AS gmtCreate "
                            + "FROM agent_change_log c LEFT JOIN agent_access_token t ON t.id = c.token_id "
                            + "ORDER BY c.id DESC LIMIT ? OFFSET ?",
                    LIST_JSON_CHARS, LIST_JSON_CHARS, LIST_JSON_CHARS, LIST_JSON_CHARS, limit, offset));
    }

    static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return truncate(AgentJson.MAPPER.writeValueAsString(value), MAX_JSON_CHARS);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
