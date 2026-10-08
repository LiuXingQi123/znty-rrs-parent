package com.znty.rrs.service;

import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.bo.FundAdjustLogBo;
import com.znty.rrs.entity.bo.FundAdjustStepBo;
import com.znty.rrs.entity.bo.FundInfoBo;
import com.znty.rrs.entity.bo.NodeApprovalConfigBo;
import com.znty.rrs.entity.fundpooladjust.FundPoolAdjustAuditReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.FundPoolAdjustMapper;
import com.znty.rrs.mapper.TempFundCodeMapper;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/** 基金池驳回后修改原因和意见的审批回归测试。 */
public class FundPoolAdjustFlowServiceTest {
    /** 基金调库数据访问组件 */
    private final FundPoolAdjustMapper mapper = mock(FundPoolAdjustMapper.class);
    /** 流程定义数据访问组件 */
    private final FlowMapper flowMapper = mock(FlowMapper.class);
    /** 有效基金临时代码登记组件 */
    private final TempFundCodeMapper tempFundCodeMapper = mock(TempFundCodeMapper.class);
    /** 当前发起人的修改待办 */
    private final FundAdjustStepBo step = new FundAdjustStepBo();
    /** 当前批次的调库申请 */
    private final FundAdjustLogBo log = new FundAdjustLogBo();

    /** 重新提交保存多行原因、允许清空意见，并使用待办所属批次。 */
    @Test
    public void modifySubmitShouldSaveReasonAdviceForStepBatch() {
        // 构造真实修改路由和发起人的重新提交请求
        FundPoolAdjustFlowService service = buildModifyService();
        FundPoolAdjustAuditReq req = buildReq();
        req.setAdjustBatchNo("OTHER_BATCH");
        req.setAdjustReason("补充原因\n第二行");
        req.setAdjustAdvice("");
        when(mapper.editAdjustLogReasonAdvice("FUND001", "补充原因\n第二行", "")).thenReturn(1);

        assertThat(service.submitAdjustAudit(req).getAuditStatus()).isEqualTo("00");

        verify(mapper).editAdjustLogReasonAdvice("FUND001", "补充原因\n第二行", "");
        verify(mapper).editAdjustStepProcess(10L, "submit", "submit", "已修改");
        verify(mapper, never()).addFundPoolStatus(any());
    }

    /** 未携带原因和意见时不覆盖原申请文本。 */
    @Test
    public void modifySubmitWithoutTextShouldKeepExistingReasonAdvice() {
        // 构造未修改原因和意见的重新提交请求
        FundPoolAdjustFlowService service = buildModifyService();
        FundPoolAdjustAuditReq req = buildReq();

        assertThat(service.submitAdjustAudit(req).getAuditStatus()).isEqualTo("00");

        verify(mapper, never()).editAdjustLogReasonAdvice(any(), any(), any());
    }

    /** 已离开驳回待修改状态的申请不得保存文本。 */
    @Test
    public void modifySubmitShouldRejectChangedAuditStatus() {
        // 模拟待办尚未刷新但申请已经审批通过
        FundPoolAdjustFlowService service = buildModifyService();
        log.setAuditStatus("20");
        FundPoolAdjustAuditReq req = buildReq();
        req.setAdjustAdvice("更新意见");

        assertThatThrownBy(() -> service.submitAdjustAudit(req)).isInstanceOf(BizException.class)
                .hasMessageContaining("不处于驳回待修改状态");

        verify(mapper, never()).editAdjustLogReasonAdvice(any(), any(), any());
        verify(mapper, never()).editAdjustStepProcess(any(), any(), any(), any());
    }

