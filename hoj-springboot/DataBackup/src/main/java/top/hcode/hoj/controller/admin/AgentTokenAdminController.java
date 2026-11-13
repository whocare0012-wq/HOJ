package top.hcode.hoj.controller.admin;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.hcode.hoj.agent.AgentScope;
import top.hcode.hoj.agent.AgentTokenService;
import top.hcode.hoj.agent.manage.AgentChangeLogService;
import top.hcode.hoj.common.result.CommonResult;
import top.hcode.hoj.shiro.AccountProfile;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 数据接口令牌管理，仅 root 可用。
 */
@RestController
@RequestMapping("/api/admin/agent-tokens")
@RequiresAuthentication
@RequiresRoles("root")
public class AgentTokenAdminController {

    @Resource
    private AgentTokenService tokenService;

    @Resource
    private AgentChangeLogService changeLogService;

    @GetMapping
    @RequiresRoles("root")
    public CommonResult<Map<String, Object>> getOverview(
            @RequestParam(defaultValue = "50") Integer logLimit,
            @RequestParam(defaultValue = "true") boolean includeHistory) {
        int limit = logLimit == null ? 50 : Math.max(1, Math.min(200, logLimit));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tokens", tokenService.list());
        result.put("logs", includeHistory ? tokenService.recentLogs(limit) : Collections.emptyList());
        result.put("changes", includeHistory ? changeLogService.recent(limit) : Collections.emptyList());
        return CommonResult.successResponse(result);
    }

    @GetMapping("/logs")
    @RequiresRoles("root")
    public CommonResult<Map<String, Object>> getLogs(
            @RequestParam(defaultValue = "1") int currentPage,
            @RequestParam(defaultValue = "20") int limit) {
        return CommonResult.successResponse(tokenService.logPage(currentPage, limit));
    }

    @GetMapping("/changes")
    @RequiresRoles("root")
    public CommonResult<Map<String, Object>> getChanges(
            @RequestParam(defaultValue = "1") int currentPage,
            @RequestParam(defaultValue = "20") int limit) {
        return CommonResult.successResponse(changeLogService.page(currentPage, limit));
    }

    @PostMapping
    @RequiresRoles("root")
    public CommonResult<Map<String, Object>> createToken(@RequestBody Map<String, Object> body) {
        AccountProfile profile = (AccountProfile) SecurityUtils.getSubject().getPrincipal();
        try {
            Object name = body.get("tokenName");
            return CommonResult.successResponse(tokenService.create(
                    name == null ? null : String.valueOf(name),
                    parseExpireDays(body.get("expiresInDays")),
                    profile.getUid(),
                    parseScopes(body.get("scopes"))));
        } catch (IllegalArgumentException e) {
            return CommonResult.errorResponse(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @RequiresRoles("root")
    public CommonResult<Void> revokeToken(@PathVariable Long id) {
        if (!tokenService.revoke(id)) {
            return CommonResult.errorResponse("令牌不存在或已吊销");
        }
        return CommonResult.successResponse();
    }

    /** 未传时保持旧行为：只读令牌 */
    private static List<String> parseScopes(Object value) {
        if (value == null) {
            return Collections.singletonList(AgentScope.READ);
        }
        List<String> scopes = new ArrayList<>();
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                scopes.add(String.valueOf(item));
            }
        } else {
            for (String item : String.valueOf(value).split(",")) {
                if (!item.trim().isEmpty()) {
                    scopes.add(item.trim());
                }
            }
        }
        return scopes;
    }

    private static Integer parseExpireDays(Object value) {
        if (value == null || "".equals(value) || "never".equals(value)) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("有效期格式不正确");
        }
    }
}
