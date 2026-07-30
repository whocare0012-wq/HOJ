package top.hcode.hoj.config;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import top.hcode.hoj.interceptor.AccessInterceptor;
import top.hcode.hoj.interceptor.ShiroAuthorizationInterceptor;
import top.hcode.hoj.utils.Constants;

import java.io.File;
import java.util.Arrays;
import java.util.List;
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

    @Value("${cors-allowed-origins:http://localhost,http://127.0.0.1}")
    private String corsAllowedOrigins;

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> allowedOrigins = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toList());
        configuration.setAllowedOrigins(allowedOrigins);
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

    // 前端直接通过/public/img/图片名称即可拿到
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // /api/public/img/** /api/public/file/**
        registry.addResourceHandler(Constants.File.IMG_API.getPath() + "**", Constants.File.FILE_API.getPath() + "**")
                .addResourceLocations("file:" + Constants.File.USER_AVATAR_FOLDER.getPath() + File.separator,
                        "file:" + Constants.File.GROUP_AVATAR_FOLDER.getPath() + File.separator,
                        "file:" + Constants.File.MARKDOWN_FILE_FOLDER.getPath() + File.separator,
                        "file:" + Constants.File.HOME_CAROUSEL_FOLDER.getPath() + File.separator,
                        "file:" + Constants.File.PROBLEM_FILE_FOLDER.getPath() + File.separator);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(shiroAuthorizationInterceptor)
                .addPathPatterns("/api/**")
                .order(Ordered.HIGHEST_PRECEDENCE);
        registry.addInterceptor(accessInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(EXCLUDE_PATH_PATTERNS);
    }
}
