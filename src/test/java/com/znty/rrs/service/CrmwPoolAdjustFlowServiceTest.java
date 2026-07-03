package com.znty.rrs.service;

import com.znty.rrs.common.enums.AttachmentPurpose;
import com.znty.rrs.common.enums.AttachmentCategory;

import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.CrmwPoolAdjustMapper;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpAdjustStepBo;
import com.znty.rrs.entity.crmwpooladjustflow.CrmwPoolAdjustAuditReq;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CrmwPoolAdjustFlowServiceTest 测试类。 */
public class CrmwPoolAdjustFlowServiceTest {

    /** 验证 submitAdjustAuditShouldUseAdminOwnPendingStepWhenAdminIsHandler 测试场景。 */
    @Test
    public void submitAdjustAuditShouldUseAdminOwnPendingStepWhenAdminIsHandler() {
        CrmwPoolAdjustMapper mapper = mock(CrmwPoolAdjustMapper.class);
        // 构建流程审批服务实例测试数据
        CrmwPoolAdjustFlowService service = buildService(mapper);
        // 构建待处理流程步骤测试数据
        IpAdjustStepBo otherStep = buildPendingStep(10L, "2", "研究员2");
        // 构建待处理流程步骤测试数据
        IpAdjustStepBo adminStep = buildPendingStep(11L, "1001", "管理员");
        // 构建审批请求测试数据
        CrmwPoolAdjustAuditReq req = buildReq(10L, "1001", "管理员", "同意");
        when(mapper.queryAdjustStepById(10L)).thenReturn(otherStep);
        when(mapper.queryPendingStepByHandler(1L, "BATCH001", 10103L, "1001")).thenReturn(adminStep);
        when(mapper.editAdjustStepProcess(11L, "approve", "approve", "同意")).thenReturn(1);
        when(mapper.queryPendingStepCountByNode(1L, "BATCH001", 10103L)).thenReturn(1);
        // 构建调库日志测试数据
        when(mapper.queryAdjustLogListForAudit(1L, "BATCH001")).thenReturn(Collections.singletonList(buildLog("00")));

        service.submitAdjustAudit(req);

        verify(mapper).editAdjustStepProcess(11L, "approve", "approve", "同意");
        verify(mapper, never()).editAdjustStepProcess(eq(10L), eq("approve"), eq("approve"), eq("同意"));
    }

    /** 验证 submitAdjustAuditShouldKeepAdminSuffixWhenAdminActsForOtherHandler 测试场景。 */
    @Test
    public void submitAdjustAuditShouldKeepAdminSuffixWhenAdminActsForOtherHandler() {
        CrmwPoolAdjustMapper mapper = mock(CrmwPoolAdjustMapper.class);
        // 构建流程审批服务实例测试数据
        CrmwPoolAdjustFlowService service = buildService(mapper);
        // 构建待处理流程步骤测试数据
        IpAdjustStepBo otherStep = buildPendingStep(10L, "2", "研究员2");
        // 构建审批请求测试数据
        CrmwPoolAdjustAuditReq req = buildReq(10L, "1001", "管理员", "同意");
        when(mapper.queryAdjustStepById(10L)).thenReturn(otherStep);
        when(mapper.queryPendingStepByHandler(1L, "BATCH001", 10103L, "1001")).thenReturn(null);
        when(mapper.editAdjustStepProcess(10L, "approve", "approve", "同意（由管理员操作）")).thenReturn(1);
        when(mapper.queryPendingStepCountByNode(1L, "BATCH001", 10103L)).thenReturn(1);
        // 构建调库日志测试数据
        when(mapper.queryAdjustLogListForAudit(1L, "BATCH001")).thenReturn(Collections.singletonList(buildLog("00")));

        service.submitAdjustAudit(req);

        verify(mapper).editAdjustStepProcess(10L, "approve", "approve", "同意（由管理员操作）");
    }

    /** 验证 submitAdjustAuditShouldRejectNormalUserActingForOtherHandler 测试场景。 */
    @Test
    public void submitAdjustAuditShouldRejectNormalUserActingForOtherHandler() {
        CrmwPoolAdjustMapper mapper = mock(CrmwPoolAdjustMapper.class);
        // 构建流程审批服务实例测试数据
        CrmwPoolAdjustFlowService service = buildService(mapper);
        // 构建待处理流程步骤测试数据
        IpAdjustStepBo otherStep = buildPendingStep(10L, "2", "研究员2");
        // 构建审批请求测试数据
        CrmwPoolAdjustAuditReq req = buildReq(10L, "1", "研究员1", "同意");
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
        CrmwPoolAdjustMapper mapper = mock(CrmwPoolAdjustMapper.class);
        // 构建流程审批服务实例测试数据
        CrmwPoolAdjustFlowService service = buildService(mapper);
        // 构建待处理流程步骤测试数据
        IpAdjustStepBo otherStep = buildPendingStep(10L, "2", "研究员2");
        // 构建名称为管理员但 ID 非 1001 的审批请求测试数据
        CrmwPoolAdjustAuditReq req = buildReq(10L, "1", "管理员", "同意");
        when(mapper.queryAdjustStepById(10L)).thenReturn(otherStep);

        try {
            service.submitAdjustAudit(req);
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("当前用户不是该步骤处理人");
            verify(mapper, never()).editAdjustStepProcess(eq(10L), eq("approve"), eq("approve"), eq("同意"));
            return;
        }
        throw new AssertionError("非 1001 用户仅名称为管理员时应抛出业务异常");
    }

