package top.hcode.hoj.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.filter.CorsFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebMvcConfigTest {

    @Test
    void allowsConfiguredLocalhostPortPattern() throws Exception {
        CorsFilter filter = createFilter(
                "https://judge.example.com",
                "http://localhost:*,http://127.0.0.1:*");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/login");
        request.addHeader("Origin", "http://localhost:13104");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        assertEquals("http://localhost:13104", response.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void keepsRejectingUnconfiguredOrigins() throws Exception {
        CorsFilter filter = createFilter(
                "https://judge.example.com",
                "http://localhost:*,http://127.0.0.1:*");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/login");
        request.addHeader("Origin", "https://untrusted.example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @SuppressWarnings("unchecked")
    private CorsFilter createFilter(String origins, String originPatterns) {
        WebMvcConfig config = new WebMvcConfig();
        ReflectionTestUtils.setField(config, "corsAllowedOrigins", origins);
        ReflectionTestUtils.setField(config, "corsAllowedOriginPatterns", originPatterns);
        FilterRegistrationBean<CorsFilter> registration = config.corsFilterRegistration();
        return registration.getFilter();
    }
}
