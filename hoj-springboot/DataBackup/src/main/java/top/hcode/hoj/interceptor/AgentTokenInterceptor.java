package top.hcode.hoj.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.util.ThreadState;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UrlPathHelper;
import top.hcode.hoj.agent.AgentApiException;
import top.hcode.hoj.agent.AgentJson;
import top.hcode.hoj.agent.AgentRateLimiter;
import top.hcode.hoj.agent.AgentScope;
import top.hcode.hoj.agent.AgentSubjectBinder;
import top.hcode.hoj.agent.AgentTokenService;
import top.hcode.hoj.shiro.AccountProfile;
import top.hcode.hoj.utils.IpUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * /api/agent/v1/** 的令牌校验、权限范围校验、限流与审计日志。
 * 该路径在 Shiro 中配置为 anon，不经过登录 JWT 过滤器，鉴权完全由此处负责。
 * 管理接口（/api/agent/v1/admin/**）需要 manage 权限，并以令牌创建者的身份执行。
 */
@Component
@Slf4j(topic = "hoj")
public class AgentTokenInterceptor implements HandlerInterceptor {

    public static final String RESULT_COUNT_ATTR = AgentTokenInterceptor.class.getName() + ".resultCount";
    static final String PRINCIPAL_ATTR = AgentTokenInterceptor.class.getName() + ".principal";
    public static final String TOKEN_NAME_ATTR = AgentTokenInterceptor.class.getName() + ".tokenName";
    public static final String TOKEN_ID_ATTR = AgentTokenInterceptor.class.getName() + ".tokenId";
    public static final String SCOPES_ATTR = AgentTokenInterceptor.class.getName() + ".scopes";
    public static final String OPERATOR_ATTR = AgentTokenInterceptor.class.getName() + ".operator";
    static final String THREAD_STATE_ATTR = AgentTokenInterceptor.class.getName() + ".threadState";

    /**
     * 与 Spring MVC 路由一致地解析路径（URL 解码、去掉 ;参数、合并重复的 /），
     * 避免 /api/agent/v1/%61dmin/... 这类写法绕过 manage 权限校验。
     */
    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    @Resource
    private AgentTokenService tokenService;

    @Resource
    private AgentRateLimiter rateLimiter;

    @Resource
    private AgentSubjectBinder subjectBinder;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String ip = IpUtils.getUserIpAddr(request);
        AgentTokenService.AgentPrincipal principal;
        try {
            principal = tokenService.authenticate(request.getHeader("Authorization"));
        } catch (AgentApiException e) {
            log.warn("[Agent API] Rejected request {} from {}: {}", request.getRequestURI(), ip, e.getCode());
            AgentJson.writeError(response, e);
            return false;
        }

        String path = PATH_HELPER.getPathWithinApplication(request);
        String requiredScope = AgentScope.requiredFor(path);
        if (requiredScope != null && !principal.hasScope(requiredScope)) {
            AgentJson.writeError(response, new AgentApiException(403, "insufficient_scope",
                    AgentScope.MANAGE.equals(requiredScope)
                            ? "该令牌没有题目/训练管理权限（manage）：请 root 在后台「AI 数据接口」页面生成勾选了“题目与训练管理”的令牌"
                            : "该令牌没有提交数据读取权限（read）"));
            tokenService.log(principal.getTokenId(), request.getRequestURI(), request.getQueryString(),
                    null, 403, ip);
            return false;
        }

        if (!rateLimiter.tryAcquire(principal.getTokenId())) {
            long retryAfter = 60 - (System.currentTimeMillis() / 1000) % 60;
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            AgentJson.writeError(response, new AgentApiException(429, "rate_limited",
                    "请求过于频繁：每个令牌每分钟最多 " + rateLimiter.getLimitPerMinute() + " 次，请 "
                            + retryAfter + " 秒后重试；"
                            + "批量拉取请使用 format=ndjson 一次导出"));
            tokenService.log(principal.getTokenId(), request.getRequestURI(), request.getQueryString(),
                    null, 429, ip);
            return false;
        }

        if (AgentScope.isManagePath(path)) {
            AccountProfile operator;
            try {
                operator = tokenService.loadCreatorProfile(principal.getCreatorUid());
            } catch (AgentApiException e) {
                AgentJson.writeError(response, e);
                tokenService.log(principal.getTokenId(), request.getRequestURI(), request.getQueryString(),
                        null, e.getStatus(), ip);
                return false;
            }
            request.setAttribute(OPERATOR_ATTR, operator);
            request.setAttribute(THREAD_STATE_ATTR, subjectBinder.bind(operator, request, response));
        }

        request.setAttribute(PRINCIPAL_ATTR, principal);
        request.setAttribute(TOKEN_NAME_ATTR, principal.getTokenName());
        request.setAttribute(TOKEN_ID_ATTR, principal.getTokenId());
        request.setAttribute(SCOPES_ATTR, principal.getScopes());
        tokenService.touch(principal.getTokenId(), ip);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        Object threadState = request.getAttribute(THREAD_STATE_ATTR);
        if (threadState instanceof ThreadState) {
            request.removeAttribute(THREAD_STATE_ATTR);
            ((ThreadState) threadState).restore();
        }
        Object principal = request.getAttribute(PRINCIPAL_ATTR);
        if (!(principal instanceof AgentTokenService.AgentPrincipal)) {
            return;
        }
        Object count = request.getAttribute(RESULT_COUNT_ATTR);
        tokenService.log(((AgentTokenService.AgentPrincipal) principal).getTokenId(),
                request.getRequestURI(), request.getQueryString(),
                count instanceof Integer ? (Integer) count : null,
                response.getStatus(), IpUtils.getUserIpAddr(request));
    }
}
