package top.hcode.hoj.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicImageThumbnailFilterTest {

    @TempDir
    Path dir;

    private PublicImageThumbnailFilter filter;
    private File thumbRoot;

    @BeforeEach
    void setUp() throws IOException {
        File avatars = dir.resolve("avatar").toFile();
        File markdown = dir.resolve("md").toFile();
        avatars.mkdirs();
        markdown.mkdirs();
        thumbRoot = dir.resolve("thumbnail").toFile();
        ImageIO.write(new BufferedImage(800, 800, BufferedImage.TYPE_INT_RGB), "png",
                new File(markdown, "0f3a9c.png"));
        ImageIO.write(new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB), "jpg",
                new File(avatars, "small1.jpg"));
        filter = new PublicImageThumbnailFilter("/api/public/img/",
                Arrays.asList(avatars.getPath(), markdown.getPath()), thumbRoot.getPath());
    }

    private MockHttpServletResponse get(String uri, String width) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (width != null) {
            request.setParameter("w", width);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        // 交给后续静态资源处理时，chain 会收到请求；这里用响应头区分两种情况
        return response;
    }

    @Test
    void servesAndCachesThumbnail() throws Exception {
        MockHttpServletResponse response = get("/api/public/img/0f3a9c.png", "64");
        assertEquals("image/png", response.getContentType());
        assertTrue(response.getHeader("Cache-Control").contains("max-age="));
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(response.getContentAsByteArray()));
        assertEquals(64, thumb.getWidth());
        File cached = new File(new File(thumbRoot, "64"), "0f3a9c.png");
        assertTrue(cached.isFile());

        long modified = cached.lastModified();
        get("/api/public/img/0f3a9c.png", "64");
        assertEquals(modified, cached.lastModified());
    }

    @Test
    void fallsThroughToOriginalWhenNotApplicable() throws Exception {
        // 不在白名单的宽度、无宽度参数、原图已经很小、文件不存在、非法文件名
        for (String[] c : new String[][]{
                {"/api/public/img/0f3a9c.png", "100"},
                {"/api/public/img/0f3a9c.png", "abc"},
                {"/api/public/img/small1.jpg", "64"},
                {"/api/public/img/missing.png", "64"},
                {"/api/public/img/..%2fsecret.png", "64"},
                {"/api/public/img/a.b.png", "64"},
                {"/api/public/img/evil.svg", "64"}}) {
            MockHttpServletResponse response = get(c[0], c[1]);
            assertNull(response.getContentType(), String.join(" ", c));
            assertEquals(0, response.getContentAsByteArray().length, String.join(" ", c));
        }
        assertNull(get("/api/public/img/0f3a9c.png", null).getContentType());
    }

    @Test
    void headRequestReturnsHeadersOnly() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("HEAD", "/api/public/img/0f3a9c.png");
        request.setParameter("w", "128");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals("image/png", response.getContentType());
        assertEquals(0, response.getContentAsByteArray().length);
        assertNotNull(response.getHeader("Content-Length"));
    }
}
