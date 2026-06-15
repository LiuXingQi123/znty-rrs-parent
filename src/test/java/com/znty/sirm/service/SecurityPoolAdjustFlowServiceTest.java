package com.znty.sirm.service;

import com.znty.sirm.exception.BizException;
import com.znty.sirm.mapper.FlowMapper;
import com.znty.sirm.mapper.SecurityPoolAdjustMapper;
import com.znty.sirm.model.IpAdjustLogBo;
import com.znty.sirm.model.IpAdjustStepBo;
import com.znty.sirm.model.SecurityPoolAdjustAuditReq;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
        IpAdjustStepBo otherStep = buildPendingStep(10L, "2", "研究员2");
        // 构建待处理流程步骤测试数据
        IpAdjustStepBo adminStep = buildPendingStep(11L, "1001", "管理员");
        // 构建审批请求测试数据
        SecurityPoolAdjustAuditReq req = buildReq(10L, "1001", "管理员", "同意");
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
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        // 构建流程审批服务实例测试数据
        SecurityPoolAdjustFlowService service = buildService(mapper);
        // 构建待处理流程步骤测试数据
        IpAdjustStepBo otherStep = buildPendingStep(10L, "2", "研究员2");
        // 构建审批请求测试数据
        SecurityPoolAdjustAuditReq req = buildReq(10L, "1001", "管理员", "同意");
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
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        // 构建流程审批服务实例测试数据
        SecurityPoolAdjustFlowService service = buildService(mapper);
        // 构建待处理流程步骤测试数据
        IpAdjustStepBo otherStep = buildPendingStep(10L, "2", "研究员2");
        // 构建审批请求测试数据
        SecurityPoolAdjustAuditReq req = buildReq(10L, "1", "研究员1", "同意");
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

    /** 构建流程审批服务实例测试数据。 */
    private SecurityPoolAdjustFlowService buildService(SecurityPoolAdjustMapper mapper) {
        SecurityPoolAdjustFlowService service = new SecurityPoolAdjustFlowService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "flowMapper", mock(FlowMapper.class));
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

    /** 构建调库日志测试数据。 */
    private IpAdjustLogBo buildLog(String auditStatus) {
        IpAdjustLogBo log = new IpAdjustLogBo();
        log.setAuditStatus(auditStatus);
        return log;
    }
}
