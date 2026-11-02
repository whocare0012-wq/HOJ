package top.hcode.hoj.config;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.CacheControl;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import top.hcode.hoj.interceptor.AccessInterceptor;
import top.hcode.hoj.interceptor.AgentTokenInterceptor;
import top.hcode.hoj.interceptor.ShiroAuthorizationInterceptor;
import top.hcode.hoj.security.PublicFileSecurityHeaderFilter;
import top.hcode.hoj.utils.Constants;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 解决跨域问题以及增加注解拦截类
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String[] EXCLUDE_PATH_PATTERNS = new String[]{
            "/api/admin/**", "/api/file/**", "/api/msg/**", "/api/public/**"
    };

    @Autowired
    private AccessInterceptor accessInterceptor;

    @Autowired
    private ShiroAuthorizationInterceptor shiroAuthorizationInterceptor;

    @Autowired
    private AgentTokenInterceptor agentTokenInterceptor;

    @Value("${cors-allowed-origins:http://localhost,http://127.0.0.1}")
    private String corsAllowedOrigins;

    @Value("${cors-allowed-origin-patterns:}")
    private String corsAllowedOriginPatterns;

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(parseCorsValues(corsAllowedOrigins));
        configuration.setAllowedOriginPatterns(parseCorsValues(corsAllowedOriginPatterns));
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "Url-Type", "X-Requested-With"));
        configuration.setExposedHeaders(Arrays.asList(
                "Refresh-Token",
                "Authorization",
                "Url-Type",
                "Content-Disposition",
                "Content-Type"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        FilterRegistrationBean<CorsFilter> registration =
                new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<PublicFileSecurityHeaderFilter> publicFileSecurityHeaderFilterRegistration() {
        FilterRegistrationBean<PublicFileSecurityHeaderFilter> registration =
                new FilterRegistrationBean<>(new PublicFileSecurityHeaderFilter());
        registration.addUrlPatterns(
                Constants.File.IMG_API.getPath() + "*",
                Constants.File.FILE_API.getPath() + "*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    private List<String> parseCorsValues(String configuredValues) {
        return Arrays.stream(configuredValues.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toList());
    }

    /** /api/public/img/** 与 /api/public/file/** 对应的磁盘目录，按顺序查找 */
    static final List<String> PUBLIC_FILE_FOLDERS = Collections.unmodifiableList(Arrays.asList(
            Constants.File.USER_AVATAR_FOLDER.getPath(),
            Constants.File.GROUP_AVATAR_FOLDER.getPath(),
            Constants.File.MARKDOWN_FILE_FOLDER.getPath(),
            Constants.File.HOME_CAROUSEL_FOLDER.getPath(),
            Constants.File.PROBLEM_FILE_FOLDER.getPath()));

    @Bean
    public FilterRegistrationBean<PublicImageThumbnailFilter> publicImageThumbnailFilterRegistration() {
        FilterRegistrationBean<PublicImageThumbnailFilter> registration =
                new FilterRegistrationBean<>(new PublicImageThumbnailFilter(
                        Constants.File.IMG_API.getPath(),
                        PUBLIC_FILE_FOLDERS,
                        Constants.File.IMG_THUMBNAIL_FOLDER.getPath()));
        registration.addUrlPatterns(Constants.File.IMG_API.getPath() + "*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }

    // 前端直接通过/public/img/图片名称即可拿到
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // /api/public/img/** /api/public/file/**
        // 上传文件都以 UUID 命名、内容不会变，可长期缓存（只作用于成功返回的文件）
        registry.addResourceHandler(Constants.File.IMG_API.getPath() + "**", Constants.File.FILE_API.getPath() + "**")
                .addResourceLocations(PUBLIC_FILE_FOLDERS.stream()
                        .map(folder -> "file:" + folder + File.separator)
                        .toArray(String[]::new))
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(shiroAuthorizationInterceptor)
                .addPathPatterns("/api/**")
                .order(Ordered.HIGHEST_PRECEDENCE);
        registry.addInterceptor(accessInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(EXCLUDE_PATH_PATTERNS);
        registry.addInterceptor(agentTokenInterceptor)
                .addPathPatterns("/api/agent/**")
                .excludePathPatterns("/api/agent/v1/openapi.json");
    }
}
