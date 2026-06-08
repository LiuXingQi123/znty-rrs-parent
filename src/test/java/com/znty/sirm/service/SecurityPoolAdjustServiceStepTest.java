package com.znty.sirm.service;

import com.znty.sirm.mapper.SecurityPoolAdjustMapper;
import com.znty.sirm.model.FlowEdgeBo;
import com.znty.sirm.model.FlowNodeBo;
import com.znty.sirm.model.IpAdjustStepBo;
import com.znty.sirm.model.NodeApprovalConfigBo;
import com.znty.sirm.model.NodeApprovalHandlerBo;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class SecurityPoolAdjustServiceStepTest {

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
        assertThat(steps.get(1).getStepStatus()).isEqualTo("approved");
        assertThat(steps.get(1).getHandlerId()).isEqualTo("1001");
        assertThat(steps.subList(2, 4)).extracting(IpAdjustStepBo::getStepStatus)
                .containsOnly("pending");
        assertThat(steps.subList(2, 4)).extracting(IpAdjustStepBo::getHandlerId)
                .containsExactly("1", "2");
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
