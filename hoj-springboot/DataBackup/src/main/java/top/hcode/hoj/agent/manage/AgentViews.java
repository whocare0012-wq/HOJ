package top.hcode.hoj.agent.manage;

import top.hcode.hoj.agent.AgentApiException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 管理接口中与数据库无关的转换：枚举值与可读名称互转、题面样例格式、测试点分数分配、变更 diff。
 */
public final class AgentViews {

    private static final Pattern EXAMPLE_PATTERN =
            Pattern.compile("<input>([\\s\\S]*?)</input><output>([\\s\\S]*?)</output>");

    private AgentViews() {
    }

    public static String time(Date date, ZoneId zone) {
        return date == null ? null
                : date.toInstant().atZone(zone).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /** 乐观锁版本号：最后修改时间的毫秒值 */
    public static Long version(Date gmtModified) {
        return gmtModified == null ? null : gmtModified.getTime();
    }

    // ------------------------------------------------------------------ 题目枚举

    public static String problemType(Integer type) {
        if (type == null) {
            return null;
        }
        return type == 1 ? "OI" : "ACM";
    }

    public static Integer problemTypeValue(String name) {
        if (name == null) {
            return null;
        }
        switch (name.trim().toUpperCase()) {
            case "ACM":
                return 0;
            case "OI":
                return 1;
            default:
                throw AgentApiException.badRequest("type 只能是 ACM 或 OI");
        }
    }

    public static String problemAuth(Integer auth) {
        if (auth == null) {
            return null;
        }
        switch (auth) {
            case 1:
                return "public";
            case 2:
                return "private";
            case 3:
                return "contest";
            default:
                return String.valueOf(auth);
        }
    }

    /** 接口只允许设为 public / private；比赛题目请在后台比赛管理中维护 */
    public static Integer problemAuthValue(String name) {
        if (name == null) {
            return null;
        }
        switch (name.trim().toLowerCase()) {
            case "public":
                return 1;
            case "private":
                return 2;
            default:
                throw AgentApiException.badRequest("auth 只能是 public（公开）或 private（隐藏）");
        }
    }

    // ------------------------------------------------------------------ 样例

    public static List<Map<String, String>> parseExamples(String value) {
        List<Map<String, String>> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        Matcher matcher = EXAMPLE_PATTERN.matcher(value);
        while (matcher.find()) {
            Map<String, String> example = new LinkedHashMap<>();
            example.put("input", matcher.group(1));
            example.put("output", matcher.group(2));
            result.add(example);
        }
        return result;
    }

    public static String formatExamples(List<Map<String, String>> examples) {
        if (examples == null || examples.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (Map<String, String> example : examples) {
            String input = example.get("input") == null ? "" : example.get("input");
            String output = example.get("output") == null ? "" : example.get("output");
            if (input.contains("</input>") || output.contains("</output>")) {
                throw AgentApiException.badRequest("样例内容不能包含 </input> 或 </output>");
            }
            result.append("<input>").append(input).append("</input><output>").append(output).append("</output>");
        }
        return result.toString();
    }

    // ------------------------------------------------------------------ 测试点分数

    /**
     * 与后台页面上传测试数据时的规则一致：总分 100 平均分给各测试点，除不尽的余数从最后一个测试点起各加 1。
     */
    public static int[] distributeScores(int count) {
        int[] scores = new int[count];
        if (count == 0) {
            return scores;
        }
        int average = 100 / count;
        int remainder = 100 - average * count;
        for (int i = 0; i < count; i++) {
            scores[i] = average + (i >= count - remainder ? 1 : 0);
        }
        return scores;
    }

    // ------------------------------------------------------------------ diff

    /** 逐字段对比，返回 {字段: {before, after}}，只包含有变化的字段 */
    public static Map<String, Object> diff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : after.entrySet()) {
            String key = entry.getKey();
            Object oldValue = before == null ? null : before.get(key);
            if (!Objects.equals(oldValue, entry.getValue())) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("before", oldValue);
                change.put("after", entry.getValue());
                changes.put(key, change);
            }
        }
        return changes;
    }
}
