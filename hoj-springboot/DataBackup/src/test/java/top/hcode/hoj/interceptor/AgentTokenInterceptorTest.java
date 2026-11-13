package top.hcode.hoj.interceptor;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import top.hcode.hoj.agent.AgentApiException;
import top.hcode.hoj.agent.AgentRateLimiter;
import top.hcode.hoj.agent.AgentScope;
import top.hcode.hoj.agent.AgentSubjectBinder;
import top.hcode.hoj.agent.AgentTokenService;
import top.hcode.hoj.shiro.AccountProfile;
import org.apache.shiro.util.ThreadState;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTokenInterceptorTest {

    private final AgentTokenService tokenService = mock(AgentTokenService.class);
    private final AgentRateLimiter rateLimiter = mock(AgentRateLimiter.class);
    private final AgentSubjectBinder subjectBinder = mock(AgentSubjectBinder.class);

    private AgentTokenInterceptor interceptor() {
        AgentTokenInterceptor interceptor = new AgentTokenInterceptor();
        ReflectionTestUtils.setField(interceptor, "tokenService", tokenService);
        ReflectionTestUtils.setField(interceptor, "rateLimiter", rateLimiter);
        ReflectionTestUtils.setField(interceptor, "subjectBinder", subjectBinder);
        when(rateLimiter.getLimitPerMinute()).thenReturn(120);
        return interceptor;
    }

    private static MockHttpServletRequest request(String path, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    @Test
    void rejectsRequestWithoutValidTokenAsJson() throws Exception {
        when(tokenService.authenticate(isNull()))
                .thenThrow(new AgentApiException(401, "missing_token", "缺少令牌"));
        MockHttpServletRequest request = request("/api/agent/v1/submissions", null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor().preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertTrue(response.getContentAsString().contains("\"code\":\"missing_token\""));
        assertNull(request.getAttribute(AgentTokenInterceptor.PRINCIPAL_ATTR));
        verify(tokenService, never()).touch(anyLong(), any());
        verify(rateLimiter, never()).tryAcquire(anyLong());
    }

    @Test
    void acceptsValidTokenAndWritesAuditLogAfterCompletion() throws Exception {
        when(tokenService.authenticate("Bearer hoj_at_ok"))
                .thenReturn(new AgentTokenService.AgentPrincipal(5L, "analysis"));
        when(rateLimiter.tryAcquire(5L)).thenReturn(true);
        MockHttpServletRequest request = request("/api/agent/v1/submissions", "Bearer hoj_at_ok");
        request.setQueryString("date=2026-11-01");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AgentTokenInterceptor interceptor = interceptor();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertNotNull(request.getAttribute(AgentTokenInterceptor.PRINCIPAL_ATTR));
        assertEquals("analysis", request.getAttribute(AgentTokenInterceptor.TOKEN_NAME_ATTR));
        verify(tokenService).touch(eq(5L), any());

        request.setAttribute(AgentTokenInterceptor.RESULT_COUNT_ATTR, 42);
        interceptor.afterCompletion(request, response, new Object(), null);
        verify(tokenService).log(eq(5L), eq("/api/agent/v1/submissions"), eq("date=2026-11-01"),
                eq(42), eq(200), any());
    }

    @Test
    void rateLimitedRequestGets429AndIsAudited() throws Exception {
        when(tokenService.authenticate("Bearer hoj_at_ok"))
                .thenReturn(new AgentTokenService.AgentPrincipal(5L, "analysis"));
        when(rateLimiter.tryAcquire(5L)).thenReturn(false);
        MockHttpServletRequest request = request("/api/agent/v1/stats", "Bearer hoj_at_ok");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor().preHandle(request, response, new Object()));
        assertEquals(429, response.getStatus());
        assertNotNull(response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("rate_limited"));
        verify(tokenService).log(eq(5L), eq("/api/agent/v1/stats"), isNull(), isNull(), eq(429), any());
        verify(tokenService, never()).touch(anyLong(), any());
    }

    @Test
    void readOnlyTokenCannotUseManagementApi() throws Exception {
        when(tokenService.authenticate("Bearer hoj_at_ok"))
                .thenReturn(new AgentTokenService.AgentPrincipal(5L, "analysis"));
        MockHttpServletRequest request = request("/api/agent/v1/admin/problems", "Bearer hoj_at_ok");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor().preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("insufficient_scope"));
        verify(tokenService).log(eq(5L), eq("/api/agent/v1/admin/problems"), isNull(), isNull(), eq(403), any());
        verify(rateLimiter, never()).tryAcquire(anyLong());
        verify(subjectBinder, never()).bind(any(), any(), any());
    }

    @Test
    void encodedOrMalformedManagementPathsStillRequireManageScope() throws Exception {
        when(tokenService.authenticate("Bearer hoj_at_ok"))
                .thenReturn(new AgentTokenService.AgentPrincipal(5L, "analysis"));
        when(rateLimiter.tryAcquire(5L)).thenReturn(true);
        for (String uri : new String[]{"/api/agent/v1/%61dmin/problems", "/api/agent/v1//admin/problems",
                "/api/agent/v1;x=1/admin/problems"}) {
            MockHttpServletRequest request = request(uri, "Bearer hoj_at_ok");
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertFalse(interceptor().preHandle(request, response, new Object()), uri);
            assertEquals(403, response.getStatus(), uri);
        }
    }

    @Test
    void manageOnlyTokenCannotReadSubmissionsButCanPing() throws Exception {
        when(tokenService.authenticate("Bearer hoj_at_m")).thenReturn(new AgentTokenService.AgentPrincipal(
                6L, "editor", AgentScope.parse("manage"), "root-uid"));
        when(rateLimiter.tryAcquire(6L)).thenReturn(true);

        MockHttpServletResponse denied = new MockHttpServletResponse();
        assertFalse(interceptor().preHandle(request("/api/agent/v1/submissions", "Bearer hoj_at_m"),
                denied, new Object()));
        assertEquals(403, denied.getStatus());

        assertTrue(interceptor().preHandle(request("/api/agent/v1/ping", "Bearer hoj_at_m"),
                new MockHttpServletResponse(), new Object()));
    }

    @Test
    void managementRequestRunsAsTokenCreatorAndRestoresSubject() throws Exception {
        when(tokenService.authenticate("Bearer hoj_at_m")).thenReturn(new AgentTokenService.AgentPrincipal(
                7L, "editor", new HashSet<>(Arrays.asList("read", "manage")), "root-uid"));
        when(rateLimiter.tryAcquire(7L)).thenReturn(true);
        AccountProfile root = new AccountProfile();
        root.setUid("root-uid");
        root.setUsername("root");
        when(tokenService.loadCreatorProfile("root-uid")).thenReturn(root);
        ThreadState state = mock(ThreadState.class);
        when(subjectBinder.bind(eq(root), any(), any())).thenReturn(state);
        MockHttpServletRequest request = request("/api/agent/v1/admin/problems/P1000", "Bearer hoj_at_m");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AgentTokenInterceptor interceptor = interceptor();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(root, request.getAttribute(AgentTokenInterceptor.OPERATOR_ATTR));
        assertEquals(7L, request.getAttribute(AgentTokenInterceptor.TOKEN_ID_ATTR));
        verify(state, never()).restore();

        interceptor.afterCompletion(request, response, new Object(), null);
        verify(state).restore();
        assertNull(request.getAttribute(AgentTokenInterceptor.THREAD_STATE_ATTR));
    }

    @Test
    void readRequestDoesNotBindAnySubject() throws Exception {
        when(tokenService.authenticate("Bearer hoj_at_ok"))
                .thenReturn(new AgentTokenService.AgentPrincipal(5L, "analysis"));
        when(rateLimiter.tryAcquire(5L)).thenReturn(true);
        assertTrue(interceptor().preHandle(request("/api/agent/v1/stats", "Bearer hoj_at_ok"),
                new MockHttpServletResponse(), new Object()));
        verify(subjectBinder, never()).bind(any(), any(), any());
        verify(tokenService, never()).loadCreatorProfile(any());
    }

    @Test
    void afterCompletionWithoutPrincipalWritesNothing() {
        interceptor().afterCompletion(request("/api/agent/v1/ping", null),
                new MockHttpServletResponse(), new Object(), null);
        verify(tokenService, never()).log(anyLong(), any(), any(), any(), eq(200), any());
    }
}
