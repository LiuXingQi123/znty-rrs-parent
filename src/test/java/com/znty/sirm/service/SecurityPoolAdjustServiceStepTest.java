package com.znty.sirm.service;

import com.znty.sirm.mapper.SecurityPoolAdjustMapper;
import com.znty.sirm.mapper.InvestmentPoolMapper;
import com.znty.sirm.model.AdjustCheckContext;
import com.znty.sirm.model.FlowEdgeBo;
import com.znty.sirm.model.FlowNodeBo;
import com.znty.sirm.model.InvestmentPoolBo;
import com.znty.sirm.model.IpAdjustStepBo;
import com.znty.sirm.model.NodeApprovalConfigBo;
import com.znty.sirm.model.NodeApprovalHandlerBo;
import com.znty.sirm.model.PoolDto;
import com.znty.sirm.model.PoolPermissionBo;
import com.znty.sirm.model.SecurityInfoBo;
import com.znty.sirm.model.SecurityPoolAdjustReq;
import com.znty.sirm.model.SecurityPoolAdjustSubmitReq;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SecurityPoolAdjustServiceStepTest {

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

    @Test
    public void queryAdjustPoolListShouldSkipPermissionFilterForAdmin() {
        InvestmentPoolMapper mapper = mock(InvestmentPoolMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "investmentPoolMapper", mapper);
        when(mapper.queryPoolList()).thenReturn(Arrays.asList(
                buildPool(1L, null, "根库"),
                buildPool(2L, 1L, "一级库")));
        when(mapper.queryMutexRelationList()).thenReturn(Collections.emptyList());

        SecurityPoolAdjustReq req = new SecurityPoolAdjustReq();

        List<PoolDto> result = service.queryAdjustPoolList(req);

        assertThat(result).extracting(PoolDto::getId).containsExactly(1L, 2L);
        verify(mapper, never()).queryPermissionListByType(org.mockito.Matchers.anyString());
        verify(mapper, never()).queryUserRoleIdList(org.mockito.Matchers.anyLong());
    }

    @Test
    public void filterAdjustablePoolsByUserShouldKeepAncestors() {
        InvestmentPoolMapper mapper = mock(InvestmentPoolMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "investmentPoolMapper", mapper);
        List<InvestmentPoolBo> pools = Arrays.asList(
                buildPool(1L, null, "根库"),
                buildPool(2L, 1L, "信用债"),
                buildPool(3L, 2L, "一级库"),
                buildPool(4L, 1L, "专户产品"),
                buildPool(5L, 4L, "二级库"),
                buildPool(6L, 1L, "未授权库"));
        when(mapper.queryUserRoleIdList(2001L)).thenReturn(Collections.singletonList(20L));
        when(mapper.queryPermissionListByType("adjustable")).thenReturn(Arrays.asList(
                buildPermission(3L, "user", 2001L),
                buildPermission(5L, "role", 20L),
                buildPermission(6L, "user", 3001L)));

        List<InvestmentPoolBo> result = ReflectionTestUtils.invokeMethod(service, "filterAdjustablePoolsByUser", pools, 2001L);

        assertThat(result).extracting(InvestmentPoolBo::getId).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    public void createInitialStepsShouldCreatePendingStepForEachPreemptHandler() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);

        FlowNodeBo start = buildNode(1L, "start", "start", 1);
        FlowNodeBo approval = buildNode(2L, "approval", "approval", 2);
        NodeApprovalConfigBo config = buildConfig(20L, approval.getId(), "preempt");
        Object snapshot = buildSnapshot(
                Arrays.asList(start, approval),
                Arrays.asList(buildEdge(start.getId(), approval.getId())),
                Arrays.asList(config),
                buildHandlerMap(config.getId(), 5));

        ReflectionTestUtils.invokeMethod(service, "createInitialSteps", 100L, snapshot, "1001", "admin");

        ArgumentCaptor<IpAdjustStepBo> captor = ArgumentCaptor.forClass(IpAdjustStepBo.class);
        verify(mapper, times(6)).addAdjustStep(captor.capture());
        List<IpAdjustStepBo> steps = captor.getAllValues();

        assertThat(steps.get(0).getNodeType()).isEqualTo("start");
        assertThat(steps.get(0).getStepStatus()).isEqualTo("auto_completed");
        assertThat(steps.subList(1, 6)).extracting(IpAdjustStepBo::getStepStatus)
                .containsOnly("pending");
        assertThat(steps.subList(1, 6)).extracting(IpAdjustStepBo::getApprovalStrategy)
                .containsOnly("preempt");
        assertThat(steps.subList(1, 6)).extracting(IpAdjustStepBo::getHandlerId)
                .containsExactly("1", "2", "3", "4", "5");
    }

    @Test
    public void createInitialStepsShouldApproveInitiatorThenCreateConfiguredPendingSteps() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);

        FlowNodeBo start = buildNode(1L, "start", "start", 1);
        FlowNodeBo initiator = buildNode(2L, "initiator", "approval", 2);
        FlowNodeBo approval = buildNode(3L, "approval", "approval", 3);
        NodeApprovalConfigBo initiatorConfig = buildConfig(20L, initiator.getId(), "initiator");
        NodeApprovalConfigBo approvalConfig = buildConfig(30L, approval.getId(), "preempt");
        Object snapshot = buildSnapshot(
                Arrays.asList(start, initiator, approval),
                Arrays.asList(buildEdge(start.getId(), initiator.getId()), buildEdge(initiator.getId(), approval.getId())),
                Arrays.asList(initiatorConfig, approvalConfig),
                buildHandlerMap(approvalConfig.getId(), 2));

        ReflectionTestUtils.invokeMethod(service, "createInitialSteps", 100L, snapshot, "1001", "admin");

        ArgumentCaptor<IpAdjustStepBo> captor = ArgumentCaptor.forClass(IpAdjustStepBo.class);
        verify(mapper, times(4)).addAdjustStep(captor.capture());
        List<IpAdjustStepBo> steps = captor.getAllValues();

        assertThat(steps.get(1).getFlowNodeId()).isEqualTo(initiator.getId());
        assertThat(steps.get(1).getApprovalStrategy()).isEqualTo("initiator");
        assertThat(steps.get(1).getStepStatus()).isEqualTo("auto_completed");
        assertThat(steps.get(1).getHandlerId()).isEqualTo("1001");
        assertThat(steps.subList(2, 4)).extracting(IpAdjustStepBo::getStepStatus)
                .containsOnly("pending");
        assertThat(steps.subList(2, 4)).extracting(IpAdjustStepBo::getHandlerId)
                .containsExactly("1", "2");
    }

    @Test
    public void createInitialStepsShouldSkipSubmitterNodeEvenWhenOldConfigIsPreempt() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);

        FlowNodeBo start = buildNode(1L, "n1", "start", 1);
        FlowNodeBo submitter = buildNode(2L, "n2", "approval", 2);
        submitter.setLabel("研究员A发起");
        submitter.setSubLabel("researcher-a");
        FlowNodeBo reviewer = buildNode(3L, "n3", "approval", 3);
        reviewer.setLabel("研究员B复核");
        NodeApprovalConfigBo submitterConfig = buildConfig(20L, submitter.getId(), "preempt");
        NodeApprovalConfigBo reviewerConfig = buildConfig(30L, reviewer.getId(), "preempt");
        Object snapshot = buildSnapshot(
                Arrays.asList(start, submitter, reviewer),
                Arrays.asList(buildEdge(start.getId(), submitter.getId()), buildEdge(submitter.getId(), reviewer.getId())),
                Arrays.asList(submitterConfig, reviewerConfig),
                buildHandlerMap(reviewerConfig.getId(), 2));

        ReflectionTestUtils.invokeMethod(service, "createInitialSteps", 100L, snapshot, "1001", "admin");

        ArgumentCaptor<IpAdjustStepBo> captor = ArgumentCaptor.forClass(IpAdjustStepBo.class);
        verify(mapper, times(4)).addAdjustStep(captor.capture());
        List<IpAdjustStepBo> steps = captor.getAllValues();

        assertThat(steps.get(0).getFlowNodeId()).isEqualTo(start.getId());
        assertThat(steps.get(0).getHandlerId()).isEqualTo("1001");
        assertThat(steps.get(1).getFlowNodeId()).isEqualTo(submitter.getId());
        assertThat(steps.get(1).getStepStatus()).isEqualTo("auto_completed");
        assertThat(steps.get(1).getHandlerId()).isEqualTo("1001");
        assertThat(steps.subList(2, 4)).extracting(IpAdjustStepBo::getFlowNodeId)
                .containsOnly(reviewer.getId());
        assertThat(steps.subList(2, 4)).extracting(IpAdjustStepBo::getStepStatus)
                .containsOnly("pending");
        assertThat(steps.subList(2, 4)).extracting(IpAdjustStepBo::getHandlerId)
                .containsExactly("1", "2");
    }

    @Test
    public void createInitialStepsShouldCreateNextPendingStepWhenEdgeAfterSubmitterIsMissing() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);

        FlowNodeBo start = buildNode(1L, "n1", "start", 1);
        FlowNodeBo submitter = buildNode(2L, "n2", "approval", 2);
        submitter.setLabel("研究员A发起");
        submitter.setSubLabel("researcher-a");
        FlowNodeBo reviewer = buildNode(3L, "n3", "approval", 3);
        reviewer.setLabel("研究员B复核");
        NodeApprovalConfigBo submitterConfig = buildConfig(20L, submitter.getId(), "initiator");
        NodeApprovalConfigBo reviewerConfig = buildConfig(30L, reviewer.getId(), "preempt");
        Object snapshot = buildSnapshot(
                Arrays.asList(start, submitter, reviewer),
                Collections.singletonList(buildEdge(start.getId(), submitter.getId())),
                Arrays.asList(submitterConfig, reviewerConfig),
                buildHandlerMap(reviewerConfig.getId(), 2));

        ReflectionTestUtils.invokeMethod(service, "createInitialSteps", 100L, snapshot, "1001", "admin");

        ArgumentCaptor<IpAdjustStepBo> captor = ArgumentCaptor.forClass(IpAdjustStepBo.class);
        verify(mapper, times(4)).addAdjustStep(captor.capture());
        List<IpAdjustStepBo> steps = captor.getAllValues();

        assertThat(steps.get(1).getFlowNodeId()).isEqualTo(submitter.getId());
        assertThat(steps.get(1).getStepStatus()).isEqualTo("auto_completed");
        assertThat(steps.subList(2, 4)).extracting(IpAdjustStepBo::getFlowNodeId)
                .containsOnly(reviewer.getId());
        assertThat(steps.subList(2, 4)).extracting(IpAdjustStepBo::getStepStatus)
                .containsOnly("pending");
        assertThat(steps.subList(2, 4)).extracting(IpAdjustStepBo::getHandlerId)
                .containsExactly("1", "2");
    }

    @Test
    public void executeInboundSubmitShouldTreatMissingFlowAsDirect() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);

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

        Object shared = buildSubmitSharedData();

        ReflectionTestUtils.invokeMethod(service, "executeInboundSubmit", req, shared);

        ArgumentCaptor<com.znty.sirm.model.IpAdjustLogBo> captor =
                ArgumentCaptor.forClass(com.znty.sirm.model.IpAdjustLogBo.class);
        verify(mapper).addPoolStatus(captor.capture());
        verify(mapper, never()).addAdjustLog(org.mockito.Matchers.any(com.znty.sirm.model.IpAdjustLogBo.class));
        assertThat(captor.getValue().getAuditStatus()).isEqualTo("20");
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

    private FlowNodeBo buildNode(Long id, String nodeId, String nodeType, Integer sortOrder) {
        FlowNodeBo node = new FlowNodeBo();
        node.setId(id);
        node.setNodeId(nodeId);
        node.setNodeType(nodeType);
        node.setLabel(nodeId);
        node.setSortOrder(sortOrder);
        return node;
    }

    private FlowEdgeBo buildEdge(Long fromNodeId, Long toNodeId) {
        FlowEdgeBo edge = new FlowEdgeBo();
        edge.setFromNodeId(fromNodeId);
        edge.setToNodeId(toNodeId);
        return edge;
    }

    private InvestmentPoolBo buildPool(Long id, Long parentId, String poolName) {
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(id);
        pool.setParentId(parentId);
        pool.setPoolName(poolName);
        return pool;
    }

    private PoolPermissionBo buildPermission(Long poolId, String subjectType, Long subjectId) {
        PoolPermissionBo permission = new PoolPermissionBo();
        permission.setPoolId(poolId);
        permission.setPermissionType("adjustable");
        permission.setSubjectType(subjectType);
        permission.setSubjectId(subjectId);
        return permission;
    }

    private NodeApprovalConfigBo buildConfig(Long id, Long nodeId, String approvalStrategy) {
        NodeApprovalConfigBo config = new NodeApprovalConfigBo();
        config.setId(id);
        config.setNodeId(nodeId);
        config.setApprovalStrategy(approvalStrategy);
        return config;
    }

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
