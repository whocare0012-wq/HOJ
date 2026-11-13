package top.hcode.hoj.agent;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 令牌权限范围：
 * <ul>
 *     <li>{@code read}：只读数据接口（提交记录、学生、统计）</li>
 *     <li>{@code manage}：题目与训练管理接口（/api/agent/v1/admin/**），可新增、修改题目与训练</li>
 * </ul>
 * 升级前生成的令牌没有 scopes 字段，视为只有 read。
 */
public final class AgentScope {

    public static final String READ = "read";
    public static final String MANAGE = "manage";
    public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(READ, MANAGE));

    static final String MANAGE_PATH_PREFIX = "/api/agent/v1/admin/";
    private static final String PING_PATH = "/api/agent/v1/ping";

    private AgentScope() {
    }

    /** 校验并规范化为逗号分隔的存储格式，顺序固定为 read,manage */
    public static String normalize(Collection<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个权限范围：" + String.join("、", ALL));
        }
        Set<String> unknown = new TreeSet<>();
        Set<String> picked = new LinkedHashSet<>();
        for (String scope : scopes) {
            String value = scope == null ? "" : scope.trim().toLowerCase();
            if (ALL.contains(value)) {
                picked.add(value);
            } else {
                unknown.add(String.valueOf(scope));
            }
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("未知的权限范围: " + String.join(", ", unknown)
                    + "；可选: " + String.join(", ", ALL));
        }
        StringBuilder result = new StringBuilder();
        for (String scope : ALL) {
            if (picked.contains(scope)) {
                if (result.length() > 0) {
                    result.append(',');
                }
                result.append(scope);
            }
        }
        return result.toString();
    }

    /** 解析数据库中保存的值；为空时按旧令牌处理（只读） */
    public static Set<String> parse(String stored) {
        Set<String> result = new LinkedHashSet<>();
        if (stored != null) {
            for (String part : stored.split(",")) {
                String value = part.trim().toLowerCase();
                if (ALL.contains(value)) {
                    result.add(value);
                }
            }
        }
        if (result.isEmpty()) {
            result.add(READ);
        }
        return Collections.unmodifiableSet(result);
    }

    /** 访问该路径所需的权限；ping 任意令牌均可访问，返回 null */
    public static String requiredFor(String pathWithinApplication) {
        if (pathWithinApplication == null || PING_PATH.equals(pathWithinApplication)) {
            return null;
        }
        return pathWithinApplication.startsWith(MANAGE_PATH_PREFIX) ? MANAGE : READ;
    }

    public static boolean isManagePath(String pathWithinApplication) {
        return pathWithinApplication != null && pathWithinApplication.startsWith(MANAGE_PATH_PREFIX);
    }
}
