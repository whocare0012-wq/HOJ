package top.hcode.hoj.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PublicFileSecurityHeaderFilterTest {

    private MockHttpServletResponse request(String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new PublicFileSecurityHeaderFilter().doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void imagesAndPdfStayInline() throws Exception {
        MockHttpServletResponse image = request("/api/public/img/avatar.png");
        assertEquals("nosniff", image.getHeader("X-Content-Type-Options"));
        assertNull(image.getHeader("Content-Disposition"));

        assertNull(request("/api/public/file/statement.PDF").getHeader("Content-Disposition"));
    }

    @Test
    void activeContentIsForcedToDownloadInSandbox() throws Exception {
        for (String uri : new String[]{
                "/api/public/file/evil.html",
                "/api/public/img/evil.svg",
                "/api/public/file/evil.js",
                "/api/public/file/no-extension"}) {
            MockHttpServletResponse response = request(uri);
            assertEquals("attachment", response.getHeader("Content-Disposition"), uri);
            assertEquals("sandbox", response.getHeader("Content-Security-Policy"), uri);
            assertEquals("nosniff", response.getHeader("X-Content-Type-Options"), uri);
        }
    }

    @Test
    void pathTricksCannotDisguiseActiveContentAsImage() throws Exception {
        // 资源处理器会去掉分号参数并解码路径，过滤器必须按同样的路径判断
        assertEquals("attachment",
                request("/api/public/file/evil.html;.png").getHeader("Content-Disposition"));
        assertEquals("attachment",
                request("/api/public/file/evil%2ehtml").getHeader("Content-Disposition"));
    }
}
