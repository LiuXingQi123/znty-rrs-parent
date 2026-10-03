package com.znty.rrs.service;

import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.bo.FundAdjustLogBo;
import com.znty.rrs.entity.bo.FundAdjustStepBo;
import com.znty.rrs.entity.bo.NodeApprovalConfigBo;
import com.znty.rrs.entity.fundpooladjust.FundPoolAdjustAuditReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.FundPoolAdjustMapper;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 基金池驳回后修改原因和意见的审批回归测试。 */
public class FundPoolAdjustFlowServiceTest {
    /** 基金调库数据访问组件 */
    private final FundPoolAdjustMapper mapper = mock(FundPoolAdjustMapper.class);
    /** 流程定义数据访问组件 */
    private final FlowMapper flowMapper = mock(FlowMapper.class);
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

    /** 构造修改节点经 resubmit 连线返回复核节点的审批服务。 */
    private FundPoolAdjustFlowService buildModifyService() {
        FundPoolAdjustFlowService service = new FundPoolAdjustFlowService();
        ReflectionTestUtils.setField(service, "fundPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "flowMapper", flowMapper);
        step.setId(10L);
        step.setAdjustLogId(1L);
        step.setAdjustBatchNo("FUND001");
        step.setFlowNodeId(101L);
        step.setStepStatus("pending");
        step.setApprovalStrategy("initiator");
        step.setHandlerId("2");
        log.setId(1L);
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
