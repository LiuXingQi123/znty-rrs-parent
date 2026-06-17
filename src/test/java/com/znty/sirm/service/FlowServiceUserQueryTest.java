package com.znty.sirm.service;

import com.znty.sirm.mapper.FlowMapper;
import com.znty.sirm.model.FlowReq;
import com.znty.sirm.model.RoleBo;
import com.znty.sirm.model.UserBo;
import com.znty.sirm.model.UserDto;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程定义人员选择相关测试
 */
public class FlowServiceUserQueryTest {

    /** 验证 queryUserListShouldIncludeDescendantRoles 测试场景。 */
    @Test
    public void queryUserListShouldIncludeDescendantRoles() {
        FlowMapper flowMapper = mock(FlowMapper.class);
        FlowService flowService = new FlowService();
        ReflectionTestUtils.setField(flowService, "flowMapper", flowMapper);

        // 构建角色测试数据
        RoleBo parent = buildRole(1L, null);
        // 构建角色测试数据
        RoleBo child = buildRole(2L, 1L);
        // 构建角色测试数据
        RoleBo other = buildRole(3L, null);
        when(flowMapper.queryRoleList()).thenReturn(Arrays.asList(parent, child, other));

        UserBo user = new UserBo();
        user.setId(10L);
        user.setName("研究员2");
        user.setUserName("yanjiuyuan2");
        user.setRoleName("role-child");
        when(flowMapper.queryUserList(Arrays.asList(1L, 2L), null)).thenReturn(Arrays.asList(user));

        FlowReq req = new FlowReq();
        req.setRoleId(1L);
        List<UserDto> result = flowService.queryUserList(req);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(10L);
        assertThat(result.get(0).getUserName()).isEqualTo("研究员2");
        verify(flowMapper).queryUserList(eq(Arrays.asList(1L, 2L)), isNull(String.class));
    }

    /** 构建角色测试数据。 */
    private RoleBo buildRole(Long id, Long parentId) {
        RoleBo role = new RoleBo();
        role.setId(id);
        role.setParentId(parentId);
        role.setName("role" + id);
        return role;
    }
}
