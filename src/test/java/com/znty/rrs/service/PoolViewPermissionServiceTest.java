package com.znty.rrs.service;

import com.znty.rrs.common.enums.PermissionType;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.common.CommonReq;
import com.znty.rrs.entity.common.PoolTreeDto;
import com.znty.rrs.mapper.CommonMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 投资池可查看权限解析与树过滤测试。 */
public class PoolViewPermissionServiceTest {

    /** 验证本人权限与角色权限均能转换为可查看池集合。 */
    @Test
    public void queryPermittedPoolIdsShouldMergeUserAndRolePermissions() {
        InvestmentPoolMapper mapper = mock(InvestmentPoolMapper.class);
        InvestmentPoolService service = new InvestmentPoolService();
        ReflectionTestUtils.setField(service, "investmentPoolMapper", mapper);
        when(mapper.queryUserRoleIdList(2L)).thenReturn(Collections.singletonList(20L));
        when(mapper.queryPermissionListByType(PermissionType.VIEWABLE.getCode())).thenReturn(Arrays.asList(
                permission(15L, "user", 2L), permission(16L, "role", 20L), permission(17L, "user", 3L)));

        Set<Long> result = service.queryPermittedPoolIdsByUser("2", PermissionType.VIEWABLE.getCode());

        assertThat(result).containsOnly(15L, 16L);
    }

    /** 验证管理员不限制投资池范围。 */
    @Test
    public void queryPermittedPoolIdsShouldNotRestrictAdministrator() {
        InvestmentPoolService service = new InvestmentPoolService();

        assertThat(service.queryPermittedPoolIdsByUser("1", PermissionType.VIEWABLE.getCode())).isNull();
    }

    /** 验证授权叶子池过滤后保留完整祖先路径。 */
    @Test
    public void queryPoolTreeListShouldKeepAuthorizedPoolAncestors() {
        CommonMapper mapper = mock(CommonMapper.class);
        InvestmentPoolService poolService = mock(InvestmentPoolService.class);
        CommonService service = new CommonService();
        ReflectionTestUtils.setField(service, "commonMapper", mapper);
        ReflectionTestUtils.setField(service, "investmentPoolService", poolService);
        CommonReq req = new CommonReq();
        req.setCurrentUserId("2");
        req.setPermissionType(PermissionType.VIEWABLE.getCode());
        PoolTreeDto root = node(1L, null);
        PoolTreeDto allowed = node(15L, 1L);
        PoolTreeDto denied = node(16L, 1L);
        when(mapper.queryPoolTreeList(req)).thenReturn(Arrays.asList(root, allowed, denied));
        when(poolService.queryPermittedPoolIdsByUser("2", PermissionType.VIEWABLE.getCode()))
                .thenReturn(Collections.singleton(15L));

        List<PoolTreeDto> result = service.queryPoolTreeList(req);

        assertThat(result).extracting(PoolTreeDto::getId).containsExactly(1L, 15L);
    }

    /** 构建权限记录。 */
    private PoolPermissionBo permission(Long poolId, String handlerType, Long handlerId) {
        PoolPermissionBo permission = new PoolPermissionBo();
        permission.setPoolId(poolId);
        permission.setHandlerType(handlerType);
        permission.setHandlerId(handlerId);
        return permission;
    }

    /** 构建投资池树节点。 */
    private PoolTreeDto node(Long id, Long parentId) {
        PoolTreeDto node = new PoolTreeDto();
        node.setId(id);
        node.setParentId(parentId);
        return node;
    }
}
