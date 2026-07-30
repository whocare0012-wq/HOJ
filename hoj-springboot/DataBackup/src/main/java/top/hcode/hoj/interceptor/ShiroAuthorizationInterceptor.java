package top.hcode.hoj.interceptor;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.UnauthenticatedException;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresGuest;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.apache.shiro.authz.annotation.RequiresUser;
import org.apache.shiro.subject.Subject;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Enforces Shiro annotations at the Spring MVC boundary.
 *
 * The legacy Shiro/Spring proxy chain does not reliably resolve class-level
 * annotations from CGLIB controller proxies. Resolving annotations from the
 * concrete HandlerMethod keeps class-level and method-level authorization
 * consistent without relying on proxy implementation details.
 */
@Component
public class ShiroAuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();
        Class<?> beanType = handlerMethod.getBeanType();
        Subject subject = SecurityUtils.getSubject();

        enforceAuthentication(subject, method, beanType);
        enforceUser(subject, method, beanType);
        enforceGuest(subject, method, beanType);
        enforceRoles(subject, method, beanType);
        enforcePermissions(subject, method, beanType);
        return true;
    }

    private void enforceAuthentication(Subject subject, Method method, Class<?> beanType) {
        if (findAnnotation(method, beanType, RequiresAuthentication.class) != null
                && !subject.isAuthenticated()) {
            throw new UnauthenticatedException("Authentication is required");
        }
    }

    private void enforceUser(Subject subject, Method method, Class<?> beanType) {
        if (findAnnotation(method, beanType, RequiresUser.class) != null
                && subject.getPrincipal() == null) {
            throw new UnauthenticatedException("A known user is required");
        }
    }

    private void enforceGuest(Subject subject, Method method, Class<?> beanType) {
        if (findAnnotation(method, beanType, RequiresGuest.class) != null
                && subject.getPrincipal() != null) {
            throw new UnauthenticatedException("This endpoint is only available to guests");
        }
    }

    private void enforceRoles(Subject subject, Method method, Class<?> beanType) {
        RequiresRoles annotation = findAnnotation(method, beanType, RequiresRoles.class);
        if (annotation == null || annotation.value().length == 0) {
            return;
        }
        if (annotation.logical() == Logical.AND) {
            subject.checkRoles(Arrays.asList(annotation.value()));
            return;
        }
        for (String role : annotation.value()) {
            if (subject.hasRole(role)) {
                return;
            }
        }
        throw new UnauthorizedException("Subject does not have any required role");
    }

    private void enforcePermissions(Subject subject, Method method, Class<?> beanType) {
        RequiresPermissions annotation =
                findAnnotation(method, beanType, RequiresPermissions.class);
        if (annotation == null || annotation.value().length == 0) {
            return;
        }
        if (annotation.logical() == Logical.AND) {
            subject.checkPermissions(annotation.value());
            return;
        }
        for (String permission : annotation.value()) {
            if (subject.isPermitted(permission)) {
                return;
            }
        }
        throw new UnauthorizedException("Subject does not have any required permission");
    }

    private <T extends Annotation> T findAnnotation(
            Method method,
            Class<?> beanType,
            Class<T> annotationType) {
        T annotation = AnnotationUtils.findAnnotation(method, annotationType);
        return annotation != null
                ? annotation
                : AnnotationUtils.findAnnotation(beanType, annotationType);
    }
}
