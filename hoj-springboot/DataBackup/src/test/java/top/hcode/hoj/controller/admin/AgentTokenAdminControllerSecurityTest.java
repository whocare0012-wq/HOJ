package top.hcode.hoj.controller.admin;

import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentTokenAdminControllerSecurityTest {

    @Test
    void everyTokenManagementEndpointIsRootOnly() {
        Class<AgentTokenAdminController> type = AgentTokenAdminController.class;
        assertNotNull(type.getAnnotation(RequiresAuthentication.class));
        assertArrayEquals(new String[]{"root"}, type.getAnnotation(RequiresRoles.class).value());
        for (Method method : type.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            RequiresRoles roles = method.getAnnotation(RequiresRoles.class);
            assertNotNull(roles, method.getName());
            assertArrayEquals(new String[]{"root"}, roles.value(), method.getName());
        }
    }
}
