package com.znty.rrs.service;

import com.znty.rrs.mapper.FlowMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.entity.flow.CanvasEdgeDto;
import com.znty.rrs.entity.flow.CanvasNodeDto;
import com.znty.rrs.entity.flow.FlowDto;
import com.znty.rrs.entity.flow.FlowReq;
import com.znty.rrs.entity.bo.RoleBo;
import com.znty.rrs.entity.bo.UserBo;
import com.znty.rrs.entity.bo.FlowVersionBo;
import com.znty.rrs.entity.common.UserDto;
import java.util.Arrays;
import java.util.Collections;
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

    /** 验证 initiator 节点不能同时配置 submit 和 resubmit 出边。 */
    @Test
    public void validateBeforePublishShouldRejectInitiatorWithSubmitAndResubmit() {
        FlowService flowService = new FlowService();
        CanvasNodeDto start = buildNode("n1", "start", null);
        CanvasNodeDto initiator = buildNode("n2", "approval", "initiator");
        CanvasNodeDto end = buildNode("n3", "end", null);
        try {
            ReflectionTestUtils.invokeMethod(flowService, "validateBeforePublish",
                    Arrays.asList(start, initiator, end),
                    Arrays.asList(buildEdge("n1", "n2", null),
                            buildEdge("n2", "n3", "submit"),
                            buildEdge("n2", "n3", "resubmit")));
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("不能同时配置提交和重新提交出边");
            return;
        }
        throw new AssertionError("initiator 同时配置 submit 和 resubmit 时应发布失败");
    }

    /** 验证非 initiator 节点不能配置 submit 出边。 */
    @Test
    public void validateBeforePublishShouldRejectSubmitRouteOnNormalApproval() {
        FlowService flowService = new FlowService();
        CanvasNodeDto start = buildNode("n1", "start", null);
        CanvasNodeDto approval = buildNode("n2", "approval", "preempt");
        CanvasNodeDto end = buildNode("n3", "end", null);
        try {
            ReflectionTestUtils.invokeMethod(flowService, "validateBeforePublish",
                    Arrays.asList(start, approval, end),
                    Arrays.asList(buildEdge("n1", "n2", null), buildEdge("n2", "n3", "submit")));
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("提交动作只能配置在发起人节点");
            return;
        }
        throw new AssertionError("普通审批节点配置 submit 时应发布失败");
    }

    /** 验证非 initiator 节点不能配置 resubmit 出边。 */
    @Test
    public void validateBeforePublishShouldRejectResubmitRouteOnNormalApproval() {
        FlowService flowService = new FlowService();
        CanvasNodeDto start = buildNode("n1", "start", null);
        CanvasNodeDto approval = buildNode("n2", "approval", "preempt");
        CanvasNodeDto end = buildNode("n3", "end", null);
        try {
            ReflectionTestUtils.invokeMethod(flowService, "validateBeforePublish",
                    Arrays.asList(start, approval, end),
                    Arrays.asList(buildEdge("n1", "n2", null), buildEdge("n2", "n3", "resubmit")));
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("重新提交动作只能配置在发起人节点");
            return;
        }
        throw new AssertionError("普通审批节点配置 resubmit 时应发布失败");
    }

    /** 验证 initiator 节点不能配置 approve 出边。 */
    @Test
    public void validateBeforePublishShouldRejectApproveRouteOnInitiator() {
        FlowService flowService = new FlowService();
        CanvasNodeDto start = buildNode("n1", "start", null);
        CanvasNodeDto initiator = buildNode("n2", "approval", "initiator");
        CanvasNodeDto end = buildNode("n3", "end", null);
        try {
            ReflectionTestUtils.invokeMethod(flowService, "validateBeforePublish",
                    Arrays.asList(start, initiator, end),
                    Arrays.asList(buildEdge("n1", "n2", null), buildEdge("n2", "n3", "approve")));
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("不能配置通过出边");
            return;
        }
        throw new AssertionError("initiator 配置 approve 时应发布失败");
    }

    /** 验证任意连线都必须配置流转动作。 */
    @Test
    public void validateBeforePublishShouldRejectRouteWithoutAction() {
        FlowService flowService = new FlowService();
        CanvasNodeDto start = buildNode("n1", "start", null);
        CanvasNodeDto approval = buildNode("n2", "approval", "preempt");
        CanvasNodeDto modify = buildNode("n3", "approval", "initiator");
        CanvasNodeDto end = buildNode("n4", "end", null);
        try {
            ReflectionTestUtils.invokeMethod(flowService, "validateBeforePublish",
                    Arrays.asList(start, approval, modify, end),
                    Arrays.asList(buildEdge("n1", "n2", null),
                            buildEdge("n2", "n3", null),
                            buildEdge("n2", "n4", "approve"),
                            buildEdge("n3", "n2", "resubmit"),
                            buildEdge("n3", "n4", "reject")));
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("必须配置流转动作");
            return;
        }
        throw new AssertionError("任意连线缺少 routeAction 时应发布失败");
    }

    /** 验证修改节点允许配置 resubmit 和 reject 出边。 */
    @Test
    public void validateBeforePublishShouldAllowModifyNodeWithResubmitAndReject() {
        FlowService flowService = new FlowService();
        CanvasNodeDto start = buildNode("n1", "start", null);
        CanvasNodeDto approval = buildNode("n2", "approval", "preempt");
        CanvasNodeDto modify = buildNode("n3", "approval", "initiator");
        CanvasNodeDto end = buildNode("n4", "end", null);

        ReflectionTestUtils.invokeMethod(flowService, "validateBeforePublish",
                Arrays.asList(start, approval, modify, end),
                Arrays.asList(buildEdge("n1", "n2", "auto"),
                        buildEdge("n2", "n3", "reject"),
                        buildEdge("n2", "n4", "approve"),
                        buildEdge("n3", "n2", "resubmit"),
                        buildEdge("n3", "n4", "reject")));
    }

    /** 验证流程详情保留画布连线的流转动作。 */
    @Test
    public void toDraftInfoShouldKeepRouteActions() {
        FlowService flowService = new FlowService();
        FlowVersionBo version = new FlowVersionBo();
        version.setId(1L);
        version.setVerNum(1);
        version.setCanvasNodes("[]");
        version.setCanvasEdges("[{\"id\":\"e1\",\"from\":\"n1\",\"to\":\"n2\",\"routeAction\":\"auto\"}"
                + ",{\"id\":\"e2\",\"from\":\"n2\",\"to\":\"n3\",\"routeAction\":\"submit\"}"
                + ",{\"id\":\"e4\",\"from\":\"n4\",\"to\":\"n3\",\"routeAction\":\"resubmit\"}"
                + ",{\"id\":\"e9\",\"from\":\"n6\",\"to\":\"n7\",\"routeAction\":\"auto\"}]");

        FlowDto.DraftInfo draft = ReflectionTestUtils.invokeMethod(flowService, "toDraftInfo", version);
        JsonNode edges = (JsonNode) draft.getEdges();

        assertThat(edges.size()).isEqualTo(4);
        assertThat(edges.get(0).path("routeAction").asText()).isEqualTo("auto");
        assertThat(edges.get(1).path("routeAction").asText()).isEqualTo("submit");
        assertThat(edges.get(2).path("routeAction").asText()).isEqualTo("resubmit");
        assertThat(edges.get(3).path("routeAction").asText()).isEqualTo("auto");
    }

    /** 构建角色测试数据。 */
    private RoleBo buildRole(Long id, Long parentId) {
        RoleBo role = new RoleBo();
        role.setId(id);
        role.setParentId(parentId);
        role.setName("role" + id);
        return role;
    }

    /** 构建画布节点测试数据。 */
    private CanvasNodeDto buildNode(String id, String type, String approvalStrategy) {
        CanvasNodeDto node = new CanvasNodeDto();
        node.setId(id);
        node.setType(type);
        node.setLabel(id);
        node.setApprovalStrategy(approvalStrategy);
        node.setApprovalPersons(Collections.emptyList());
        return node;
    }

    /** 构建画布连线测试数据。 */
    private CanvasEdgeDto buildEdge(String from, String to, String routeAction) {
        CanvasEdgeDto edge = new CanvasEdgeDto();
        edge.setFrom(from);
        edge.setTo(to);
        edge.setLabel(from + "-" + to);
        edge.setRouteAction(routeAction);
        edge.setCondRules(Collections.emptyList());
        return edge;
    }
}