    /** 修改待办的普通处理人必须是原申请人。 */
    @Test
    public void modifySubmitShouldRejectHandlerWhoIsNotInitiator() {
        // 模拟错误指派给其他人的修改待办
        FundPoolAdjustFlowService service = buildModifyService();
        step.setHandlerId("3");
        FundPoolAdjustAuditReq req = buildReq();
        req.setHandlerId("3");
        req.setAdjustReason("更新原因");

        assertThatThrownBy(() -> service.submitAdjustAudit(req)).isInstanceOf(BizException.class)
                .hasMessageContaining("仅发起人可修改调整原因和意见");

        verify(mapper, never()).editAdjustLogReasonAdvice(any(), any(), any());
    }

    /** 终止流程不能同时修改申请文本。 */
    @Test
    public void terminateShouldRejectReasonAdviceChanges() {
        // 构造终止修改流程且携带文本修改的请求
        FundPoolAdjustFlowService service = buildModifyService();
        FundPoolAdjustAuditReq req = buildReq();
        req.setProcessAction("reject");
        req.setAdjustReason("终止时修改原因");

        assertThatThrownBy(() -> service.submitAdjustAudit(req)).isInstanceOf(BizException.class)
                .hasMessage("仅驳回待修改提交时允许修改调整原因和意见");

        verify(mapper, never()).editAdjustLogReasonAdvice(any(), any(), any());
    }

    /** 修改原因和审批步骤写入之前必须已取得基金主档锁。 */
    @Test
    public void auditShouldLockFundBeforeReasonAndStepWrites() {
        // 构造发起人的修改待办与携带原因的重新提交请求
        FundPoolAdjustFlowService service = buildModifyService();
        // 构造允许修改原因的审批请求
        FundPoolAdjustAuditReq req = buildReq();
        req.setAdjustReason("复核原因");
        when(mapper.editAdjustLogReasonAdvice("FUND001", "复核原因", null)).thenReturn(1);

        service.submitAdjustAudit(req);

        InOrder order = inOrder(mapper);
        order.verify(mapper).queryFundListForUpdate(Collections.singletonList("FUND001.SH"));
        order.verify(mapper).editAdjustLogReasonAdvice("FUND001", "复核原因", null);
        order.verify(mapper).editAdjustStepProcess(10L, "submit", "submit", "已修改");
    }

    /** 等待主档锁期间临时代码已转正时，审批应提示刷新且零业务写入。 */
    @Test
    public void auditShouldRejectFundCodeChangedWhileWaitingForMasterLock() {
        // 构造原临时代码的有效待办，随后模拟锁等待期间完成代码替换
        FundPoolAdjustFlowService service = buildModifyService();
        FundAdjustLogBo replaced = new FundAdjustLogBo();
        replaced.setFundCode("OFFICIAL001.SH");
        when(mapper.queryAdjustLogListForAudit("FUND001"))
                .thenReturn(Collections.singletonList(log), Collections.singletonList(replaced));
        // 构造仍携带旧待办信息的审批请求
        FundPoolAdjustAuditReq req = buildReq();

        assertThatThrownBy(() -> service.submitAdjustAudit(req))
                .isInstanceOf(BizException.class).hasMessage("基金调库批次代码已发生变化，请刷新后重试");

        verify(mapper, never()).editAdjustLogReasonAdvice(any(), any(), any());
        verify(mapper, never()).editAdjustStepProcess(any(), any(), any(), any());
        verify(mapper, never()).editAdjustLogAuditStatus(any(), any());
        verify(mapper, never()).addFundPoolStatus(any());
    }