    /** 验证 submitAdjustAuditShouldRejectSubmitterProcessLaterStep 测试场景。 */
    @Test
    public void submitAdjustAuditShouldRejectSubmitterProcessLaterStep() {
        CrmwPoolAdjustMapper mapper = mock(CrmwPoolAdjustMapper.class);
        // 构建流程审批服务实例测试数据
        CrmwPoolAdjustFlowService service = buildService(mapper);
        // 构建发起人后续待处理步骤测试数据
        IpAdjustStepBo step = buildPendingStep(10L, "1", "研究员1");
        // 构建发起人审批请求测试数据
        CrmwPoolAdjustAuditReq req = buildReq(10L, "1", "研究员1", "同意");
        when(mapper.queryAdjustStepById(10L)).thenReturn(step);
        when(mapper.queryAdjustLogListForAudit(1L, "BATCH001")).thenReturn(Collections.singletonList(buildLog("00", "1")));

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
        CrmwPoolAdjustMapper mapper = mock(CrmwPoolAdjustMapper.class);
        // 构建流程审批服务实例测试数据
        CrmwPoolAdjustFlowService service = buildService(mapper);
        // 构建管理员待处理步骤测试数据
        IpAdjustStepBo step = buildPendingStep(10L, "1001", "管理员");
        // 构建管理员审批请求测试数据
        CrmwPoolAdjustAuditReq req = buildReq(10L, "1001", "管理员", "同意");
        when(mapper.queryAdjustStepById(10L)).thenReturn(step);
        when(mapper.editAdjustStepProcess(10L, "approve", "approve", "同意")).thenReturn(1);
        when(mapper.queryPendingStepCountByNode(1L, "BATCH001", 10103L)).thenReturn(1);
        when(mapper.queryAdjustLogListForAudit(1L, "BATCH001")).thenReturn(Collections.singletonList(buildLog("00", "1001")));

        service.submitAdjustAudit(req);

        verify(mapper).editAdjustStepProcess(10L, "approve", "approve", "同意");
    }

    /** 验证 submitAdjustAuditShouldAllowSubmitterModifyRejectedProcess 测试场景。 */
    @Test
    public void submitAdjustAuditShouldAllowSubmitterModifyRejectedProcess() {
        CrmwPoolAdjustMapper mapper = mock(CrmwPoolAdjustMapper.class);
        // 构建流程审批服务实例测试数据
        CrmwPoolAdjustFlowService service = buildService(mapper);
        // 构建发起人修改节点待处理步骤测试数据
        IpAdjustStepBo step = buildPendingStep(10L, "1", "研究员1");
        step.setNodeLabel("流程发起人修改");
        // 构建发起人审批请求测试数据
        CrmwPoolAdjustAuditReq req = buildReq(10L, "1", "研究员1", "已修改");
        when(mapper.queryAdjustStepById(10L)).thenReturn(step);
        when(mapper.editAdjustStepProcess(10L, "submit", "submit", "已修改")).thenReturn(1);
        when(mapper.queryPendingStepCountByNode(1L, "BATCH001", 10103L)).thenReturn(1);
        when(mapper.queryAdjustLogListForAudit(1L, "BATCH001")).thenReturn(Collections.singletonList(buildLog("11", "1")));

        service.submitAdjustAudit(req);

        verify(mapper).editAdjustStepProcess(10L, "submit", "submit", "已修改");
    }

