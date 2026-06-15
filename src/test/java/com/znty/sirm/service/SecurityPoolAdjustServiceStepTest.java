package com.znty.sirm.service;

import com.znty.sirm.mapper.SecurityPoolAdjustMapper;
import com.znty.sirm.mapper.InvestmentPoolMapper;
import com.znty.sirm.model.AdjustCheckContext;
import com.znty.sirm.model.FlowEdgeBo;
import com.znty.sirm.model.FlowNodeBo;
import com.znty.sirm.model.InvestmentPoolBo;
import com.znty.sirm.model.IpAdjustLogBo;
import com.znty.sirm.model.IpAdjustStepBo;
import com.znty.sirm.model.NodeApprovalConfigBo;
import com.znty.sirm.model.NodeApprovalHandlerBo;
import com.znty.sirm.model.PoolDto;
import com.znty.sirm.model.PoolPermissionBo;
import com.znty.sirm.model.SecurityInfoBo;
import com.znty.sirm.model.SecurityPoolAdjustReq;
import com.znty.sirm.model.SecurityPoolAdjustSubmitReq;
import com.znty.sirm.exception.BizException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SecurityPoolAdjustServiceStepTest 测试类。 */
public class SecurityPoolAdjustServiceStepTest {

    /** 验证 checkInConditionsShouldShowPendingProcessNodeLabel 测试场景。 */
    @Test
    public void checkInConditionsShouldShowPendingProcessNodeLabel() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setSecurityInfo(new SecurityInfoBo());
        ctx.setHasPendingProcess(true);
        ctx.setPendingProcessNodeLabel("研究员B复核");
        ctx.setCurrentPoolIds(Collections.<Long>emptySet());
        ctx.setRequestInPoolIds(Collections.<Long>emptySet());
        ctx.setTargetPoolRelations(Collections.<String, List<Long>>emptyMap());
        ctx.setPoolMap(Collections.<Long, InvestmentPoolBo>emptyMap());

        List<String> failures = service.checkInConditions(ctx);

