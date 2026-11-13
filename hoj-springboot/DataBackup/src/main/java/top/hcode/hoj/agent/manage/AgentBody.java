package top.hcode.hoj.agent.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import top.hcode.hoj.agent.AgentApiException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 管理接口的 JSON 请求体读取。与查询参数一致：未知字段直接报错，类型不对给出明确提示，
 * 避免 agent 拼错字段名时修改被静默忽略。
 */
public final class AgentBody {

    private final ObjectNode node;
    private final String context;

    public AgentBody(JsonNode node, String context, Set<String> allowed) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            node = JsonNodeFactory.instance.objectNode();
        }
        if (!node.isObject()) {
            throw AgentApiException.badRequest(context + " 必须是 JSON 对象");
        }
        this.node = (ObjectNode) node;
        this.context = context;
        Set<String> unknown = new TreeSet<>();
        Iterator<String> names = this.node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) {
                unknown.add(name);
            }
        }
        if (!unknown.isEmpty()) {
            throw AgentApiException.badRequest(context + " 中有未知字段: " + String.join(", ", unknown)
                    + "；可用字段: " + String.join(", ", new TreeSet<>(allowed)));
        }
    }

    public static Set<String> fields(String... names) {
        return new LinkedHashSet<>(Arrays.asList(names));
    }

    public static Set<String> fields(Set<String> base, String... extra) {
        Set<String> result = new LinkedHashSet<>(base);
        result.addAll(Arrays.asList(extra));
        return result;
    }

    public boolean has(String name) {
        return node.has(name);
    }

    public JsonNode raw(String name) {
        return node.get(name);
    }

    private String label(String name) {
        return context + "." + name;
    }

    /** 字段存在时返回字符串（允许 null 表示清空）；字段不存在返回 null */
    public String string(String name) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw AgentApiException.badRequest(label(name) + " 必须是字符串");
        }
        return value.asText();
    }

    public String requiredString(String name) {
        String value = string(name);
        if (value == null || value.trim().isEmpty()) {
            throw AgentApiException.badRequest("缺少必填字段 " + label(name));
        }
        return value;
    }

    public Integer integer(String name, int min, int max) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw AgentApiException.badRequest(label(name) + " 必须是整数");
        }
        int result = value.asInt();
        if (result < min || result > max) {
            throw AgentApiException.badRequest(label(name) + " 取值范围为 " + min + " ~ " + max);
        }
        return result;
    }

    public Long longValue(String name) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw AgentApiException.badRequest(label(name) + " 必须是整数");
        }
        return value.asLong();
    }

    public Boolean bool(String name) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isBoolean()) {
            throw AgentApiException.badRequest(label(name) + " 必须是 true 或 false");
        }
        return value.asBoolean();
    }

    public String choice(String name, String... options) {
        String value = string(name);
        if (value == null) {
            return null;
        }
        for (String option : options) {
            if (option.equalsIgnoreCase(value.trim())) {
                return option;
            }
        }
        throw AgentApiException.badRequest(label(name) + " 只能是: " + String.join(", ", options));
    }

    /** 字符串数组；字段不存在返回 null，存在则返回去空白、去重后的列表 */
    public List<String> stringList(String name, int maxSize) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            return value == null ? null : Collections.emptyList();
        }
        if (!value.isArray()) {
            throw AgentApiException.badRequest(label(name) + " 必须是字符串数组");
        }
        if (value.size() > maxSize) {
            throw AgentApiException.badRequest(label(name) + " 最多 " + maxSize + " 项");
        }
        Set<String> result = new LinkedHashSet<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                throw AgentApiException.badRequest(label(name) + " 必须是字符串数组");
            }
            String text = item.asText().trim();
            if (!text.isEmpty()) {
                result.add(text);
            }
        }
        return new ArrayList<>(result);
    }

    /** 对象数组，每个元素按 allowed 字段校验 */
    public List<AgentBody> objectList(String name, Set<String> allowed, int maxSize) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isArray()) {
            throw AgentApiException.badRequest(label(name) + " 必须是数组");
        }
        if (value.size() > maxSize) {
            throw AgentApiException.badRequest(label(name) + " 最多 " + maxSize + " 项");
        }
        List<AgentBody> result = new ArrayList<>();
        int index = 0;
        for (JsonNode item : value) {
            result.add(new AgentBody(item, label(name) + "[" + index + "]", allowed));
            index++;
        }
        return result;
    }
}
