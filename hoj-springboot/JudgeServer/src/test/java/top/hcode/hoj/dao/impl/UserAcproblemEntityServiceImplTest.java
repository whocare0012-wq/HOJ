package top.hcode.hoj.dao.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.hcode.hoj.mapper.UserAcproblemMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAcproblemEntityServiceImplTest {

    private UserAcproblemMapper mapper;
    private UserAcproblemEntityServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(UserAcproblemMapper.class);
        service = new UserAcproblemEntityServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void returnsTrueWhenTheSolvedProblemRelationIsInserted() {
        when(mapper.insertIfAbsent("user-1", 12L, 34L)).thenReturn(1);

        assertTrue(service.saveIfAbsent("user-1", 12L, 34L));
        verify(mapper).insertIfAbsent("user-1", 12L, 34L);
    }

    @Test
    void returnsFalseWhenTheSolvedProblemRelationAlreadyExists() {
        when(mapper.insertIfAbsent("user-1", 12L, 35L)).thenReturn(0);

        assertFalse(service.saveIfAbsent("user-1", 12L, 35L));
        verify(mapper).insertIfAbsent("user-1", 12L, 35L);
    }
}
