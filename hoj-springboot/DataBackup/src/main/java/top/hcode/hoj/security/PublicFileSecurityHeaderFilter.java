package top.hcode.hoj.security;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import top.hcode.hoj.utils.UploadFileTypes;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 为 /api/public/img/** 与 /api/public/file/** 下的上传文件加安全响应头。
 * <p>
 * 图片和 PDF 可直接在浏览器中打开，其余类型按附件下载并启用 CSP sandbox。
 * 扩展名按与资源处理器一致的路径判断：解码后、去掉分号参数。
 */
public class PublicFileSecurityHeaderFilter extends OncePerRequestFilter {

    private final UrlPathHelper urlPathHelper = new UrlPathHelper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");

        String path = urlPathHelper.getLookupPathForRequest(request);
        if (!UploadFileTypes.isInlineSafe(UploadFileTypes.getSuffix(path))) {
            response.setHeader("Content-Disposition", "attachment");
            response.setHeader("Content-Security-Policy", "sandbox");
        }
        filterChain.doFilter(request, response);
    }
}
