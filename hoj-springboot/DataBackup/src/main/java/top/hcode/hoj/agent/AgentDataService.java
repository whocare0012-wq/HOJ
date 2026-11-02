package top.hcode.hoj.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AI 数据接口的查询实现。
 * <p>
 * 分页按 submit_id 做游标（keyset），新提交不断写入时也不会错位，且始终走主键。
 * 带时间范围时先用 idx_judge_submit_time 求出该范围内 submit_id 的最小/最大值，
 * 再在主键区间内过滤，避免大表上按时间排序或全表扫描。
 */
@Service
public class AgentDataService {

    public static final int STREAM_BATCH_SIZE = 500;
    private static final int ERROR_MESSAGE_MAX_LENGTH = 4000;

    private static final String FROM_CLAUSE = " FROM judge j"
            + " LEFT JOIN user_info u ON u.uuid = j.uid"
            + " LEFT JOIN problem p ON p.id = j.pid"
            + " LEFT JOIN contest c ON c.id = j.cid AND j.cid <> 0";

    private static final String SUBMISSION_COLUMNS = "SELECT j.submit_id, j.submit_time, j.uid, j.username,"
            + " u.realname, u.number, u.nickname, j.pid, p.problem_id, p.title AS problem_title,"
            + " j.display_pid, j.cid, c.title AS contest_title, j.gid, j.language, j.status,"
            + " j.score, j.time, j.memory, j.length";

    @Resource
    private NamedParameterJdbcTemplate namedJdbc;

    @Value("${hoj.agent-api.zone-id:Asia/Shanghai}")
    private String zoneId;

    public ZoneId zone() {
        return ZoneId.of(zoneId);
    }

    // ------------------------------------------------------------------ submissions