    /** 后续 O32 节点对有效临时代码转人工，保留现有流程版本。 */
    @Test
    public void temporaryFundShouldCreateManualPendingStepAtLaterO32Node() {
        // 构造修改节点后接 O32 和普通人工复核节点的流程
        FundPoolAdjustFlowService service = buildModifyService();
        // 将下一个审批节点配置为 O32，验证临时代码阻止自动推进
        configureNextStrategy("o32");
        when(tempFundCodeMapper.queryTemporaryCodeCountByFundCode("FUND001.SH")).thenReturn(1);
        // 构造重新提交后到达 O32 的审批请求
        FundPoolAdjustAuditReq req = buildReq();

        service.submitAdjustAudit(req);

        ArgumentCaptor<FundAdjustStepBo> created = ArgumentCaptor.forClass(FundAdjustStepBo.class);
        verify(mapper).addAdjustStep(created.capture());
        assertThat(created.getValue().getFlowNodeId()).isEqualTo(102L);
        assertThat(created.getValue().getApprovalStrategy()).isEqualTo("o32");
        assertThat(created.getValue().getStepStatus()).isEqualTo("pending");
        verify(mapper, never()).addFundPoolStatus(any());
    }

    /** 正式基金后续 O32 自动处理，继续推进至下一普通人工节点。 */
    @Test
    public void officialFundShouldKeepLaterO32AutomaticBehavior() {
        // 构造正式基金的修改待办和后续自动策略流程
        FundPoolAdjustFlowService service = buildModifyService();
        // 将后续节点配置为 O32，保留正式基金自动行为
        configureNextStrategy("o32");
        // 构造正式基金的重新提交请求
        FundPoolAdjustAuditReq req = buildReq();

        service.submitAdjustAudit(req);

        ArgumentCaptor<FundAdjustStepBo> created = ArgumentCaptor.forClass(FundAdjustStepBo.class);
        verify(mapper, times(2)).addAdjustStep(created.capture());
        assertThat(created.getAllValues().get(0).getFlowNodeId()).isEqualTo(102L);
        assertThat(created.getAllValues().get(0).getStepStatus()).isEqualTo("auto_process");
        assertThat(created.getAllValues().get(1).getFlowNodeId()).isEqualTo(103L);
        assertThat(created.getAllValues().get(1).getStepStatus()).isEqualTo("pending");
    }

    /** 临时代码后续 auto 节点继续自动处理，不受 O32 特例影响。 */
    @Test
    public void temporaryFundShouldKeepLaterAutoStrategyAutomatic() {
        // 构造临时代码的修改待办和后续自动策略流程
        FundPoolAdjustFlowService service = buildModifyService();
        // 将后续节点配置为普通 auto，验证只对 O32 改成人工
        configureNextStrategy("auto");
        when(tempFundCodeMapper.queryTemporaryCodeCountByFundCode("FUND001.SH")).thenReturn(1);
        // 构造到达 auto 节点的审批请求
        FundPoolAdjustAuditReq req = buildReq();

        service.submitAdjustAudit(req);

        ArgumentCaptor<FundAdjustStepBo> created = ArgumentCaptor.forClass(FundAdjustStepBo.class);
        verify(mapper, times(2)).addAdjustStep(created.capture());
        assertThat(created.getAllValues().get(0).getStepStatus()).isEqualTo("auto_process");
        assertThat(created.getAllValues().get(1).getStepStatus()).isEqualTo("pending");
    }

    /**
     * 在修改节点之后配置自动策略审批，再连接普通人工节点。
     *
     * @param strategy O32 或普通 auto 审批策略
     */
    private void configureNextStrategy(String strategy) {
        FlowNodeBo modify = flowMapper.queryFlowNodeById(101L);
        FlowNodeBo automated = new FlowNodeBo();
        automated.setId(102L);
        automated.setVersionId(1L);
        automated.setNodeType("approval");
        FlowNodeBo manual = new FlowNodeBo();
        manual.setId(103L);
        manual.setVersionId(1L);
        manual.setNodeType("approval");
        FlowEdgeBo submit = new FlowEdgeBo();
        submit.setFromNodeId(101L);
        submit.setToNodeId(102L);
        submit.setRouteAction("resubmit");
        FlowEdgeBo next = new FlowEdgeBo();
        next.setFromNodeId(102L);
        next.setToNodeId(103L);
        next.setRouteAction("approve");
        NodeApprovalConfigBo modifyConfig = new NodeApprovalConfigBo();
        modifyConfig.setId(201L);
        modifyConfig.setNodeId(101L);
        modifyConfig.setApprovalStrategy("initiator");
        NodeApprovalConfigBo strategyConfig = new NodeApprovalConfigBo();
        strategyConfig.setId(202L);
        strategyConfig.setNodeId(102L);
        strategyConfig.setApprovalStrategy(strategy);
        when(flowMapper.queryFlowNodeListByVersionId(1L)).thenReturn(Arrays.asList(modify, automated, manual));
        when(flowMapper.queryFlowEdgeListByVersionId(1L)).thenReturn(Arrays.asList(submit, next));
        when(flowMapper.queryApprovalConfigListByVersionId(1L)).thenReturn(Arrays.asList(modifyConfig, strategyConfig));
    }