        assertThat(failures).contains("证券存在进行中的调库流程（当前节点：研究员B复核），请等待流程结束后再发起调库");
    }

    /** 验证 queryAdjustPoolListShouldSkipPermissionFilterForAdmin 测试场景。 */
    @Test
    public void queryAdjustPoolListShouldSkipPermissionFilterForAdmin() {
        InvestmentPoolMapper mapper = mock(InvestmentPoolMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "investmentPoolMapper", mapper);
        when(mapper.queryPoolList()).thenReturn(Arrays.asList(
                // 构建投资池测试数据
                buildPool(1L, null, "根库"),
                // 构建投资池测试数据
                buildPool(2L, 1L, "一级库")));
        when(mapper.queryMutexRelationList()).thenReturn(Collections.emptyList());

        SecurityPoolAdjustReq req = new SecurityPoolAdjustReq();

        List<PoolDto> result = service.queryAdjustPoolList(req);

        assertThat(result).extracting(PoolDto::getId).containsExactly(1L, 2L);
        verify(mapper, never()).queryPermissionListByType(org.mockito.Matchers.anyString());
        verify(mapper, never()).queryUserRoleIdList(org.mockito.Matchers.anyLong());
    }

    /** 验证 filterAdjustablePoolsByUserShouldKeepAncestors 测试场景。 */
    @Test
    public void filterAdjustablePoolsByUserShouldKeepAncestors() {
        InvestmentPoolMapper mapper = mock(InvestmentPoolMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "investmentPoolMapper", mapper);
        List<InvestmentPoolBo> pools = Arrays.asList(
                // 构建投资池测试数据
                buildPool(1L, null, "根库"),
                // 构建投资池测试数据
                buildPool(2L, 1L, "信用债"),
                // 构建投资池测试数据
                buildPool(3L, 2L, "一级库"),
                // 构建投资池测试数据
                buildPool(4L, 1L, "专户产品"),
                // 构建投资池测试数据
                buildPool(5L, 4L, "二级库"),
                // 构建投资池测试数据
                buildPool(6L, 1L, "未授权库"));
        when(mapper.queryUserRoleIdList(2001L)).thenReturn(Collections.singletonList(20L));
        when(mapper.queryPermissionListByType("adjustable")).thenReturn(Arrays.asList(
                // 构建投资池权限测试数据
                buildPermission(3L, "user", 2001L),
                // 构建投资池权限测试数据
                buildPermission(5L, "role", 20L),
                // 构建投资池权限测试数据
                buildPermission(6L, "user", 3001L)));

        List<InvestmentPoolBo> result = ReflectionTestUtils.invokeMethod(service, "filterAdjustablePoolsByUser", pools, 2001L);

        assertThat(result).extracting(InvestmentPoolBo::getId).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    /** 验证 createInitialStepsShouldCreatePendingStepForEachPreemptHandler 测试场景。 */
    @Test
    public void createInitialStepsShouldCreatePendingStepForEachPreemptHandler() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);

        // 构建流程节点测试数据
        FlowNodeBo start = buildNode(1L, "start", "start", 1);
        // 构建流程节点测试数据
        FlowNodeBo approval = buildNode(2L, "approval", "approval", 2);
        // 构建审批配置测试数据
        NodeApprovalConfigBo config = buildConfig(20L, approval.getId(), "preempt");
        // 构建流程快照测试数据
        Object snapshot = buildSnapshot(
                Arrays.asList(start, approval),
                // 构建流程连线测试数据
                Arrays.asList(buildEdge(start.getId(), approval.getId())),
                Arrays.asList(config),
                // 构建审批处理人映射测试数据
                buildHandlerMap(config.getId(), 5));

        ReflectionTestUtils.invokeMethod(service, "createInitialSteps", 100L, null, snapshot, "1001", "admin");

        ArgumentCaptor<IpAdjustStepBo> captor = ArgumentCaptor.forClass(IpAdjustStepBo.class);
        verify(mapper, times(6)).addAdjustStep(captor.capture());
        List<IpAdjustStepBo> steps = captor.getAllValues();

        assertThat(steps.get(0).getNodeType()).isEqualTo("start");
        assertThat(steps.get(0).getStepStatus()).isEqualTo("auto_process");
        assertThat(steps.subList(1, 6)).extracting(IpAdjustStepBo::getStepStatus)
                .containsOnly("pending");
        assertThat(steps.subList(1, 6)).extracting(IpAdjustStepBo::getApprovalStrategy)
                .containsOnly("preempt");
        assertThat(steps.subList(1, 6)).extracting(IpAdjustStepBo::getHandlerId)
                .containsExactly("1", "2", "3", "4", "5");
    }

    /** 验证 createInitialStepsShouldApproveInitiatorThenCreateConfiguredPendingSteps 测试场景。 */
    @Test
    public void createInitialStepsShouldApproveInitiatorThenCreateConfiguredPendingSteps() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);

        // 构建流程节点测试数据
        FlowNodeBo start = buildNode(1L, "start", "start", 1);
        // 构建流程节点测试数据
        FlowNodeBo initiator = buildNode(2L, "initiator", "approval", 2);
        // 构建流程节点测试数据
        FlowNodeBo approval = buildNode(3L, "approval", "approval", 3);
        // 构建审批配置测试数据
        NodeApprovalConfigBo initiatorConfig = buildConfig(20L, initiator.getId(), "initiator");
        // 构建审批配置测试数据
        NodeApprovalConfigBo approvalConfig = buildConfig(30L, approval.getId(), "preempt");
        // 构建流程快照测试数据
        Object snapshot = buildSnapshot(
                Arrays.asList(start, initiator, approval),
                // 构建流程连线测试数据
                Arrays.asList(buildEdge(start.getId(), initiator.getId()), buildEdge(initiator.getId(), approval.getId())),
                Arrays.asList(initiatorConfig, approvalConfig),
                // 构建审批处理人映射测试数据
                buildHandlerMap(approvalConfig.getId(), 2));

        ReflectionTestUtils.invokeMethod(service, "createInitialSteps", 100L, null, snapshot, "1001", "admin");

        ArgumentCaptor<IpAdjustStepBo> captor = ArgumentCaptor.forClass(IpAdjustStepBo.class);
        verify(mapper, times(4)).addAdjustStep(captor.capture());
        List<IpAdjustStepBo> steps = captor.getAllValues();

        assertThat(steps.get(1).getFlowNodeId()).isEqualTo(initiator.getId());
        assertThat(steps.get(1).getApprovalStrategy()).isEqualTo("initiator");
        assertThat(steps.get(1).getStepStatus()).isEqualTo("submit");
        assertThat(steps.get(1).getHandlerId()).isEqualTo("1001");
        assertThat(steps.subList(2, 4)).extracting(IpAdjustStepBo::getStepStatus)
                .containsOnly("pending");
        assertThat(steps.subList(2, 4)).extracting(IpAdjustStepBo::getHandlerId)
                .containsExactly("1", "2");
    }

    /** 验证 createInitialStepsShouldSkipSubmitterNodeEvenWhenOldConfigIsPreempt 测试场景。 */
    @Test
    public void createInitialStepsShouldSkipSubmitterNodeEvenWhenOldConfigIsPreempt() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);

        // 构建流程节点测试数据
        FlowNodeBo start = buildNode(1L, "n1", "start", 1);
        // 构建流程节点测试数据
        FlowNodeBo submitter = buildNode(2L, "n2", "approval", 2);
        submitter.setLabel("研究员A发起");
        submitter.setSubLabel("researcher-a");
        // 构建流程节点测试数据
        FlowNodeBo reviewer = buildNode(3L, "n3", "approval", 3);
        reviewer.setLabel("研究员B复核");
        // 构建审批配置测试数据
        NodeApprovalConfigBo submitterConfig = buildConfig(20L, submitter.getId(), "preempt");
        // 构建审批配置测试数据
        NodeApprovalConfigBo reviewerConfig = buildConfig(30L, reviewer.getId(), "preempt");
        // 构建流程快照测试数据
        Object snapshot = buildSnapshot(
                Arrays.asList(start, submitter, reviewer),
                // 构建流程连线测试数据
                Arrays.asList(buildEdge(start.getId(), submitter.getId()), buildEdge(submitter.getId(), reviewer.getId())),
                Arrays.asList(submitterConfig, reviewerConfig),
                // 构建审批处理人映射测试数据
                buildHandlerMap(reviewerConfig.getId(), 2));

        ReflectionTestUtils.invokeMethod(service, "createInitialSteps", 100L, null, snapshot, "1001", "admin");

        ArgumentCaptor<IpAdjustStepBo> captor = ArgumentCaptor.forClass(IpAdjustStepBo.class);
        verify(mapper, times(4)).addAdjustStep(captor.capture());
        List<IpAdjustStepBo> steps = captor.getAllValues();

        assertThat(steps.get(0).getFlowNodeId()).isEqualTo(start.getId());
        assertThat(steps.get(0).getHandlerId()).isNull();
        assertThat(steps.get(1).getFlowNodeId()).isEqualTo(submitter.getId());
        assertThat(steps.get(1).getStepStatus()).isEqualTo("submit");
        assertThat(steps.get(1).getHandlerId()).isEqualTo("1001");
        assertThat(steps.subList(2, 4)).extracting(IpAdjustStepBo::getFlowNodeId)
                .containsOnly(reviewer.getId());
        assertThat(steps.subList(2, 4)).extracting(IpAdjustStepBo::getStepStatus)
                .containsOnly("pending");
        assertThat(steps.subList(2, 4)).extracting(IpAdjustStepBo::getHandlerId)
                .containsExactly("1", "2");
    }

    /** 验证 createInitialStepsShouldThrowWhenEdgeAfterSubmitterIsMissing 测试场景。 */
    @Test
    public void createInitialStepsShouldThrowWhenEdgeAfterSubmitterIsMissing() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);

        // 构建流程节点测试数据
        FlowNodeBo start = buildNode(1L, "n1", "start", 1);
        // 构建流程节点测试数据
        FlowNodeBo submitter = buildNode(2L, "n2", "approval", 2);
        submitter.setLabel("研究员A发起");
        submitter.setSubLabel("researcher-a");
        // 构建流程节点测试数据
        FlowNodeBo reviewer = buildNode(3L, "n3", "approval", 3);
        reviewer.setLabel("研究员B复核");
        // 构建审批配置测试数据
        NodeApprovalConfigBo submitterConfig = buildConfig(20L, submitter.getId(), "initiator");
        // 构建审批配置测试数据
        NodeApprovalConfigBo reviewerConfig = buildConfig(30L, reviewer.getId(), "preempt");
        // 构建流程快照测试数据
        Object snapshot = buildSnapshot(
                Arrays.asList(start, submitter, reviewer),
                // 构建流程连线测试数据
                Collections.singletonList(buildEdge(start.getId(), submitter.getId())),
                Arrays.asList(submitterConfig, reviewerConfig),
                // 构建审批处理人映射测试数据
                buildHandlerMap(reviewerConfig.getId(), 2));

        try {
            ReflectionTestUtils.invokeMethod(service, "createInitialSteps", 100L, null, snapshot, "1001", "admin");
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("缺少下一步连线");
            return;
        }
        throw new AssertionError("缺少下一步连线时应抛出流程配置异常");
    }

    /** 验证 executeInboundSubmitShouldTreatMissingFlowAsDirect 测试场景。 */
    @Test
    public void executeInboundSubmitShouldTreatMissingFlowAsDirect() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        doAnswer(invocation -> {
            IpAdjustLogBo bo = (IpAdjustLogBo) invocation.getArguments()[0];
            bo.setId(99L);
            return 1;
        }).when(mapper).addAdjustLog(any(IpAdjustLogBo.class));

        SecurityPoolAdjustSubmitReq req = new SecurityPoolAdjustSubmitReq();
        req.setSecurityCode("S001");
        req.setSecurityShortName("test");
        req.setSecurityType("bond");
        req.setAdjustType("manual");
        req.setAdjusterId("1001");
        req.setAdjusterName("admin");

        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        item.setAdjustMode("\u8c03\u5165");
        item.setTargetPoolId(10L);
        item.setTargetPoolName("pool");
        item.setPoolType("special_account");
        req.setItems(Collections.singletonList(item));

        // 构建调库提交共享数据测试数据
        Object shared = buildSubmitSharedData();

        List<Long> result = ReflectionTestUtils.invokeMethod(service, "executeInboundSubmit", req, shared);

        ArgumentCaptor<IpAdjustLogBo> logCaptor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        ArgumentCaptor<IpAdjustLogBo> statusCaptor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(mapper).addAdjustLog(logCaptor.capture());
        verify(mapper).addPoolStatus(statusCaptor.capture());
        assertThat(logCaptor.getValue().getAuditStatus()).isEqualTo("20");
        assertThat(statusCaptor.getValue().getAuditStatus()).isEqualTo("20");
        assertThat(result).containsExactly(99L);
    }

    /** 验证 executeOutboundSubmitShouldCreateLogWhenFlowIsDirect 测试场景。 */
    @Test
    public void executeOutboundSubmitShouldCreateLogWhenFlowIsDirect() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        doAnswer(invocation -> {
            IpAdjustLogBo bo = (IpAdjustLogBo) invocation.getArguments()[0];
            bo.setId(88L);
            return 1;
        }).when(mapper).addAdjustLog(any(IpAdjustLogBo.class));

        SecurityPoolAdjustSubmitReq req = new SecurityPoolAdjustSubmitReq();
        req.setSecurityCode("S001");
        req.setSecurityShortName("test");
        req.setSecurityType("bond");
        req.setAdjustType("manual");
        req.setAdjusterId("1001");
        req.setAdjusterName("admin");

        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        item.setAdjustMode("\u8c03\u51fa");
        item.setTargetPoolId(10L);
        item.setTargetPoolName("pool");
        item.setPoolType("special_account");
        req.setItems(Collections.singletonList(item));

        // 构建调库提交共享数据测试数据
        Object shared = buildSubmitSharedData();

        List<Long> result = ReflectionTestUtils.invokeMethod(service, "executeOutboundSubmit", req, shared);

        ArgumentCaptor<IpAdjustLogBo> captor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(mapper).addAdjustLog(captor.capture());
        verify(mapper).softDeletePoolStatus("S001", 10L);
        assertThat(captor.getValue().getAuditStatus()).isEqualTo("20");
        assertThat(result).containsExactly(88L);
    }

    private Object buildSnapshot(List<FlowNodeBo> nodes, List<FlowEdgeBo> edges,
                                 List<NodeApprovalConfigBo> configs,
                                 Map<Long, List<NodeApprovalHandlerBo>> handlerMap) throws Exception {
        Map<Long, FlowNodeBo> nodeMap = new LinkedHashMap<>();
        for (FlowNodeBo node : nodes) {
            nodeMap.put(node.getId(), node);
        }
        Map<Long, NodeApprovalConfigBo> configMap = new HashMap<>();
        for (NodeApprovalConfigBo config : configs) {
            configMap.put(config.getNodeId(), config);
        }
        Class<?> snapshotClass = Class.forName("com.znty.sirm.service.SecurityPoolAdjustService$FlowSnapshot");
        Constructor<?> constructor = snapshotClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(null, null, nodeMap, edges, configMap, handlerMap);
    }

    /** 构建调库提交共享数据测试数据。 */
    private Object buildSubmitSharedData() throws Exception {
        Class<?> sharedClass = Class.forName("com.znty.sirm.service.SecurityPoolAdjustService$SubmitSharedData");
        Constructor<?> constructor = sharedClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(
                new SecurityInfoBo(),
                new HashMap<Long, com.znty.sirm.model.InvestmentPoolBo>(),
                Collections.<Long>emptySet(),
                new HashMap<Long, Map<String, List<Long>>>(),
                false,
                false,
                false,
                new HashMap<Long, Object>());
    }

    /** 构建流程节点测试数据。 */
    private FlowNodeBo buildNode(Long id, String nodeId, String nodeType, Integer sortOrder) {
        FlowNodeBo node = new FlowNodeBo();
        node.setId(id);
        node.setNodeId(nodeId);
        node.setNodeType(nodeType);
        node.setLabel(nodeId);
        node.setSortOrder(sortOrder);
        return node;
    }

    /** 构建流程连线测试数据。 */
    private FlowEdgeBo buildEdge(Long fromNodeId, Long toNodeId) {
        FlowEdgeBo edge = new FlowEdgeBo();
        edge.setFromNodeId(fromNodeId);
        edge.setToNodeId(toNodeId);
        return edge;
    }

    /** 构建投资池测试数据。 */
    private InvestmentPoolBo buildPool(Long id, Long parentId, String poolName) {
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(id);
        pool.setParentId(parentId);
        pool.setPoolName(poolName);
        return pool;
    }

    /** 构建投资池权限测试数据。 */
    private PoolPermissionBo buildPermission(Long poolId, String subjectType, Long subjectId) {
        PoolPermissionBo permission = new PoolPermissionBo();
        permission.setPoolId(poolId);
        permission.setPermissionType("adjustable");
        permission.setSubjectType(subjectType);
        permission.setSubjectId(subjectId);
        return permission;
    }

    /** 构建审批配置测试数据。 */
    private NodeApprovalConfigBo buildConfig(Long id, Long nodeId, String approvalStrategy) {
        NodeApprovalConfigBo config = new NodeApprovalConfigBo();
        config.setId(id);
        config.setNodeId(nodeId);
        config.setApprovalStrategy(approvalStrategy);
        return config;
    }

    /** 构建审批处理人映射测试数据。 */
    private Map<Long, List<NodeApprovalHandlerBo>> buildHandlerMap(Long configId, int count) {
        Map<Long, List<NodeApprovalHandlerBo>> map = new HashMap<>();
        List<NodeApprovalHandlerBo> handlers = new ArrayList<>();
        for (long i = 1; i <= count; i++) {
            NodeApprovalHandlerBo handler = new NodeApprovalHandlerBo();
            handler.setApprovalConfigId(configId);
            handler.setSubjectType("user");
            handler.setSubjectId(i);
            handler.setSubjectName("user" + i);
            handlers.add(handler);
        }
        map.put(configId, handlers);
        return map;
    }
}
