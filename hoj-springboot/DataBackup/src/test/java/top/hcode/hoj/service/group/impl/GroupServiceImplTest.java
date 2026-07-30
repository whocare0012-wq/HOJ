package top.hcode.hoj.service.group.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.hcode.hoj.common.result.CommonResult;
import top.hcode.hoj.manager.group.GroupManager;
import top.hcode.hoj.pojo.vo.GroupVO;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroupServiceImplTest {

    private GroupManager groupManager;
    private GroupServiceImpl service;

    @BeforeEach
    void setUp() {
        groupManager = mock(GroupManager.class);
        service = new GroupServiceImpl();
        ReflectionTestUtils.setField(service, "groupManager", groupManager);
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultsMissingOnlyMineToFalse() {
        IPage<GroupVO> page = mock(IPage.class);
        when(groupManager.getGroupList(10, 1, null, null, false)).thenReturn(page);

        CommonResult<IPage<GroupVO>> result = service.getGroupList(10, 1, null, null, null);

        assertSame(page, result.getData());
        verify(groupManager).getGroupList(10, 1, null, null, false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void preservesExplicitOnlyMineTrue() {
        IPage<GroupVO> page = mock(IPage.class);
        when(groupManager.getGroupList(20, 2, "team", 1, true)).thenReturn(page);

        CommonResult<IPage<GroupVO>> result = service.getGroupList(20, 2, "team", 1, true);

        assertSame(page, result.getData());
        verify(groupManager).getGroupList(20, 2, "team", 1, true);
    }
}