    /** 构造修改节点经 resubmit 连线返回复核节点的审批服务。 */
    private FundPoolAdjustFlowService buildModifyService() {
        FundPoolAdjustFlowService service = new FundPoolAdjustFlowService();
        ReflectionTestUtils.setField(service, "fundPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "flowMapper", flowMapper);
        ReflectionTestUtils.setField(service, "tempFundCodeMapper", tempFundCodeMapper);
        step.setId(10L);
        step.setAdjustLogId(1L);
        step.setAdjustBatchNo("FUND001");
        step.setFlowNodeId(101L);
        step.setStepStatus("pending");
        step.setApprovalStrategy("initiator");
        step.setHandlerId("2");
        log.setId(1L);
        log.setFundCode("FUND001.SH");
        log.setAdjustBatchNo("FUND001");
        log.setAuditStatus("11");
        log.setAdjusterId("2");
        FlowNodeBo modify = new FlowNodeBo();
        modify.setId(101L);
        modify.setVersionId(1L);
        modify.setNodeType("approval");
        FlowNodeBo review = new FlowNodeBo();
        review.setId(102L);
        review.setVersionId(1L);
        review.setNodeType("approval");
        FlowEdgeBo edge = new FlowEdgeBo();
        edge.setFromNodeId(101L);
        edge.setToNodeId(102L);
        edge.setRouteAction("resubmit");
        NodeApprovalConfigBo config = new NodeApprovalConfigBo();
        config.setId(201L);
        config.setNodeId(101L);
        config.setApprovalStrategy("initiator");
        when(mapper.queryAdjustStepById(10L)).thenReturn(step);
        when(mapper.queryAdjustLogListForAudit("FUND001")).thenReturn(Collections.singletonList(log));
        FundInfoBo fund = new FundInfoBo();
        fund.setId(1L);
        fund.setFundCode("FUND001.SH");
        when(mapper.queryFundListForUpdate(anyList())).thenReturn(Collections.singletonList(fund));
        when(mapper.editAdjustStepProcess(any(), any(), any(), any())).thenReturn(1);
        when(mapper.editAdjustLogAuditStatus("FUND001", "00")).thenReturn(1);
        when(flowMapper.queryFlowNodeById(101L)).thenReturn(modify);
        when(flowMapper.queryFlowNodeListByVersionId(1L)).thenReturn(Arrays.asList(modify, review));
        when(flowMapper.queryFlowEdgeListByVersionId(1L)).thenReturn(Collections.singletonList(edge));
        when(flowMapper.queryApprovalConfigListByVersionId(1L)).thenReturn(Collections.singletonList(config));
        return service;
    }

    /** 构造原发起人在修改待办上的重新提交请求。 */
    private FundPoolAdjustAuditReq buildReq() {
        FundPoolAdjustAuditReq req = new FundPoolAdjustAuditReq();
        req.setStepId(10L);
        req.setAdjustLogId(1L);
        req.setAdjustBatchNo("FUND001");
        req.setHandlerId("2");
        req.setHandlerName("研究员1");
        req.setProcessAction("approve");
        req.setProcessComment("已修改");
        return req;
    }
}