    /** 验证驳回待修改提交时保存附件变更。 */
    @Test
    public void submitAdjustAuditShouldSaveAttachmentChangesWhenModifySubmit() {
        CrmwPoolAdjustMapper mapper = mock(CrmwPoolAdjustMapper.class);
        SysAttachmentService attachmentService = mock(SysAttachmentService.class);
        // 构建流程审批服务实例测试数据
        CrmwPoolAdjustFlowService service = buildService(mapper, attachmentService);
        // 构建发起人修改节点待处理步骤测试数据
        IpAdjustStepBo step = buildPendingStep(10L, "1", "研究员1");
        step.setNodeLabel("流程发起人修改");
        // 构建发起人审批请求测试数据
        CrmwPoolAdjustAuditReq req = buildReq(10L, "1", "研究员1", "已修改");
        CrmwPoolAdjustAuditReq.AttachmentChange change = new CrmwPoolAdjustAuditReq.AttachmentChange();
        change.setAdjustLogId(1L);
        change.setCreditReportFileIndexes(Collections.singletonList(0));
        change.setMaterialFileIndexes(Collections.singletonList(1));
        change.setCreditReportSourceAttachmentIds(Collections.singletonList(7L));
        change.setMaterialSourceAttachmentIds(Collections.singletonList(8L));
        change.setDeleteAttachmentIds(Collections.singletonList(9L));
        req.setAttachmentChanges(Collections.singletonList(change));
        when(mapper.queryAdjustStepById(10L)).thenReturn(step);
        when(mapper.editAdjustStepProcess(10L, "submit", "submit", "已修改")).thenReturn(1);
        when(mapper.queryPendingStepCountByNode(1L, "BATCH001", 10103L)).thenReturn(1);
        when(mapper.queryAdjustLogListForAudit(1L, "BATCH001")).thenReturn(Collections.singletonList(buildLog(1L, "11", "1")));

        service.submitAdjustAudit(req, Collections.emptyList());

        verify(attachmentService).deleteAdjustLogAttachments(1L, Collections.singletonList(9L));
        verify(attachmentService).bindAttachments(1L, Collections.singletonList(0),
                AttachmentCategory.CREDIT_REPORT_HAND.getCode(), null);
        verify(attachmentService).bindAttachments(1L, Collections.singletonList(1),
                AttachmentCategory.MATERIAL_HAND.getCode(), null);
        verify(attachmentService).copyReportAttachments(1L, Collections.singletonList(7L),
                AttachmentPurpose.CREDIT_REPORT.getCode(), "1");
        verify(attachmentService).copyReportAttachments(1L, Collections.singletonList(8L),
                AttachmentPurpose.MATERIAL.getCode(), "1");
        verify(mapper).editAdjustStepProcess(10L, "submit", "submit", "已修改");
    }

    /** 验证最终通过时CRMW调入结果写入独立状态表对应Mapper。 */
    @Test
    public void finishAdjustBatchShouldAddCrmwPoolStatusForInbound() {
        CrmwPoolAdjustMapper mapper = mock(CrmwPoolAdjustMapper.class);
        SysAttachmentService attachmentService = mock(SysAttachmentService.class);
        CrmwPoolAdjustFlowService service = buildService(mapper, attachmentService);
        ReflectionTestUtils.setField(service, "investmentPoolService", mock(InvestmentPoolService.class));
        ReflectionTestUtils.setField(service, "reportService", mock(ReportService.class));
        IpAdjustStepBo step = buildPendingStep(10L, "1", "研究员1");
        IpAdjustLogBo log = buildLog(1L, "00", "1");
        log.setAdjustMode("调入");
        log.setCrmwScode("CRMW24001.IB");
        when(mapper.queryAdjustLogListForAudit(1L, "BATCH001")).thenReturn(Collections.singletonList(log));
        when(attachmentService.queryHandCreditReportAttachments(1L)).thenReturn(Collections.emptyList());

        ReflectionTestUtils.invokeMethod(service, "finishAdjustBatch", step);

        verify(mapper).addPoolStatus(log);
        assertThat(log.getCrmwScode()).isEqualTo("CRMW24001.IB");
    }

    /** 验证最终通过时CRMW调出结果只软删除独立状态记录。 */
    @Test
    public void finishAdjustBatchShouldDeleteCrmwPoolStatusForOutbound() {
        CrmwPoolAdjustMapper mapper = mock(CrmwPoolAdjustMapper.class);
        SysAttachmentService attachmentService = mock(SysAttachmentService.class);
        CrmwPoolAdjustFlowService service = buildService(mapper, attachmentService);
        ReflectionTestUtils.setField(service, "investmentPoolService", mock(InvestmentPoolService.class));
        ReflectionTestUtils.setField(service, "reportService", mock(ReportService.class));
        IpAdjustStepBo step = buildPendingStep(10L, "1", "研究员1");
        IpAdjustLogBo log = buildLog(1L, "00", "1");
        log.setAdjustMode("调出");
        log.setSecurityCode("100001");
        log.setTargetPoolId(22L);
        when(mapper.queryAdjustLogListForAudit(1L, "BATCH001")).thenReturn(Collections.singletonList(log));
        when(attachmentService.queryHandCreditReportAttachments(1L)).thenReturn(Collections.emptyList());

        ReflectionTestUtils.invokeMethod(service, "finishAdjustBatch", step);

        verify(mapper).deletePoolStatusSoft("100001", 22L);
        verify(mapper, never()).addPoolStatus(log);
    }

    /** 构建流程审批服务实例测试数据。 */
    private CrmwPoolAdjustFlowService buildService(CrmwPoolAdjustMapper mapper) {
        return buildService(mapper, mock(SysAttachmentService.class));
    }

    /** 构建流程审批服务实例测试数据。 */
    private CrmwPoolAdjustFlowService buildService(CrmwPoolAdjustMapper mapper, SysAttachmentService attachmentService) {
        CrmwPoolAdjustFlowService service = new CrmwPoolAdjustFlowService();
        ReflectionTestUtils.setField(service, "crmwPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "flowMapper", mock(FlowMapper.class));
        ReflectionTestUtils.setField(service, "sysAttachmentService", attachmentService);
        return service;
    }

    /** 构建审批请求测试数据。 */
    private CrmwPoolAdjustAuditReq buildReq(Long stepId, String handlerId, String handlerName, String comment) {
        CrmwPoolAdjustAuditReq req = new CrmwPoolAdjustAuditReq();
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
}
