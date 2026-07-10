package com.znty.rrs.service;

import com.znty.rrs.common.enums.AttachmentPurpose;
import com.znty.rrs.common.enums.AttachmentCategory;

import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.mapper.TempSecurityCodeMapper;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.NodeApprovalConfigBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.bo.FlowVersionBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpAdjustStepBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.securitypooladjustflow.SecurityPoolAdjustAuditReq;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SecurityPoolAdjustFlowServiceTest 测试类。 */
public class SecurityPoolAdjustFlowServiceTest {

    /** 验证 submitAdjustAuditShouldUseAdminOwnPendingStepWhenAdminIsHandler 测试场景。 */
    @Test
    public void submitAdjustAuditShouldUseAdminOwnPendingStepWhenAdminIsHandler() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        // 构建流程审批服务实例测试数据
        SecurityPoolAdjustFlowService service = buildService(mapper);
        // 构建待处理流程步骤测试数据
        IpAdjustStepBo otherStep = buildPendingStep(10L, "3", "研究员2");
        // 构建待处理流程步骤测试数据
        IpAdjustStepBo adminStep = buildPendingStep(11L, "1", "管理员");
        // 构建审批请求测试数据
        SecurityPoolAdjustAuditReq req = buildReq(10L, "1", "管理员", "同意");
        when(mapper.queryAdjustStepById(10L)).thenReturn(otherStep);
        when(mapper.queryPendingStepByHandler(1L, "BATCH001", 10103L, "1")).thenReturn(adminStep);
        when(mapper.editAdjustStepProcess(11L, "approve", "approve", "同意")).thenReturn(1);
        when(mapper.queryPendingStepCountByNode(1L, "BATCH001", 10103L)).thenReturn(1);
        // 构建调库日志测试数据
        IpAdjustLogBo tempLog = buildLog("00");
        tempLog.setSecurityCode("S001");
        when(mapper.queryAdjustLogListForAudit(1L, "BATCH001")).thenReturn(Collections.singletonList(tempLog));

        service.submitAdjustAudit(req);

