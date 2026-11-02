package top.hcode.hoj.agent;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import top.hcode.hoj.utils.Constants;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 提交记录的公共筛选条件（submissions 与 stats 共用）。
 * <p>
 * 生成的 SQL 片段约定表别名：j=judge，u=user_info，p=problem，c=contest；所有取值都以命名参数绑定。
 */
public final class AgentSubmissionFilter {

    /** 所有使用该筛选的接口都接受的参数 */
    public static final Set<String> PARAMS = AgentParams.names(
            "username", "uid", "number", "group_id", "problem", "contest_id",
            "status", "language", "from", "to", "date");

    private static final Map<String, Integer> STATUS_ALIASES = buildStatusAliases();

    private final List<String> usernames;
    private final List<String> uids;
    private final List<String> numbers;
    private final Long groupId;
    private final List<String> problems;
    private final Long contestId;
    private final List<Integer> statuses;
    private final List<String> languages;
    private final Timestamp from;
    private final Timestamp to;

    private AgentSubmissionFilter(AgentParams params, ZoneId zone) {
        this.usernames = params.list("username");
        this.uids = params.list("uid");
        this.numbers = params.list("number");
        this.groupId = params.longValue("group_id");
        this.problems = params.list("problem");
        this.contestId = params.longValue("contest_id");
        this.statuses = parseStatuses(params.list("status"));
        this.languages = params.list("language");

        String date = params.single("date");
        String fromValue = params.single("from");
        String toValue = params.single("to");
        if (date != null) {
            if (fromValue != null || toValue != null) {
                throw AgentApiException.badRequest("date 不能与 from/to 同时使用");
            }
            LocalDate day = parseDate("date", date);
            this.from = Timestamp.from(day.atStartOfDay(zone).toInstant());
            this.to = Timestamp.from(day.plusDays(1).atStartOfDay(zone).toInstant());
        } else {
            this.from = fromValue == null ? null : parseBound("from", fromValue, zone, false);
            this.to = toValue == null ? null : parseBound("to", toValue, zone, true);
        }
        if (from != null && to != null && !from.before(to)) {
            throw AgentApiException.badRequest("from 必须早于 to");
        }
    }

    public static AgentSubmissionFilter parse(AgentParams params, ZoneId zone) {
        return new AgentSubmissionFilter(params, zone);
    }

    public Timestamp getFrom() {
        return from;
    }

    public Timestamp getTo() {
        return to;
    }

    public boolean hasTimeRange() {
        return from != null || to != null;
    }

    /**
     * 追加 WHERE 条件（以 AND 开头）。正在进行或尚未结束的比赛中的提交一律排除，
     * 避免比赛期间代码外泄；比赛记录缺失时同样按未结束处理。
     */
    public void appendConditions(StringBuilder sql, MapSqlParameterSource args, Timestamp now) {
        sql.append(" AND (j.cid = 0 OR (c.end_time IS NOT NULL AND c.end_time <= :now))");
        args.addValue("now", now);
        appendIn(sql, args, "j.username", "usernames", usernames);
        appendIn(sql, args, "j.uid", "uids", uids);
        appendIn(sql, args, "u.number", "numbers", numbers);
        // 题号按题库中的展示 ID 匹配（比赛内提交的 display_pid 可能是 A、B 这类比赛内编号）
        appendIn(sql, args, "p.problem_id", "problems", problems);
        appendIn(sql, args, "j.status", "statuses", statuses);
        appendIn(sql, args, "j.language", "languages", languages);
        if (groupId != null) {
            // 团队成员（普通成员、管理员、拥有者）的全部提交，不限于团队内题目
            sql.append(" AND j.uid IN (SELECT gm.uid FROM group_member gm"
                    + " WHERE gm.gid = :groupId AND gm.auth IN (3, 4, 5))");
            args.addValue("groupId", groupId);
        }
        if (contestId != null) {
            sql.append(" AND j.cid = :contestId");
            args.addValue("contestId", contestId);
        }
        if (from != null) {
            sql.append(" AND j.submit_time >= :fromTime");
            args.addValue("fromTime", from);
        }
        if (to != null) {
            sql.append(" AND j.submit_time < :toTime");
            args.addValue("toTime", to);
        }
    }

    private static void appendIn(StringBuilder sql, MapSqlParameterSource args,
                                 String column, String name, List<?> values) {
        if (!values.isEmpty()) {
            sql.append(" AND ").append(column).append(" IN (:").append(name).append(")");
            args.addValue(name, values);
        }
    }

    static List<Integer> parseStatuses(List<String> values) {
        if (values.isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> result = new ArrayList<>();
        for (String value : values) {
            Integer status = STATUS_ALIASES.get(normalizeStatus(value));
            if (status == null) {
                try {
                    status = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw AgentApiException.badRequest("无法识别的 status: " + value
                            + "；可用 AC, WA, CE, TLE, MLE, RE, PE, PA, SE, CA 或数字状态码");
                }
            }
            if (!result.contains(status)) {
                result.add(status);
            }
        }
        return result;
    }

    /** 状态码对应的英文名，如 -1 → Wrong Answer */
    public static String statusName(Integer status) {
        if (status == null) {
            return null;
        }
        for (Constants.Judge judge : Constants.Judge.values()) {
            if (judge.getStatus().equals(status) && judge != Constants.Judge.JUDGE_SERVER_SUBMIT_PREFIX) {
                return judge.getName();
            }
        }
        return "Unknown";
    }

    private static Map<String, Integer> buildStatusAliases() {
        Map<String, Integer> aliases = new HashMap<>();
        for (Constants.Judge judge : Constants.Judge.values()) {
            if (judge == Constants.Judge.JUDGE_SERVER_SUBMIT_PREFIX) {
                continue;
            }
            aliases.put(normalizeStatus(judge.getName()), judge.getStatus());
            if (judge.getColumnName() != null) {
                aliases.put(normalizeStatus(judge.getColumnName()), judge.getStatus());
            }
        }
        return aliases;
    }

    private static String normalizeStatus(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]", "");
    }

    private static LocalDate parseDate(String name, String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw AgentApiException.badRequest("参数 " + name + " 需为 YYYY-MM-DD 格式: " + value);
        }
    }

    /**
     * 解析时间边界：纯日期表示当天 0 点（to 为纯日期时包含当天全天）；
     * 不带时区的时间按站点时区解释；带时区（如 +08:00、Z）则按其自身时区。
     */
    static Timestamp parseBound(String name, String value, ZoneId zone, boolean upper) {
        String text = value.replace(' ', 'T');
        try {
            if (text.length() == 10) {
                LocalDate day = LocalDate.parse(text);
                return Timestamp.from((upper ? day.plusDays(1) : day).atStartOfDay(zone).toInstant());
            }
            try {
                return Timestamp.from(OffsetDateTime.parse(text).toInstant());
            } catch (DateTimeParseException ignored) {
                return Timestamp.from(LocalDateTime.parse(text).atZone(zone).toInstant());
            }
        } catch (DateTimeParseException e) {
            throw AgentApiException.badRequest("参数 " + name
                    + " 需为 YYYY-MM-DD、YYYY-MM-DDTHH:mm:ss 或带时区的 ISO 8601 时间: " + value);
        }
    }
}
