package top.hcode.hoj.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import top.hcode.hoj.utils.ImageResizer;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 公共图片的按需缩略图：GET /api/public/img/{name}.png?w=64 返回长边不超过 64 像素的小图。
 * <p>
 * 头像等图片过去以原图展示，排名页一页就要下载数 MB。缩略图首次请求时生成并缓存到磁盘，
 * 之后直接返回；已上传的旧图片同样适用。不满足条件（宽度不在白名单、文件名不合规、
 * 原图本身就小、格式不支持等）时交给后续的静态资源处理，返回原图。
 */
@Slf4j(topic = "hoj")
public class PublicImageThumbnailFilter extends OncePerRequestFilter {

    static final Set<Integer> ALLOWED_WIDTHS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(64, 128, 256)));
    /** 上传文件名都是 UUID，只接受这种形式，从根本上杜绝路径穿越 */
    private static final Pattern NAME = Pattern.compile("([A-Za-z0-9_-]{1,64})\\.(jpg|jpeg|png)", Pattern.CASE_INSENSITIVE);
    private static final long CACHE_SECONDS = 30L * 24 * 3600;

    private final String urlPrefix;
    private final List<String> sourceFolders;
    private final File thumbnailRoot;
    private final UrlPathHelper urlPathHelper = new UrlPathHelper();

    public PublicImageThumbnailFilter(String urlPrefix, List<String> sourceFolders, String thumbnailRoot) {
        this.urlPrefix = urlPrefix;
        this.sourceFolders = sourceFolders;
        this.thumbnailRoot = new File(thumbnailRoot);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        return request.getParameter("w") == null || !("GET".equals(method) || "HEAD".equals(method));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        File thumbnail = null;
        try {
            thumbnail = resolveThumbnail(request);
        } catch (IOException | RuntimeException e) {
            log.warn("[Thumbnail] Failed to create thumbnail for {}", request.getRequestURI(), e);
        }
        if (thumbnail == null) {
            chain.doFilter(request, response);
            return;
        }
        response.setContentType(thumbnail.getName().endsWith(".png") ? "image/png" : "image/jpeg");
        response.setContentLengthLong(thumbnail.length());
        response.setHeader("Cache-Control", "public, max-age=" + CACHE_SECONDS);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setDateHeader("Last-Modified", thumbnail.lastModified());
        if ("HEAD".equals(request.getMethod())) {
            return;
        }
        try (OutputStream output = response.getOutputStream()) {
            Files.copy(thumbnail.toPath(), output);
        }
    }

    /** 返回可直接输出的缩略图文件；不适用时返回 null */
    File resolveThumbnail(HttpServletRequest request) throws IOException {
        int width;
        try {
            width = Integer.parseInt(request.getParameter("w"));
        } catch (NumberFormatException e) {
            return null;
        }
        if (!ALLOWED_WIDTHS.contains(width)) {
            return null;
        }
        String path = urlPathHelper.getLookupPathForRequest(request);
        if (!path.startsWith(urlPrefix)) {
            return null;
        }
        Matcher matcher = NAME.matcher(path.substring(urlPrefix.length()));
        if (!matcher.matches()) {
            return null;
        }
        String fileName = matcher.group(0);
        File source = findSource(fileName);
        if (source == null) {
            return null;
        }
        String format = ImageResizer.outputFormat(matcher.group(2));
        File target = new File(new File(thumbnailRoot, String.valueOf(width)),
                matcher.group(1) + ("png".equals(format) ? ".png" : ".jpg"));
        if (target.isFile() && target.lastModified() >= source.lastModified()) {
            return target;
        }
        byte[] bytes = ImageResizer.shrink(source, width, format);
        if (bytes == null) {
            return null;
        }
        Files.createDirectories(target.getParentFile().toPath());
        File temp = File.createTempFile(matcher.group(1), ".tmp", target.getParentFile());
        try {
            Files.write(temp.toPath(), bytes);
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
        return target;
    }

    private File findSource(String fileName) {
        for (String folder : sourceFolders) {
            File candidate = new File(folder, fileName);
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }
}
