package com.znty.rrs.service;

import com.znty.rrs.common.enums.FlowStatus;
import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.FlowDefinitionEvtBo;
import com.znty.rrs.entity.bo.FlowNodeEvtBo;
import com.znty.rrs.entity.bo.FlowVersionBo;
import com.znty.rrs.entity.bo.FlowVersionEvtBo;
import com.znty.rrs.entity.flow.CanvasEdgeDto;
import com.znty.rrs.entity.flow.CanvasNodeDto;
import com.znty.rrs.entity.flow.DesignerReq;
import com.znty.rrs.entity.flow.FlowReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FlowMapper;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程定义操作人审计测试。
 */
public class FlowServiceOperatorTest {

    /** 验证新建流程记录实际创建人和事件操作人。 */
    @Test
    public void addFlowShouldRecordActualOperator() {
        FlowMapper mapper = mock(FlowMapper.class);
        // 构建流程服务
        FlowService service = buildService(mapper);
        when(mapper.countByFlowKey("bond:test", null)).thenReturn(0);
        doAnswer(invocation -> {
            ((FlowDefinitionBo) invocation.getArguments()[0]).setId(1L);
            return 1;
        }).when(mapper).addFlowDefinition(any(FlowDefinitionBo.class));
        doAnswer(invocation -> {
            ((FlowVersionBo) invocation.getArguments()[0]).setId(2L);
            return 1;
        }).when(mapper).addFlowVersion(any(FlowVersionBo.class));

        FlowReq req = new FlowReq();
        req.setName("测试流程");
        req.setFlowKey("bond:test");
        req.setOperatorId(8L);
        service.addFlow(req);

        ArgumentCaptor<FlowDefinitionBo> definitionCaptor = ArgumentCaptor.forClass(FlowDefinitionBo.class);
        verify(mapper).addFlowDefinition(definitionCaptor.capture());
        assertThat(definitionCaptor.getValue().getCreatedBy()).isEqualTo(8L);
        assertThat(definitionCaptor.getValue().getUpdatedBy()).isEqualTo(8L);

        ArgumentCaptor<FlowVersionBo> versionCaptor = ArgumentCaptor.forClass(FlowVersionBo.class);
        verify(mapper).addFlowVersion(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getCreatedBy()).isEqualTo(8L);

        ArgumentCaptor<FlowDefinitionEvtBo> definitionEvtCaptor =
                ArgumentCaptor.forClass(FlowDefinitionEvtBo.class);
        verify(mapper).addFlowDefinitionEvt(definitionEvtCaptor.capture());
        assertThat(definitionEvtCaptor.getValue().getOpterId()).isEqualTo("8");

        ArgumentCaptor<FlowVersionEvtBo> versionEvtCaptor = ArgumentCaptor.forClass(FlowVersionEvtBo.class);
        verify(mapper).addFlowVersionEvt(versionEvtCaptor.capture());
        assertThat(versionEvtCaptor.getValue().getOpterId()).isEqualTo("8");
    }

    /** 验证保存草稿更新实际修改人并写入节点事件操作人。 */
    @Test
    public void editFlowDraftShouldRecordActualOperator() {
        FlowMapper mapper = mock(FlowMapper.class);
        // 构建流程服务
        FlowService service = buildService(mapper);
        // 构建流程定义
        FlowDefinitionBo definition = buildDefinition(1L, FlowStatus.DRAFT.getCode(), 8L);
        // 构建流程版本
        FlowVersionBo version = buildVersion(2L, FlowStatus.DRAFT.getCode(), 8L);
        when(mapper.queryFlowByIdForUpdate(1L)).thenReturn(definition);
        when(mapper.queryDraftFlowVersion(1L)).thenReturn(version);
        // 准备空的旧归一化数据
        stubEmptyNormalizedRows(mapper, 2L);

        DesignerReq req = new DesignerReq();
        req.setId(1L);
        req.setFlowKey("bond:test");
        req.setOperatorId(12L);
        // 构建开始节点
        req.setNodes(Collections.singletonList(buildNode("start", "start")));
        req.setEdges(Collections.emptyList());
        service.editFlowDraft(req);

        ArgumentCaptor<FlowDefinitionBo> definitionCaptor = ArgumentCaptor.forClass(FlowDefinitionBo.class);
        verify(mapper).editFlowDefinition(definitionCaptor.capture());
        assertThat(definitionCaptor.getValue().getCreatedBy()).isEqualTo(8L);
        assertThat(definitionCaptor.getValue().getUpdatedBy()).isEqualTo(12L);

        ArgumentCaptor<FlowVersionEvtBo> versionEvtCaptor = ArgumentCaptor.forClass(FlowVersionEvtBo.class);
        verify(mapper).addFlowVersionEvt(versionEvtCaptor.capture());
        assertThat(versionEvtCaptor.getValue().getOpterId()).isEqualTo("12");

        ArgumentCaptor<FlowNodeEvtBo> nodeEvtCaptor = ArgumentCaptor.forClass(FlowNodeEvtBo.class);
        verify(mapper).addFlowNodeEvt(nodeEvtCaptor.capture());
        assertThat(nodeEvtCaptor.getValue().getOpterId()).isEqualTo("12");
    }

