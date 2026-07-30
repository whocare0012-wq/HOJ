package top.hcode.hoj.controller.admin;

import org.apache.shiro.authz.annotation.RequiresRoles;
import org.junit.jupiter.api.Test;
import top.hcode.hoj.common.DailyFortuneType;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DailyCheckInAdminControllerSecurityTest {

    @Test
    void advicePoolApiRequiresRootRole() {
        assertRootOnly("getAdvicePools");
        assertRootOnly("updateAdvicePool", String.class,
                top.hcode.hoj.pojo.vo.DailyFortuneAdvicePoolVO.class);
        assertRootOnly("getAdminOverview");
        assertRootOnly("createFortune",
                top.hcode.hoj.pojo.vo.DailyFortuneConfigVO.class);
        assertRootOnly("updateFortune", Long.class,
                top.hcode.hoj.pojo.vo.DailyFortuneConfigVO.class);
        assertRootOnly("deleteFortune", Long.class);
        assertRootOnly("getFortuneAdvice", String.class);
        assertRootOnly("createAdvice", String.class, String.class,
                top.hcode.hoj.pojo.vo.DailyFortuneAdviceItemVO.class);
        assertRootOnly("updateAdvice", Long.class,
                top.hcode.hoj.pojo.vo.DailyFortuneAdviceItemVO.class);
        assertRootOnly("deleteAdvice", Long.class);
    }

    @Test
    void exposesExactlyTheSixSupportedFortuneTypes() {
        assertEquals(
                Arrays.asList(
                        "great_luck",
                        "medium_luck",
                        "small_luck",
                        "neutral",
                        "bad_luck",
                        "great_bad_luck"),
                Arrays.stream(DailyFortuneType.values())
                        .map(DailyFortuneType::getCode)
                        .collect(Collectors.toList()));
    }

    private void assertRootOnly(String methodName, Class<?>... parameterTypes) {
        try {
            RequiresRoles requiresRoles = DailyCheckInAdminController.class
                    .getMethod(methodName, parameterTypes)
                    .getAnnotation(RequiresRoles.class);
            assertNotNull(requiresRoles);
            assertArrayEquals(new String[]{"root"}, requiresRoles.value());
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }
}
