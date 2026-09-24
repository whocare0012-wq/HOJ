package top.hcode.hoj.manager.admin.user;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.test.util.ReflectionTestUtils;
import top.hcode.hoj.common.exception.StatusFailException;
import top.hcode.hoj.dao.user.*;
import top.hcode.hoj.pojo.dto.AdminEditUserDTO;
import top.hcode.hoj.pojo.entity.user.UserRole;
import top.hcode.hoj.utils.RedisUtils;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AdminUserEditingSafetyTest {
    UserInfoEntityService users = mock(UserInfoEntityService.class);
    UserRoleEntityService roles = mock(UserRoleEntityService.class);
    AdminUserManager target = new AdminUserManager();
    RecordingTransactions transactions = new RecordingTransactions();
    AdminUserManager manager;

    AdminUserEditingSafetyTest() {
        ReflectionTestUtils.setField(target, "userInfoEntityService", users);
        ReflectionTestUtils.setField(target, "userRoleEntityService", roles);
        ReflectionTestUtils.setField(target, "redisUtils", new RedisUtils());
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        manager = (AdminUserManager) factory.getProxy();
    }

    AdminEditUserDTO valid() {
        AdminEditUserDTO dto = new AdminEditUserDTO();
        dto.setUid("u1"); dto.setUsername("user"); dto.setType(1002);
        dto.setStatus(0); dto.setSetNewPwd(false);
        return dto;
    }

    @Test void missingControlFieldsAreValidationErrors() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertFalse(factory.getValidator().validate(new AdminEditUserDTO()).isEmpty());
            AdminEditUserDTO dto = valid();
            dto.setType(1008);
            assertTrue(factory.getValidator().validate(dto).isEmpty());
            dto.setType(1009);
            assertFalse(factory.getValidator().validate(dto).isEmpty());
        }
        assertThrows(StatusFailException.class, () -> manager.editUser(new AdminEditUserDTO()));
        verifyNoInteractions(users, roles);
    }

    @Test void acceptsTwentyEmojiAndRefreshesOnlyAfterCommit() throws Exception {
        AdminEditUserDTO dto = valid();
        dto.setTitleName(String.join("", java.util.Collections.nCopies(20, "👑")));
        when(users.update(any())).thenAnswer(call -> {
            verify(roles, never()).deleteCache(anyString(), anyBoolean());
            return true;
        });
        when(roles.getOne(any(), eq(false))).thenReturn(new UserRole().setRoleId(1002L));
        doAnswer(call -> { assertTrue(transactions.committed); return null; })
                .when(roles).deleteCache("u1", false);
        manager.editUser(dto);
        verify(roles).deleteCache("u1", false);
        assertFalse(transactions.rolledBack);
    }

    @Test void rejectsTwentyOneEmojiWithoutWrites() {
        AdminEditUserDTO dto = valid();
        dto.setTitleName(String.join("", java.util.Collections.nCopies(21, "👑")));
        assertThrows(StatusFailException.class, () -> manager.editUser(dto));
        verifyNoInteractions(users, roles);
    }

    @Test void roleWriteFailureRollsBackAndDoesNotInvalidateCaches() {
        when(users.update(any())).thenReturn(true);
        when(roles.getOne(any(), eq(false))).thenReturn(new UserRole().setRoleId(1003L));
        when(roles.updateById(any())).thenReturn(false);
        assertThrows(StatusFailException.class, () -> manager.editUser(valid()));
        assertTrue(transactions.rolledBack);
        assertFalse(transactions.committed);
        verify(roles, never()).deleteCache(anyString(), anyBoolean());
    }

    @Test void emptyNewPasswordIsRejected() {
        AdminEditUserDTO dto = valid(); dto.setSetNewPwd(true);
        assertThrows(StatusFailException.class, () -> manager.editUser(dto));
        verifyNoInteractions(users, roles);
    }

    static class RecordingTransactions extends AbstractPlatformTransactionManager {
        boolean committed, rolledBack;
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object transaction, TransactionDefinition definition) {}
        protected void doCommit(DefaultTransactionStatus status) { committed = true; }
        protected void doRollback(DefaultTransactionStatus status) { rolledBack = true; }
    }
}
