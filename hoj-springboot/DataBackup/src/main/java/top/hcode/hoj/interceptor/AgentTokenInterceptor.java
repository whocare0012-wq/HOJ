package top.hcode.hoj.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import top.hcode.hoj.agent.AgentApiException;
import top.hcode.hoj.agent.AgentJson;
import top.hcode.hoj.agent.AgentRateLimiter;
import top.hcode.hoj.agent.AgentTokenService;
import top.hcode.hoj.utils.IpUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * /api/agent/v1/** 的令牌校验、限流与审计日志。
 * 该路径在 Shiro 中配置为 anon，不经过登录 JWT 过滤器，鉴权完全由此处负责。
 */
@Component
@Slf4j(topic = "hoj")
public class AgentTokenInterceptor implements HandlerInterceptor {

    public static final String RESULT_COUNT_ATTR = AgentTokenInterceptor.class.getName() + ".resultCount";
    static final String PRINCIPAL_ATTR = AgentTokenInterceptor.class.getName() + ".principal";
    public static final String TOKEN_NAME_ATTR = AgentTokenInterceptor.class.getName() + ".tokenName";

    @Resource
    private AgentTokenService tokenService;

    @Resource
    private AgentRateLimiter rateLimiter;

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

        request.setAttribute(PRINCIPAL_ATTR, principal);
        request.setAttribute(TOKEN_NAME_ATTR, principal.getTokenName());
        tokenService.touch(principal.getTokenId(), ip);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
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
