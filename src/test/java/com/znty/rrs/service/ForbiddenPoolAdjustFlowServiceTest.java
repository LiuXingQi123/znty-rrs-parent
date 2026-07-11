package com.znty.rrs.service;

import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.bo.FlowVersionBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpAdjustStepBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.securitypooladjustflow.SecurityPoolAdjustAuditReq;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.ForbiddenPoolAdjustMapper;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 禁投池主体调整审批服务测试。 */
public class ForbiddenPoolAdjustFlowServiceTest {

    /** 验证审批最终通过后主体和实际变化的旗下债券一并入池。 */
    @Test
    public void finishAdjustBatchShouldSyncCompanyAndChangedBonds() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        SysAttachmentService attachmentService = mock(SysAttachmentService.class);
        InvestmentPoolService poolService = mock(InvestmentPoolService.class);
        ForbiddenPoolAdjustFlowService service = new ForbiddenPoolAdjustFlowService();
        ReflectionTestUtils.setField(service, "forbiddenPoolAdjustMapper", mapper);
        ForbiddenPoolAdjustService adjustService = mock(ForbiddenPoolAdjustService.class);
        ReflectionTestUtils.setField(service, "forbiddenPoolAdjustService", adjustService);
        ReflectionTestUtils.setField(service, "sysAttachmentService", attachmentService);
        ReflectionTestUtils.setField(service, "investmentPoolService", poolService);
        IpAdjustLogBo companyLog = buildCompanyLog();
        when(mapper.queryAdjustLogListForAudit(10L, "COMPANY202606281001"))
                .thenReturn(Collections.singletonList(companyLog));
        when(mapper.editActiveAdjustLogAuditStatus(10L, "COMPANY202606281001", "20")).thenReturn(1);
        when(poolService.queryPoolFullNameMap()).thenReturn(Collections.<Long, String>emptyMap());
        when(attachmentService.queryHandCreditReportAttachments(10L)).thenReturn(Collections.emptyList());
        IpAdjustStepBo step = new IpAdjustStepBo();
        step.setAdjustLogId(10L);
        step.setAdjustBatchNo("COMPANY202606281001");

        ReflectionTestUtils.invokeMethod(service, "finishAdjustBatch", step);

