package top.hcode.hoj.interceptor;

import org.apache.shiro.authz.UnauthenticatedException;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShiroAuthorizationInterceptorTest {

    private final ShiroAuthorizationInterceptor interceptor =
            new ShiroAuthorizationInterceptor();

    @AfterEach
    void clearSubject() {
        ThreadContext.unbindSubject();
    }

    @Test
    void rejectsAnonymousAccessWhenAuthenticationIsDeclaredOnControllerClass()
            throws Exception {
        Subject subject = bindSubject();
        when(subject.isAuthenticated()).thenReturn(false);

        assertThrows(
                UnauthenticatedException.class,
                () -> invoke(ClassSecuredController.class, "read"));
    }

    @Test
    void enforcesClassLevelRoleAfterAuthentication() throws Exception {
        Subject subject = bindSubject();
        when(subject.isAuthenticated()).thenReturn(true);
        doThrow(new UnauthorizedException())
                .when(subject)
                .checkRoles(Collections.singletonList("root"));

        assertThrows(
                UnauthorizedException.class,
                () -> invoke(ClassSecuredController.class, "read"));
    }

    @Test
    void allowsAuthenticatedSubjectWithRequiredClassLevelRole() throws Exception {
        Subject subject = bindSubject();
        when(subject.isAuthenticated()).thenReturn(true);
        when(subject.hasRole("root")).thenReturn(true);

        assertDoesNotThrow(() -> invoke(ClassSecuredController.class, "read"));
    }

    @Test
    void leavesUnannotatedControllerMethodsPublic() throws Exception {
        bindSubject();
        assertDoesNotThrow(() -> invoke(PublicController.class, "read"));
    }

    private Subject bindSubject() {
        Subject subject = mock(Subject.class);
        ThreadContext.bind(subject);
        return subject;
    }

    private void invoke(Class<?> controllerType, String methodName) throws Exception {
        Object controller = controllerType.getDeclaredConstructor().newInstance();
        HandlerMethod handler = new HandlerMethod(
                controller,
                controllerType.getMethod(methodName));
        interceptor.preHandle(null, null, handler);
    }

    @RequiresAuthentication
    @RequiresRoles("root")
    public static class ClassSecuredController {
        public void read() {
        }
    }

    public static class PublicController {
        public void read() {
        }
    }
}