    /** 验证发布流程记录实际发布人且保留版本创建人。 */
    @Test
    public void editFlowToPublishedShouldRecordActualPublisher() {
        FlowMapper mapper = mock(FlowMapper.class);
        // 构建流程服务
        FlowService service = buildService(mapper);
        // 构建流程定义
        FlowDefinitionBo definition = buildDefinition(1L, FlowStatus.DRAFT.getCode(), 8L);
        // 构建流程版本
        FlowVersionBo version = buildVersion(2L, FlowStatus.DRAFT.getCode(), 8L);
        when(mapper.queryFlowByIdForUpdate(1L)).thenReturn(definition);
        when(mapper.queryDraftFlowVersion(1L)).thenReturn(version);
        // 准备空的旧归一化数据
        stubEmptyNormalizedRows(mapper, 2L);

        DesignerReq req = new DesignerReq();
        req.setId(1L);
        req.setOperatorId(15L);
        // 构建开始和结束节点
        req.setNodes(Arrays.asList(buildNode("start", "start"), buildNode("end", "end")));
        // 构建提交连线
        req.setEdges(Collections.singletonList(buildEdge("edge1", "start", "end")));
        service.editFlowToPublished(req);

        ArgumentCaptor<FlowVersionBo> versionCaptor = ArgumentCaptor.forClass(FlowVersionBo.class);
        verify(mapper).editFlowVersion(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getCreatedBy()).isEqualTo(8L);
        assertThat(versionCaptor.getValue().getPublishedBy()).isEqualTo(15L);

        ArgumentCaptor<FlowDefinitionBo> definitionCaptor = ArgumentCaptor.forClass(FlowDefinitionBo.class);
        verify(mapper).editFlowDefinition(definitionCaptor.capture());
        assertThat(definitionCaptor.getValue().getUpdatedBy()).isEqualTo(15L);

        ArgumentCaptor<FlowVersionEvtBo> versionEvtCaptor = ArgumentCaptor.forClass(FlowVersionEvtBo.class);
        verify(mapper).addFlowVersionEvt(versionEvtCaptor.capture());
        assertThat(versionEvtCaptor.getValue().getOpterId()).isEqualTo("15");
    }

    /** 验证删除流程同步记录最后更新人。 */
    @Test
    public void deleteFlowShouldRecordActualOperator() {
        FlowMapper mapper = mock(FlowMapper.class);
        // 构建流程服务
        FlowService service = buildService(mapper);
        // 构建流程定义
        FlowDefinitionBo definition = buildDefinition(1L, FlowStatus.DRAFT.getCode(), 8L);
        when(mapper.queryFlowById(1L)).thenReturn(definition);

        FlowReq req = new FlowReq();
        req.setId(1L);
        req.setOperatorId(18L);
        service.deleteFlow(req);

        verify(mapper).deleteFlowLogical(eq(1L), eq(18L), any(Date.class));
        ArgumentCaptor<FlowDefinitionEvtBo> eventCaptor = ArgumentCaptor.forClass(FlowDefinitionEvtBo.class);
        verify(mapper).addFlowDefinitionEvt(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getUpdatedBy()).isEqualTo(18L);
        assertThat(eventCaptor.getValue().getOpterId()).isEqualTo("18");
    }

    /** 验证写操作缺少操作人时被阻断。 */
    @Test(expected = BizException.class)
    public void addFlowShouldRejectMissingOperator() {
        // 构建流程服务
        FlowService service = buildService(mock(FlowMapper.class));
        service.addFlow(new FlowReq());
    }

    /** 构建流程服务。 */
    private FlowService buildService(FlowMapper mapper) {
        FlowService service = new FlowService();
        ReflectionTestUtils.setField(service, "flowMapper", mapper);
        return service;
    }

    /** 构建流程定义。 */
    private FlowDefinitionBo buildDefinition(Long id, String status, Long createdBy) {
        FlowDefinitionBo definition = new FlowDefinitionBo();
        definition.setId(id);
        definition.setName("测试流程");
        definition.setFlowKey("bond:test");
        definition.setStatus(status);
        definition.setCreatedBy(createdBy);
        definition.setUpdatedBy(createdBy);
        return definition;
    }

    /** 构建流程版本。 */
    private FlowVersionBo buildVersion(Long id, String status, Long createdBy) {
        FlowVersionBo version = new FlowVersionBo();
        version.setId(id);
        version.setFlowId(1L);
        version.setFlowKey("bond:test");
        version.setVerNum(1);
        version.setStatus(status);
        version.setCreatedBy(createdBy);
        return version;
    }

    /** 构建画布节点。 */
    private CanvasNodeDto buildNode(String id, String type) {
        CanvasNodeDto node = new CanvasNodeDto();
        node.setId(id);
        node.setType(type);
        node.setLabel(type);
        return node;
    }

    /** 构建画布连线。 */
    private CanvasEdgeDto buildEdge(String id, String from, String to) {
        CanvasEdgeDto edge = new CanvasEdgeDto();
        edge.setId(id);
        edge.setFrom(from);
        edge.setTo(to);
        edge.setRouteAction("auto_process");
        return edge;
    }

    /** 为全量替换模式准备空的旧归一化数据。 */
    private void stubEmptyNormalizedRows(FlowMapper mapper, Long versionId) {
        when(mapper.queryFlowNodeListByVersionId(versionId)).thenReturn(Collections.emptyList());
        when(mapper.queryApprovalHandlerListByVersionId(versionId)).thenReturn(Collections.emptyList());
        when(mapper.queryApprovalConfigListByVersionId(versionId)).thenReturn(Collections.emptyList());
        when(mapper.queryAutoConfigListByVersionId(versionId)).thenReturn(Collections.emptyList());
        when(mapper.queryNotifyConfigListByVersionId(versionId)).thenReturn(Collections.emptyList());
        when(mapper.queryConditionConfigListByVersionId(versionId)).thenReturn(Collections.emptyList());
        when(mapper.queryFlowEdgeListByVersionId(versionId)).thenReturn(Collections.emptyList());
        when(mapper.queryCondRuleListByVersionId(versionId)).thenReturn(Collections.emptyList());
    }
}