    public SubmissionPage listSubmissions(AgentSubmissionFilter filter, boolean includeCode,
                                          int limit, Long cursor, boolean desc) {
        long[] bounds = idBounds(filter);
        if (bounds == EMPTY_RANGE) {
            return new SubmissionPage(new ArrayList<>(), false, null);
        }
        List<Map<String, Object>> rows = querySubmissions(filter, includeCode, limit + 1, cursor, desc, bounds);
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, limit));
        }
        Long nextCursor = hasMore ? (Long) rows.get(rows.size() - 1).get("submit_id") : null;
        return new SubmissionPage(rows, hasMore, nextCursor);
    }

    /**
     * 逐批读取并交给 sink，用于 NDJSON 全量导出；返回写出的条数。maxRows 为 null 表示不限。
     */
    public int streamSubmissions(AgentSubmissionFilter filter, boolean includeCode, Long cursor,
                                 boolean desc, Integer maxRows, Consumer<Map<String, Object>> sink) {
        long[] bounds = idBounds(filter);
        if (bounds == EMPTY_RANGE) {
            return 0;
        }
        int written = 0;
        Long position = cursor;
        while (maxRows == null || written < maxRows) {
            int batch = maxRows == null ? STREAM_BATCH_SIZE : Math.min(STREAM_BATCH_SIZE, maxRows - written);
            List<Map<String, Object>> rows = querySubmissions(filter, includeCode, batch, position, desc, bounds);
            for (Map<String, Object> row : rows) {
                sink.accept(row);
            }
            written += rows.size();
            if (rows.size() < batch) {
                break;
            }
            position = (Long) rows.get(rows.size() - 1).get("submit_id");
        }
        return written;
    }

    public Map<String, Object> getSubmission(long submitId) {
        StringBuilder sql = new StringBuilder(SUBMISSION_COLUMNS).append(", j.code, j.error_message")
                .append(FROM_CLAUSE).append(" WHERE j.submit_id = :submitId");
        MapSqlParameterSource args = new MapSqlParameterSource("submitId", submitId);
        // 复用公共条件，保证进行中比赛的提交同样不可通过单条接口取得
        AgentSubmissionFilter.parse(new AgentParams(null, AgentSubmissionFilter.PARAMS), zone())
                .appendConditions(sql, args, now());
        List<Map<String, Object>> rows = namedJdbc.query(sql.toString(), args, submissionMapper(true));
        if (rows.isEmpty()) {
            throw AgentApiException.notFound("提交 " + submitId + " 不存在，或属于尚未结束的比赛");
        }
        return rows.get(0);
    }

    private List<Map<String, Object>> querySubmissions(AgentSubmissionFilter filter, boolean includeCode,
                                                       int limit, Long cursor, boolean desc, long[] bounds) {
        StringBuilder sql = new StringBuilder(SUBMISSION_COLUMNS);
        if (includeCode) {
            sql.append(", j.code, j.error_message");
        }
        sql.append(FROM_CLAUSE).append(" WHERE 1 = 1");
        MapSqlParameterSource args = new MapSqlParameterSource();
        filter.appendConditions(sql, args, now());
        appendIdBounds(sql, args, bounds);
        if (cursor != null) {
            sql.append(desc ? " AND j.submit_id < :cursor" : " AND j.submit_id > :cursor");
            args.addValue("cursor", cursor);
        }
        sql.append(" ORDER BY j.submit_id ").append(desc ? "DESC" : "ASC").append(" LIMIT :limit");
        args.addValue("limit", limit);
        return namedJdbc.query(sql.toString(), args, submissionMapper(includeCode));
    }

    private RowMapper<Map<String, Object>> submissionMapper(boolean includeCode) {
        ZoneId zone = zone();
        return (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("submit_id", rs.getLong("submit_id"));
            row.put("submitted_at", formatTime(rs.getTimestamp("submit_time"), zone));

            Map<String, Object> student = new LinkedHashMap<>();
            student.put("uid", rs.getString("uid"));
            student.put("username", rs.getString("username"));
            student.put("realname", rs.getString("realname"));
            student.put("number", rs.getString("number"));
            student.put("nickname", rs.getString("nickname"));
            row.put("student", student);

            Map<String, Object> problem = new LinkedHashMap<>();
            problem.put("id", longOrNull(rs, "pid"));
            problem.put("problem_id", rs.getString("problem_id"));
            problem.put("title", rs.getString("problem_title"));
            row.put("problem", problem);

            long cid = rs.getLong("cid");
            if (cid > 0) {
                Map<String, Object> contest = new LinkedHashMap<>();
                contest.put("id", cid);
                contest.put("title", rs.getString("contest_title"));
                contest.put("problem_display_id", rs.getString("display_pid"));
                row.put("contest", contest);
            } else {
                row.put("contest", null);
            }
            row.put("group_id", longOrNull(rs, "gid"));
            row.put("language", rs.getString("language"));
            Integer status = intOrNull(rs, "status");
            row.put("status", AgentSubmissionFilter.statusName(status));
            row.put("status_code", status);
            row.put("score", intOrNull(rs, "score"));
            row.put("time_ms", intOrNull(rs, "time"));
            row.put("memory_kb", intOrNull(rs, "memory"));
            row.put("code_length", intOrNull(rs, "length"));
            if (includeCode) {
                row.put("code", rs.getString("code"));
                String error = rs.getString("error_message");
                row.put("error_message", error != null && error.length() > ERROR_MESSAGE_MAX_LENGTH
                        ? error.substring(0, ERROR_MESSAGE_MAX_LENGTH) + "…(truncated)" : error);
            }
            return row;
        };
    }

    // ------------------------------------------------------------------ students

    public Map<String, Object> listStudents(String keyword, Long groupId, int limit, String cursor) {
        StringBuilder sql = new StringBuilder(
                "SELECT u.uuid, u.username, u.realname, u.number, u.nickname, u.school, u.course, u.status"
                        + " FROM user_info u WHERE 1 = 1");
        MapSqlParameterSource args = new MapSqlParameterSource();
        if (keyword != null) {
            sql.append(" AND (u.username LIKE :kw OR u.realname LIKE :kw OR u.number LIKE :kw"
                    + " OR u.nickname LIKE :kw)");
            args.addValue("kw", "%" + escapeLike(keyword) + "%");
        }
        if (groupId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM group_member gm WHERE gm.uid = u.uuid"
                    + " AND gm.gid = :groupId AND gm.auth IN (3, 4, 5))");
            args.addValue("groupId", groupId);
        }
        if (cursor != null) {
            sql.append(" AND u.uuid > :cursor");
            args.addValue("cursor", cursor);
        }
        sql.append(" ORDER BY u.uuid LIMIT :limit");
        args.addValue("limit", limit + 1);
        List<Map<String, Object>> rows = namedJdbc.query(sql.toString(), args, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uid", rs.getString("uuid"));
            row.put("username", rs.getString("username"));
            row.put("realname", rs.getString("realname"));
            row.put("number", rs.getString("number"));
            row.put("nickname", rs.getString("nickname"));
            row.put("school", rs.getString("school"));
            row.put("course", rs.getString("course"));
            row.put("enabled", rs.getInt("status") == 0);
            return row;
        });
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, limit));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", rows);
        result.put("has_more", hasMore);
        result.put("next_cursor", hasMore ? rows.get(rows.size() - 1).get("uid") : null);
        return result;
    }

    // ------------------------------------------------------------------ stats

    public Map<String, Object> stats(AgentSubmissionFilter filter, String groupBy, int limit) {
        ZoneId zone = zone();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("group_by", groupBy);
        long[] bounds = idBounds(filter);

        String select;
        String group;
        String order;
        switch (groupBy) {
            case "user":
                select = "SELECT j.uid AS uid, MAX(j.username) AS username, MAX(u.realname) AS realname,"
                        + " MAX(u.number) AS number, COUNT(*) AS submissions, " + ACCEPTED_SUM
                        + ", COUNT(DISTINCT j.pid) AS attempted_problems,"
                        + " COUNT(DISTINCT CASE WHEN j.status = 0 THEN j.pid END) AS solved_problems,"
                        + " MIN(j.submit_time) AS first_submit_at, MAX(j.submit_time) AS last_submit_at";
                group = " GROUP BY j.uid";
                order = " ORDER BY submissions DESC, uid";
                break;
            case "problem":
                select = "SELECT j.pid AS id, MAX(p.problem_id) AS problem_id, MAX(p.title) AS title,"
                        + " COUNT(*) AS submissions, " + ACCEPTED_SUM
                        + ", COUNT(DISTINCT j.uid) AS users_attempted,"
                        + " COUNT(DISTINCT CASE WHEN j.status = 0 THEN j.uid END) AS users_solved";
                group = " GROUP BY j.pid";
                order = " ORDER BY submissions DESC, id";
                break;
            case "day":
                select = "SELECT DATE(j.submit_time) AS day, COUNT(*) AS submissions, " + ACCEPTED_SUM
                        + ", COUNT(DISTINCT j.uid) AS active_users";
                group = " GROUP BY DATE(j.submit_time)";
                order = " ORDER BY day";
                break;
            case "status":
                select = "SELECT j.status AS status_code, COUNT(*) AS submissions";
                group = " GROUP BY j.status";
                order = " ORDER BY submissions DESC";
                break;
            case "language":
                select = "SELECT j.language AS language, COUNT(*) AS submissions, " + ACCEPTED_SUM
                        + ", COUNT(DISTINCT j.uid) AS users";
                group = " GROUP BY j.language";
                order = " ORDER BY submissions DESC";
                break;
            default:
                throw AgentApiException.badRequest("group_by 只能是 user、problem、day、status、language");
        }

        if (bounds == EMPTY_RANGE) {
            result.put("totals", emptyTotals());
            result.put("rows", new ArrayList<>());
            result.put("truncated", false);
            return result;
        }

        MapSqlParameterSource totalArgs = new MapSqlParameterSource();
        StringBuilder totalSql = new StringBuilder("SELECT COUNT(*) AS submissions, ").append(ACCEPTED_SUM)
                .append(", COUNT(DISTINCT j.uid) AS users, COUNT(DISTINCT j.pid) AS problems,")
                .append(" MIN(j.submit_time) AS first_submit_at, MAX(j.submit_time) AS last_submit_at")
                .append(FROM_CLAUSE).append(" WHERE 1 = 1");
        filter.appendConditions(totalSql, totalArgs, now());
        appendIdBounds(totalSql, totalArgs, bounds);
        result.put("totals", normalize(namedJdbc.queryForMap(totalSql.toString(), totalArgs), zone));

        MapSqlParameterSource args = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder(select).append(FROM_CLAUSE).append(" WHERE 1 = 1");
        filter.appendConditions(sql, args, now());
        appendIdBounds(sql, args, bounds);
        sql.append(group).append(order).append(" LIMIT :limit");
        args.addValue("limit", limit + 1);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : namedJdbc.queryForList(sql.toString(), args)) {
            Map<String, Object> normalized = normalize(row, zone);
            if ("status".equals(groupBy)) {
                Integer code = normalized.get("status_code") == null ? null
                        : ((Number) normalized.get("status_code")).intValue();
                Map<String, Object> withName = new LinkedHashMap<>();
                withName.put("status", AgentSubmissionFilter.statusName(code));
                withName.putAll(normalized);
                normalized = withName;
            }
            rows.add(normalized);
        }
        boolean truncated = rows.size() > limit;
        result.put("rows", truncated ? new ArrayList<>(rows.subList(0, limit)) : rows);
        result.put("truncated", truncated);
        return result;
    }

    private static final String ACCEPTED_SUM = "SUM(CASE WHEN j.status = 0 THEN 1 ELSE 0 END) AS accepted_submissions";

    private static Map<String, Object> emptyTotals() {
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("submissions", 0L);
        totals.put("accepted_submissions", 0L);
        totals.put("users", 0L);
        totals.put("problems", 0L);
        totals.put("first_submit_at", null);
        totals.put("last_submit_at", null);
        return totals;
    }

    // ------------------------------------------------------------------ helpers

    /** 无时间范围时返回 null；范围内没有提交时返回 EMPTY_RANGE */
    private long[] idBounds(AgentSubmissionFilter filter) {
        if (!filter.hasTimeRange()) {
            return null;
        }
        StringBuilder sql = new StringBuilder(
                "SELECT MIN(submit_id) AS lo, MAX(submit_id) AS hi FROM judge WHERE 1 = 1");
        MapSqlParameterSource args = new MapSqlParameterSource();
        if (filter.getFrom() != null) {
            sql.append(" AND submit_time >= :fromTime");
            args.addValue("fromTime", filter.getFrom());
        }
        if (filter.getTo() != null) {
            sql.append(" AND submit_time < :toTime");
            args.addValue("toTime", filter.getTo());
        }
        Map<String, Object> row = namedJdbc.queryForMap(sql.toString(), args);
        if (row.get("lo") == null) {
            return EMPTY_RANGE;
        }
        return new long[]{((Number) row.get("lo")).longValue(), ((Number) row.get("hi")).longValue()};
    }

    private static final long[] EMPTY_RANGE = new long[0];

    private static void appendIdBounds(StringBuilder sql, MapSqlParameterSource args, long[] bounds) {
        if (bounds != null && bounds.length == 2) {
            sql.append(" AND j.submit_id BETWEEN :idLo AND :idHi");
            args.addValue("idLo", bounds[0]);
            args.addValue("idHi", bounds[1]);
        }
    }

    static Map<String, Object> normalize(Map<String, Object> row, ZoneId zone) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            Object value = entry.getValue();
            // Connector/J 8 的 getObject 对 DATETIME 返回 LocalDateTime、对 DATE 返回 LocalDate，
            // 旧驱动或其它配置下则是 Timestamp / java.sql.Date，统一转成带时区的字符串
            if (value instanceof Timestamp) {
                value = formatTime((Timestamp) value, zone);
            } else if (value instanceof LocalDateTime) {
                // DATETIME 存的是站点时区的墙上时间，直接按站点时区解释，不受 JVM 默认时区影响
                value = ((LocalDateTime) value).atZone(zone).toOffsetDateTime()
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            } else if (value instanceof java.sql.Date || value instanceof LocalDate) {
                value = value.toString();
            } else if (value instanceof BigDecimal || value instanceof BigInteger) {
                value = ((Number) value).longValue();
            }
            result.put(entry.getKey(), value);
        }
        if (result.containsKey("accepted_submissions") && result.get("accepted_submissions") == null) {
            result.put("accepted_submissions", 0L);
        }
        return result;
    }

    static String formatTime(Timestamp timestamp, ZoneId zone) {
        return timestamp == null ? null
                : timestamp.toInstant().atZone(zone).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }

    private static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer intOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    public static final class SubmissionPage {
        private final List<Map<String, Object>> rows;
        private final boolean hasMore;
        private final Long nextCursor;

        SubmissionPage(List<Map<String, Object>> rows, boolean hasMore, Long nextCursor) {
            this.rows = rows;
            this.hasMore = hasMore;
            this.nextCursor = nextCursor;
        }

        public List<Map<String, Object>> getRows() {
            return rows;
        }

        public boolean isHasMore() {
            return hasMore;
        }

        public Long getNextCursor() {
            return nextCursor;
        }
    }
}
