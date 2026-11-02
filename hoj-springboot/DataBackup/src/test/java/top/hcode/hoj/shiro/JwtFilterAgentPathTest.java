package top.hcode.hoj.shiro;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtFilterAgentPathTest {

    private static boolean agent(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        // DispatcherServlet 映射在 "/" 时，容器给出的 servletPath 即完整请求路径
        request.setServletPath(uri);
        return JwtFilter.isAgentApiRequest(request);
    }

    @Test
    void onlyAgentApiPathsSkipLoginJwt() {
        assertTrue(agent("/api/agent/v1/submissions"));
        assertTrue(agent("/api/agent/v1/ping"));
        assertFalse(agent("/api/admin/agent-tokens"));
        assertFalse(agent("/api/login"));
        assertFalse(agent("/api/agentx/v1"));
    }

    @Test
    void traversalCannotDisguiseOtherPathsAsAgentApi() {
        assertFalse(agent("/api/agent/../admin/agent-tokens"));
        assertFalse(agent("/api/agent/v1/../../admin/user"));
    }
}