        verify(mapper).editAdjustStepProcess(11L, "approve", "approve", "同意");
        verify(mapper, never()).editAdjustStepProcess(eq(10L), eq("approve"), eq("approve"), eq("同意"));
    }

    /** 验证 submitAdjustAuditShouldKeepAdminSuffixWhenAdminActsForOtherHandler 测试场景。 */
    @Test
    public void submitAdjustAuditShouldKeepAdminSuffixWhenAdminActsForOtherHandler() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        // 构建流程审批服务实例测试数据
        SecurityPoolAdjustFlowService service = buildService(mapper);
        // 构建待处理流程步骤测试数据
        IpAdjustStepBo otherStep = buildPendingStep(10L, "3", "研究员2");
        // 构建审批请求测试数据
        SecurityPoolAdjustAuditReq req = buildReq(10L, "1", "管理员", "同意");
        when(mapper.queryAdjustStepById(10L)).thenReturn(otherStep);
        when(mapper.queryPendingStepByHandler(1L, "BATCH001", 10103L, "1")).thenReturn(null);
        when(mapper.editAdjustStepProcess(10L, "approve", "approve", "同意（由管理员操作）")).thenReturn(1);
        when(mapper.queryPendingStepCountByNode(1L, "BATCH001", 10103L)).thenReturn(1);
        // 构建调库日志测试数据
        IpAdjustLogBo tempLog = buildLog("00");
        tempLog.setSecurityCode("S001");
        when(mapper.queryAdjustLogListForAudit(1L, "BATCH001")).thenReturn(Collections.singletonList(tempLog));

        service.submitAdjustAudit(req);

        verify(mapper).editAdjustStepProcess(10L, "approve", "approve", "同意（由管理员操作）");
    }

    /** 验证 submitAdjustAuditShouldRejectNormalUserActingForOtherHandler 测试场景。 */
    @Test
    public void submitAdjustAuditShouldRejectNormalUserActingForOtherHandler() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        // 构建流程审批服务实例测试数据
        SecurityPoolAdjustFlowService service = buildService(mapper);
        // 构建待处理流程步骤测试数据
        IpAdjustStepBo otherStep = buildPendingStep(10L, "3", "研究员2");
        // 构建审批请求测试数据
        SecurityPoolAdjustAuditReq req = buildReq(10L, "2", "研究员1", "同意");
        when(mapper.queryAdjustStepById(10L)).thenReturn(otherStep);

        try {
            service.submitAdjustAudit(req);
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("当前用户不是该步骤处理人");
            verify(mapper, never()).editAdjustStepProcess(eq(10L), eq("approve"), eq("approve"), eq("同意"));
            return;
        }
        throw new AssertionError("普通用户代办他人步骤时应抛出业务异常");
    }

    /** 验证 submitAdjustAuditShouldRejectAdminNameWithoutAdminId 测试场景。 */
    @Test
    public void submitAdjustAuditShouldRejectAdminNameWithoutAdminId() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        // 构建流程审批服务实例测试数据
        SecurityPoolAdjustFlowService service = buildService(mapper);
        // 构建待处理流程步骤测试数据
        IpAdjustStepBo otherStep = buildPendingStep(10L, "3", "研究员2");
        // 构建名称为管理员但 ID 非 1 的审批请求测试数据
        SecurityPoolAdjustAuditReq req = buildReq(10L, "2", "管理员", "同意");
        when(mapper.queryAdjustStepById(10L)).thenReturn(otherStep);

        try {
            service.submitAdjustAudit(req);
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("当前用户不是该步骤处理人");
            verify(mapper, never()).editAdjustStepProcess(eq(10L), eq("approve"), eq("approve"), eq("同意"));
            return;
        }
        throw new AssertionError("非 1 用户仅名称为管理员时应抛出业务异常");
    }

    /** 验证 submitAdjustAuditShouldRejectSubmitterProcessLaterStep 测试场景。 */
    @Test
    public void submitAdjustAuditShouldRejectSubmitterProcessLaterStep() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        // 构建流程审批服务实例测试数据
        SecurityPoolAdjustFlowService service = buildService(mapper);
        // 构建发起人后续待处理步骤测试数据
        IpAdjustStepBo step = buildPendingStep(10L, "2", "研究员1");
        // 构建发起人审批请求测试数据
        SecurityPoolAdjustAuditReq req = buildReq(10L, "2", "研究员1", "同意");
        when(mapper.queryAdjustStepById(10L)).thenReturn(step);
        when(mapper.queryAdjustLogListForAudit(1L, "BATCH001")).thenReturn(Collections.singletonList(buildLog("00", "2")));

        try {
            service.submitAdjustAudit(req);
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("发起人不能参与后续流程操作");
            verify(mapper, never()).editAdjustStepProcess(eq(10L), eq("approve"), eq("approve"), eq("同意"));
            return;
        }
        throw new AssertionError("发起人处理后续步骤时应抛出业务异常");
    }

    /** 验证 submitAdjustAuditShouldAllowAdminSubmitterProcessLaterStep 测试场景。 */
    @Test
    public void submitAdjustAuditShouldAllowAdminSubmitterProcessLaterStep() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        // 构建流程审批服务实例测试数据
        SecurityPoolAdjustFlowService service = buildService(mapper);
        // 构建管理员待处理步骤测试数据
        IpAdjustStepBo step = buildPendingStep(10L, "1", "管理员");
        // 构建管理员审批请求测试数据
        SecurityPoolAdjustAuditReq req = buildReq(10L, "1", "管理员", "同意");
        when(mapper.queryAdjustStepById(10L)).thenReturn(step);
        when(mapper.editAdjustStepProcess(10L, "approve", "approve", "同意")).thenReturn(1);
        when(mapper.queryPendingStepCountByNode(1L, "BATCH001", 10103L)).thenReturn(1);
        when(mapper.queryAdjustLogListForAudit(1L, "BATCH001")).thenReturn(Collections.singletonList(buildLog("00", "1")));

        service.submitAdjustAudit(req);

        verify(mapper).editAdjustStepProcess(10L, "approve", "approve", "同意");
    }

    /** 验证中间审核节点通过后仍保持流程中状态。 */
    @Test
    public void submitAdjustAuditShouldKeepProcessingStatusForMiddleApprovalNode() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        FlowMapper flowMapper = mock(FlowMapper.class);
        // 构建流程审批服务实例测试数据
        SecurityPoolAdjustFlowService service = buildService(mapper, flowMapper);
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

    /** 验证 submitAdjustAuditShouldAllowSubmitterModifyRejectedProcess 测试场景。 */
    @Test
    public void submitAdjustAuditShouldAllowSubmitterModifyRejectedProcess() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        FlowMapper flowMapper = mock(FlowMapper.class);
        // 构建流程审批服务实例测试数据
        SecurityPoolAdjustFlowService service = buildService(mapper, flowMapper);
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
    }

    /** 验证驳回待修改提交时保存附件变更。 */
    @Test
    public void submitAdjustAuditShouldSaveAttachmentChangesWhenModifySubmit() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SysAttachmentService attachmentService = mock(SysAttachmentService.class);
        FlowMapper flowMapper = mock(FlowMapper.class);
        // 构建流程审批服务实例测试数据
        SecurityPoolAdjustFlowService service = new SecurityPoolAdjustFlowService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "flowMapper", flowMapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", attachmentService);
        // 构建发起人修改节点待处理步骤测试数据
        IpAdjustStepBo step = buildPendingStep(10L, "2", "研究员1");
        step.setNodeLabel("流程发起人修改");
        step.setApprovalStrategy("initiator");
        // 构建发起人审批请求测试数据
        SecurityPoolAdjustAuditReq req = buildReq(10L, "2", "研究员1", "已修改");
        SecurityPoolAdjustAuditReq.AttachmentChange change = new SecurityPoolAdjustAuditReq.AttachmentChange();
        change.setAdjustLogId(1L);
        change.setCreditReportFileIndexes(Collections.singletonList(0));
        change.setMaterialFileIndexes(Collections.singletonList(1));
        change.setCreditReportSourceAttachmentIds(Collections.singletonList(7L));
        change.setMaterialSourceAttachmentIds(Collections.singletonList(8L));
        change.setDeleteAttachmentIds(Collections.singletonList(9L));
        req.setAttachmentChanges(Collections.singletonList(change));
        when(mapper.queryAdjustStepById(10L)).thenReturn(step);
        when(mapper.editAdjustStepProcess(10L, "submit", "submit", "已修改")).thenReturn(1);
        when(mapper.queryAdjustLogListForAudit(1L, "BATCH001")).thenReturn(Collections.singletonList(buildLog(1L, "11", "2")));
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

        service.submitAdjustAudit(req, Collections.emptyList());

        verify(attachmentService).deleteAdjustLogAttachments(1L, Collections.singletonList(9L));
        verify(attachmentService).bindAttachments(1L, Collections.singletonList(0),
                AttachmentCategory.CREDIT_REPORT_HAND.getCode(), null);
        verify(attachmentService).bindAttachments(1L, Collections.singletonList(1),
                AttachmentCategory.MATERIAL_HAND.getCode(), null);
        verify(attachmentService).copyReportAttachments(1L, Collections.singletonList(7L),
                AttachmentPurpose.CREDIT_REPORT.getCode(), "2");
        verify(attachmentService).copyReportAttachments(1L, Collections.singletonList(8L),
                AttachmentPurpose.MATERIAL.getCode(), "2");
        verify(mapper).editAdjustStepProcess(10L, "submit", "submit", "已修改");
    }

    /** 构建流程审批服务实例测试数据。 */
    private SecurityPoolAdjustFlowService buildService(SecurityPoolAdjustMapper mapper) {
        return buildService(mapper, mock(SysAttachmentService.class));
    }

    /** 构建流程审批服务实例测试数据。 */
    private SecurityPoolAdjustFlowService buildService(SecurityPoolAdjustMapper mapper, SysAttachmentService attachmentService) {
        SecurityPoolAdjustFlowService service = new SecurityPoolAdjustFlowService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "flowMapper", mock(FlowMapper.class));
        ReflectionTestUtils.setField(service, "sysAttachmentService", attachmentService);
        return service;
    }

    /** 构建流程审批服务实例测试数据。 */
    private SecurityPoolAdjustFlowService buildService(SecurityPoolAdjustMapper mapper, FlowMapper flowMapper) {
        SecurityPoolAdjustFlowService service = new SecurityPoolAdjustFlowService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "flowMapper", flowMapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));
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

    /** 模拟流程快照查询。 */
    private void mockFlowSnapshot(FlowMapper flowMapper, FlowNodeBo node, FlowEdgeBo edge) {
        FlowVersionBo version = new FlowVersionBo();
        version.setId(1L);
        when(flowMapper.queryFlowNodeById(node.getId())).thenReturn(node);
        when(flowMapper.queryFlowVersionById(1L)).thenReturn(version);
        when(flowMapper.queryFlowNodeListByVersionId(1L)).thenReturn(Collections.singletonList(node));
        when(flowMapper.queryFlowEdgeListByVersionId(1L)).thenReturn(Collections.singletonList(edge));
        when(flowMapper.queryCondRuleListByVersionId(1L)).thenReturn(Collections.emptyList());
        when(flowMapper.queryApprovalConfigListByVersionId(1L)).thenReturn(Collections.emptyList());
        when(flowMapper.queryApprovalHandlerListByVersionId(1L)).thenReturn(Collections.emptyList());
    }

    /** 构建调库日志测试数据。 */
    private IpAdjustLogBo buildLog(String auditStatus) {
        IpAdjustLogBo log = new IpAdjustLogBo();
        log.setAuditStatus(auditStatus);
        return log;
    }

    /** 构建调库日志测试数据。 */
    private IpAdjustLogBo buildLog(String auditStatus, String adjusterId) {
        IpAdjustLogBo log = buildLog(auditStatus);
        log.setAdjusterId(adjusterId);
        return log;
    }

    /** 构建调库日志测试数据。 */
    private IpAdjustLogBo buildLog(Long id, String auditStatus, String adjusterId) {
        IpAdjustLogBo log = buildLog(auditStatus, adjusterId);
        log.setId(id);
        return log;
    }

    /** 评级联动改判：改判池在矩阵允许列表内时，应更新日志目标池并落地到改判池。 */
    @Test
    public void finishAdjustBatchShouldRedirectToApprovedPool() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SysAttachmentService attachmentService = mock(SysAttachmentService.class);
        SecurityPoolAdjustFlowService service = buildService(mapper, attachmentService);
        SecurityPoolAdjustService securityPoolAdjustService = mock(SecurityPoolAdjustService.class);
        ReflectionTestUtils.setField(service, "securityPoolAdjustService", securityPoolAdjustService);
        ReflectionTestUtils.setField(service, "investmentPoolService", mock(InvestmentPoolService.class));

        IpAdjustStepBo step = buildPendingStep(10L, "1", "管理员");
        IpAdjustLogBo log = buildLog(1L, "00", "1");
        log.setSecurityCode("S001");
        log.setAdjustMode("调入");
        log.setTargetPoolId(10L);
        when(mapper.queryAdjustLogListForAudit(1L, "BATCH001")).thenReturn(Collections.singletonList(log));
        // 改判池在矩阵允许列表内
        InvestmentPoolBo redirectPool = new InvestmentPoolBo();
        redirectPool.setId(20L);
        redirectPool.setPoolName("一级库");
        when(securityPoolAdjustService.queryRedirectPoolOptions("S001")).thenReturn(Collections.singletonList(redirectPool));
        // 无手工信评报告附件，跳过 generateInternalReportsOnFinish
        when(attachmentService.queryHandCreditReportAttachments(1L)).thenReturn(Collections.emptyList());

        ReflectionTestUtils.invokeMethod(service, "finishAdjustBatch", step, 20L);

        // 验证更新日志目标池为改判池
        verify(mapper).editAdjustLogTargetPool("BATCH001", 20L, "一级库");
        // 验证落地到改判池
        ArgumentCaptor<IpAdjustLogBo> captor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(mapper).addPoolStatus(captor.capture());
        assertThat(captor.getValue().getTargetPoolId()).isEqualTo(20L);
    }

    /** 评级联动改判：改判池不在矩阵允许列表内时应抛出异常。 */
    @Test
    public void finishAdjustBatchShouldRejectRedirectPoolNotInMatrix() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SysAttachmentService attachmentService = mock(SysAttachmentService.class);
        SecurityPoolAdjustFlowService service = buildService(mapper, attachmentService);
        SecurityPoolAdjustService securityPoolAdjustService = mock(SecurityPoolAdjustService.class);
        ReflectionTestUtils.setField(service, "securityPoolAdjustService", securityPoolAdjustService);

        IpAdjustStepBo step = buildPendingStep(10L, "1", "管理员");
        IpAdjustLogBo log = buildLog(1L, "00", "1");
        log.setSecurityCode("S001");
        log.setAdjustMode("调入");
        when(mapper.queryAdjustLogListForAudit(1L, "BATCH001")).thenReturn(Collections.singletonList(log));
        // 改判池不在矩阵允许列表内
        when(securityPoolAdjustService.queryRedirectPoolOptions("S001")).thenReturn(Collections.<InvestmentPoolBo>emptyList());

        try {
            ReflectionTestUtils.invokeMethod(service, "finishAdjustBatch", step, 99L);
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("改判池不在主体债入库矩阵允许列表内");
            return;
        }
        throw new AssertionError("改判池不在矩阵内时应抛出异常");
    }

    // ===== 节点语义 approval_strategy 判断测试 =====

    /** 验证 O32 节点 approval_strategy=o32 时 isAutoApprovalNode 返回 true。 */
    @Test
    public void isAutoApprovalNodeShouldReturnTrueWhenO32Strategy() {
        SecurityPoolAdjustFlowService service = buildService(mock(SecurityPoolAdjustMapper.class));
        FlowNodeBo node = buildFlowNode(1L, "n6", "approval", "O32自动审批");
        NodeApprovalConfigBo config = new NodeApprovalConfigBo();
        config.setApprovalStrategy("o32");
        Boolean result = ReflectionTestUtils.invokeMethod(service, "isAutoApprovalNode", node, config);
        assertThat(result).isTrue();
    }

    /** 验证 approval_strategy=preempt 时 isAutoApprovalNode 返回 false。 */
    @Test
    public void isAutoApprovalNodeShouldReturnFalseWhenNotO32() {
        SecurityPoolAdjustFlowService service = buildService(mock(SecurityPoolAdjustMapper.class));
        FlowNodeBo node = buildFlowNode(1L, "n6", "approval", "O32自动审批");
        NodeApprovalConfigBo config = new NodeApprovalConfigBo();
        config.setApprovalStrategy("preempt");
        Boolean result = ReflectionTestUtils.invokeMethod(service, "isAutoApprovalNode", node, config);
        assertThat(result).isFalse();
    }

    /** 验证 initiator + submit 出边时识别为发起提交节点。 */
    @Test
    public void isSubmitSemanticStepShouldReturnTrueWhenInitiatorHasSubmitRoute() {
        FlowMapper flowMapper = mock(FlowMapper.class);
        SecurityPoolAdjustFlowService service = buildService(mock(SecurityPoolAdjustMapper.class), flowMapper);
        FlowNodeBo node = buildFlowNode(1L, "n2", "approval", "研究员A发起");
        IpAdjustStepBo step = buildPendingStep(10L, "2", "研究员1");
        step.setFlowNodeId(node.getId());
        step.setApprovalStrategy("initiator");
        mockFlowSnapshot(flowMapper, node, buildFlowEdge(node.getId(), 2L, "submit"));
        Boolean result = ReflectionTestUtils.invokeMethod(service, "isSubmitSemanticStep", step);
        assertThat(result).isTrue();
    }

    /** 验证 initiator + resubmit 出边时识别为驳回修改节点。 */
    @Test
    public void isModifyStepShouldReturnTrueWhenInitiatorHasResubmitRoute() {
        FlowMapper flowMapper = mock(FlowMapper.class);
        SecurityPoolAdjustFlowService service = buildService(mock(SecurityPoolAdjustMapper.class), flowMapper);
        FlowNodeBo node = buildFlowNode(1L, "n4", "approval", "研究员A修改");
        IpAdjustStepBo step = buildPendingStep(10L, "2", "研究员1");
        step.setFlowNodeId(node.getId());
        step.setApprovalStrategy("initiator");
        mockFlowSnapshot(flowMapper, node, buildFlowEdge(node.getId(), 2L, "resubmit"));
        Boolean result = ReflectionTestUtils.invokeMethod(service, "isModifyStep", step);
        assertThat(result).isTrue();
    }

    // ===== O32 临时代码判断测试（isTemporaryCode）=====

    /** 验证证券代码在临时代码表中时 isTemporaryCode 返回 true。 */
    @Test
    public void isTemporaryCodeShouldReturnTrueWhenSecurityCodeInTempTable() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        TempSecurityCodeMapper tempMapper = mock(TempSecurityCodeMapper.class);
        SecurityPoolAdjustFlowService service = buildService(mapper);
        ReflectionTestUtils.setField(service, "tempSecurityCodeMapper", tempMapper);
        IpAdjustStepBo step = buildPendingStep(10L, "3", "研究员2");
        IpAdjustLogBo tempLog = buildLog("00");
        tempLog.setSecurityCode("S001");
        when(mapper.queryAdjustLogListForAudit(1L, "BATCH001")).thenReturn(Collections.singletonList(tempLog));
        when(tempMapper.queryTemporaryCodeCountBySecurityCode("S001")).thenReturn(1);
        Boolean result = ReflectionTestUtils.invokeMethod(service, "isTemporaryCode", step);
        assertThat(result).isTrue();
    }

    /** 验证证券代码不在临时代码表中时 isTemporaryCode 返回 false。 */
    @Test
    public void isTemporaryCodeShouldReturnFalseWhenSecurityCodeNotInTempTable() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        TempSecurityCodeMapper tempMapper = mock(TempSecurityCodeMapper.class);
        SecurityPoolAdjustFlowService service = buildService(mapper);
        ReflectionTestUtils.setField(service, "tempSecurityCodeMapper", tempMapper);
        IpAdjustStepBo step = buildPendingStep(10L, "3", "研究员2");
        IpAdjustLogBo tempLog = buildLog("00");
        tempLog.setSecurityCode("S001");
        when(mapper.queryAdjustLogListForAudit(1L, "BATCH001")).thenReturn(Collections.singletonList(tempLog));
        when(tempMapper.queryTemporaryCodeCountBySecurityCode("S001")).thenReturn(0);
        Boolean result = ReflectionTestUtils.invokeMethod(service, "isTemporaryCode", step);
        assertThat(result).isFalse();
    }
}
