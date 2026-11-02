package top.hcode.hoj.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 请求参数读取：同一参数既可重复传，也可用英文逗号分隔；未知参数直接报错，
 * 让 agent 拼错参数名时立刻得到提示，而不是静默返回全部数据。
 */
public final class AgentParams {

    private final Map<String, String[]> raw;

    public AgentParams(Map<String, String[]> raw, Set<String> allowed) {
        this.raw = raw == null ? Collections.emptyMap() : raw;
        Set<String> unknown = new TreeSet<>();
        for (String name : this.raw.keySet()) {
            if (!allowed.contains(name)) {
                unknown.add(name);
            }
        }
        if (!unknown.isEmpty()) {
            throw AgentApiException.badRequest("未知参数: " + String.join(", ", unknown)
                    + "；可用参数: " + String.join(", ", new TreeSet<>(allowed)));
        }
    }

    public static Set<String> names(String... names) {
        return new LinkedHashSet<>(Arrays.asList(names));
    }

    /** 读取多值参数，去掉空白与重复项 */
    public List<String> list(String name) {
        String[] values = raw.get(name);
        if (values == null) {
            return Collections.emptyList();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            for (String part : value.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        if (result.size() > 500) {
            throw AgentApiException.badRequest("参数 " + name + " 最多 500 个值");
        }
        return new ArrayList<>(result);
    }

    /** 读取单值参数，未传或为空时返回 null */
    public String single(String name) {
        String[] values = raw.get(name);
        if (values == null || values.length == 0) {
            return null;
        }
        if (values.length > 1) {
            throw AgentApiException.badRequest("参数 " + name + " 只能传一个值");
        }
        String value = values[0] == null ? "" : values[0].trim();
        return value.isEmpty() ? null : value;
    }

    public Long longValue(String name) {
        String value = single(name);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw AgentApiException.badRequest("参数 " + name + " 必须是整数: " + value);
        }
    }

    public int intValue(String name, int defaultValue, int min, int max) {
        Long value = longValue(name);
        if (value == null) {
            return defaultValue;
        }
        if (value < min || value > max) {
            throw AgentApiException.badRequest("参数 " + name + " 取值范围为 " + min + " ~ " + max);
        }
        return value.intValue();
    }

    public boolean bool(String name, boolean defaultValue) {
        String value = single(name);
        if (value == null) {
            return defaultValue;
        }
        switch (value.toLowerCase()) {
            case "true":
            case "1":
            case "yes":
                return true;
            case "false":
            case "0":
            case "no":
                return false;
            default:
                throw AgentApiException.badRequest("参数 " + name + " 只能是 true 或 false");
        }
    }

    public String choice(String name, String defaultValue, String... options) {
        String value = single(name);
        if (value == null) {
            return defaultValue;
        }
        for (String option : options) {
            if (option.equalsIgnoreCase(value)) {
                return option;
            }
        }
        throw AgentApiException.badRequest("参数 " + name + " 只能是: " + String.join(", ", options));
    }
}