        verify(adjustService).recheckBeforeFinalApproval(Collections.singletonList(companyLog));
        verify(adjustService).applyPoolStatusChanges(Collections.singletonList(companyLog));
        verify(mapper, never()).addAdjustStep(any(IpAdjustStepBo.class));
    }

    /** 验证中间审核节点通过后仍保持流程中状态。 */
    @Test
    public void submitAdjustAuditShouldKeepProcessingStatusForMiddleApprovalNode() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        FlowMapper flowMapper = mock(FlowMapper.class);
        // 构建流程审批服务实例测试数据
        ForbiddenPoolAdjustFlowService service = buildService(mapper, flowMapper);
        // 构建部门负责人审核节点测试数据
        IpAdjustStepBo step = buildPendingStep(10L, "3", "研究员2");
        step.setApprovalStrategy("preempt");
        step.setNodeLabel("部门负责人审核");
        // 构建审批请求测试数据
        SecurityPoolAdjustAuditReq req = buildReq(10L, "3", "研究员2", "同意");
        when(mapper.queryAdjustStepById(10L)).thenReturn(step);
        when(mapper.editAdjustStepProcess(10L, "approve", "approve", "同意")).thenReturn(1);
        when(mapper.queryAdjustLogListForAudit(1L, "BATCH001")).thenReturn(Collections.singletonList(buildLog("00", "2")));
        // 构建当前审核节点测试数据
        FlowNodeBo currentNode = buildFlowNode(10103L, "n3", "approval", "部门负责人审核");
        // 构建后续审核节点测试数据
        FlowNodeBo nextNode = buildFlowNode(10104L, "n4", "approval", "风控负责人审核");
        // 构建通过流转连线测试数据
        FlowEdgeBo edge = buildFlowEdge(10103L, 10104L, "approve");
        FlowVersionBo version = new FlowVersionBo();
        version.setId(1L);
        when(flowMapper.queryFlowNodeById(10103L)).thenReturn(currentNode);
        when(flowMapper.queryFlowVersionById(1L)).thenReturn(version);
        when(flowMapper.queryFlowNodeListByVersionId(1L)).thenReturn(Arrays.asList(currentNode, nextNode));
        when(flowMapper.queryFlowEdgeListByVersionId(1L)).thenReturn(Collections.singletonList(edge));
        when(flowMapper.queryCondRuleListByVersionId(1L)).thenReturn(Collections.emptyList());
        when(flowMapper.queryApprovalConfigListByVersionId(1L)).thenReturn(Collections.emptyList());
        when(flowMapper.queryApprovalHandlerListByVersionId(1L)).thenReturn(Collections.emptyList());

        service.submitAdjustAudit(req);

        verify(mapper).editAdjustLogAuditStatus(1L, "BATCH001", "00");
        verify(mapper, never()).editAdjustLogAuditStatus(1L, "BATCH001", "20");
    }

    /** 验证修改节点提交时按 resubmit 连线重新进入流程。 */
    @Test
    public void submitAdjustAuditShouldRouteModifySubmitByResubmitAction() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        FlowMapper flowMapper = mock(FlowMapper.class);
        // 构建流程审批服务实例测试数据
        ForbiddenPoolAdjustFlowService service = buildService(mapper, flowMapper);
        // 构建发起人修改节点待处理步骤测试数据
        IpAdjustStepBo step = buildPendingStep(10L, "2", "研究员1");
        step.setNodeLabel("流程发起人修改");
        step.setApprovalStrategy("initiator");
        // 构建发起人审批请求测试数据
        SecurityPoolAdjustAuditReq req = buildReq(10L, "2", "研究员1", "已修改");
        when(mapper.queryAdjustStepById(10L)).thenReturn(step);
        when(mapper.editAdjustStepProcess(10L, "submit", "submit", "已修改")).thenReturn(1);
        when(mapper.queryAdjustLogListForAudit(1L, "BATCH001")).thenReturn(Collections.singletonList(buildLog("11", "2")));
        // 构建流程快照：修改节点 -> 复核节点（避免流程直接结束触发落地）
        FlowNodeBo modifyNode = buildFlowNode(10103L, "n4", "approval", "流程发起人修改");
        FlowNodeBo reviewerNode = buildFlowNode(10104L, "n3", "approval", "研究员B复核");
        FlowEdgeBo edge = buildFlowEdge(10103L, 10104L, "resubmit");
        FlowVersionBo version = new FlowVersionBo();
        version.setId(1L);
        when(flowMapper.queryFlowNodeById(10103L)).thenReturn(modifyNode);
        when(flowMapper.queryFlowVersionById(1L)).thenReturn(version);
        when(flowMapper.queryFlowNodeListByVersionId(1L)).thenReturn(Arrays.asList(modifyNode, reviewerNode));
        when(flowMapper.queryFlowEdgeListByVersionId(1L)).thenReturn(Collections.singletonList(edge));
        when(flowMapper.queryCondRuleListByVersionId(1L)).thenReturn(Collections.emptyList());
        when(flowMapper.queryApprovalConfigListByVersionId(1L)).thenReturn(Collections.emptyList());
        when(flowMapper.queryApprovalHandlerListByVersionId(1L)).thenReturn(Collections.emptyList());

        service.submitAdjustAudit(req);

        verify(mapper).editAdjustStepProcess(10L, "submit", "submit", "已修改");
        verify(mapper).editAdjustLogAuditStatus(1L, "BATCH001", "00");
    }

    /** 验证驳回修改重新提交时按目标池要求校验内部报告来源。 */
    @Test
    public void modifySubmitShouldValidateInternalReportSources() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        FlowMapper flowMapper = mock(FlowMapper.class);
        SysAttachmentService attachmentService = mock(SysAttachmentService.class);
        InvestmentPoolService poolService = mock(InvestmentPoolService.class);
        ForbiddenPoolAdjustFlowService service = new ForbiddenPoolAdjustFlowService();
        ReflectionTestUtils.setField(service, "forbiddenPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "flowMapper", flowMapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", attachmentService);
        ReflectionTestUtils.setField(service, "investmentPoolService", poolService);
        IpAdjustLogBo log = buildCompanyLog();
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(15L);
        pool.setInReportRestriction("internal");
        when(mapper.queryAdjustLogListForAudit(10L, "COMPANY202606281001"))
                .thenReturn(Collections.singletonList(log));
        when(poolService.queryPoolBoList()).thenReturn(Collections.singletonList(pool));
        IpAdjustStepBo step = new IpAdjustStepBo();
        step.setAdjustLogId(10L);
        step.setAdjustBatchNo("COMPANY202606281001");
        step.setNodeLabel("流程发起人修改");
        step.setApprovalStrategy("initiator");
        step.setFlowNodeId(10103L);
        FlowNodeBo modifyNode = buildFlowNode(10103L, "n4", "approval", "流程发起人修改");
        FlowNodeBo reviewerNode = buildFlowNode(10104L, "n3", "approval", "研究员复核");
        FlowEdgeBo edge = buildFlowEdge(10103L, 10104L, "resubmit");
        FlowVersionBo version = new FlowVersionBo();
        version.setId(1L);
        when(flowMapper.queryFlowNodeById(10103L)).thenReturn(modifyNode);
        when(flowMapper.queryFlowVersionById(1L)).thenReturn(version);
        when(flowMapper.queryFlowNodeListByVersionId(1L)).thenReturn(Arrays.asList(modifyNode, reviewerNode));
        when(flowMapper.queryFlowEdgeListByVersionId(1L)).thenReturn(Collections.singletonList(edge));
        when(flowMapper.queryCondRuleListByVersionId(1L)).thenReturn(Collections.emptyList());
        when(flowMapper.queryApprovalConfigListByVersionId(1L)).thenReturn(Collections.emptyList());
        when(flowMapper.queryApprovalHandlerListByVersionId(1L)).thenReturn(Collections.emptyList());
        SecurityPoolAdjustAuditReq req = new SecurityPoolAdjustAuditReq();
        req.setProcessAction("approve");
        req.setHandlerId("2");
        SecurityPoolAdjustAuditReq.AttachmentChange change =
                new SecurityPoolAdjustAuditReq.AttachmentChange();
        change.setAdjustLogId(10L);
        change.setCreditReportSourceAttachmentIds(Collections.singletonList(100L));
        req.setAttachmentChanges(Collections.singletonList(change));

        ReflectionTestUtils.invokeMethod(service, "applyAttachmentChangesForModifySubmit",
                req, step, Collections.emptyList());

        verify(attachmentService).validateCreditReportSources(Collections.singletonList(100L), true);
    }

    /** 构建流程审批服务实例测试数据。 */
    private ForbiddenPoolAdjustFlowService buildService(ForbiddenPoolAdjustMapper mapper, FlowMapper flowMapper) {
        ForbiddenPoolAdjustFlowService service = new ForbiddenPoolAdjustFlowService();
        ReflectionTestUtils.setField(service, "forbiddenPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "forbiddenPoolAdjustService", mock(ForbiddenPoolAdjustService.class));
        ReflectionTestUtils.setField(service, "flowMapper", flowMapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));
        ReflectionTestUtils.setField(service, "investmentPoolService", mock(InvestmentPoolService.class));
        return service;
    }

    /** 构建审批请求测试数据。 */
    private SecurityPoolAdjustAuditReq buildReq(Long stepId, String handlerId, String handlerName, String comment) {
        SecurityPoolAdjustAuditReq req = new SecurityPoolAdjustAuditReq();
        req.setStepId(stepId);
        req.setAdjustLogId(1L);
        req.setAdjustBatchNo("BATCH001");
        req.setProcessAction("approve");
        req.setProcessComment(comment);
        req.setHandlerId(handlerId);
        req.setHandlerName(handlerName);
        return req;
    }

    /** 构建待处理流程步骤测试数据。 */
    private IpAdjustStepBo buildPendingStep(Long id, String handlerId, String handlerName) {
        IpAdjustStepBo step = new IpAdjustStepBo();
        step.setId(id);
        step.setAdjustLogId(1L);
        step.setAdjustBatchNo("BATCH001");
        step.setFlowNodeId(10103L);
        step.setNodeLabel("研究员B复核");
        step.setNodeType("approval");
        step.setApprovalStrategy("all");
        step.setStepStatus("pending");
        step.setHandlerId(handlerId);
        step.setHandlerName(handlerName);
        return step;
    }

    /** 构建流程节点测试数据。 */
    private FlowNodeBo buildFlowNode(Long id, String nodeId, String nodeType, String label) {
        FlowNodeBo node = new FlowNodeBo();
        node.setId(id);
        node.setVersionId(1L);
        node.setFlowId(101L);
        node.setNodeId(nodeId);
        node.setNodeType(nodeType);
        node.setLabel(label);
        node.setSortOrder(1);
        return node;
    }

    /** 构建流程连线测试数据。 */
    private FlowEdgeBo buildFlowEdge(Long fromNodeId, Long toNodeId, String routeAction) {
        FlowEdgeBo edge = new FlowEdgeBo();
        edge.setFromNodeId(fromNodeId);
        edge.setToNodeId(toNodeId);
        edge.setRouteAction(routeAction);
        return edge;
    }

    /** 构建调库日志测试数据。 */
    private IpAdjustLogBo buildLog(String auditStatus, String adjusterId) {
        IpAdjustLogBo log = new IpAdjustLogBo();
        log.setAuditStatus(auditStatus);
        log.setAdjusterId(adjusterId);
        return log;
    }

    /** 构建主体调入日志。 */
    private IpAdjustLogBo buildCompanyLog() {
        IpAdjustLogBo log = new IpAdjustLogBo();
        log.setId(10L);
        log.setSecurityCode("C10001");
        log.setSecurityShortName("某公司");
        log.setSecurityType("company");
        log.setAdjustMode("调入");
        log.setAdjustBatchNo("COMPANY202606281001");
        log.setTargetPoolId(15L);
        log.setTargetPoolName("禁投池");
        log.setPoolType("forbidden");
        log.setAdjusterId("1");
        log.setAdjusterName("管理员");
        return log;
    }
}
