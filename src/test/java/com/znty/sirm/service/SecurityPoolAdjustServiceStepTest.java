package com.znty.sirm.service;

import com.znty.sirm.common.enums.AttachmentPurpose;
import com.znty.sirm.common.enums.AttachmentCategory;

import com.znty.sirm.mapper.SecurityPoolAdjustMapper;
import com.znty.sirm.mapper.InvestmentPoolMapper;
import com.znty.sirm.mapper.FlowMapper;
import com.znty.sirm.entity.securitypooladjust.AdjustLogDto;
import com.znty.sirm.entity.securitypooladjust.AdjustCheckContext;
import com.znty.sirm.entity.securitypooladjust.AdjustCheckDto;
import com.znty.sirm.entity.securitypooladjust.AdjustCheckReq;
import com.znty.sirm.entity.securitypooladjust.AdjustSharedData;
import com.znty.sirm.entity.bo.FlowDefinitionBo;
import com.znty.sirm.entity.bo.FlowEdgeBo;
import com.znty.sirm.entity.bo.FlowNodeBo;
import com.znty.sirm.entity.bo.InvestmentPoolBo;
import com.znty.sirm.entity.bo.IpAdjustLogBo;
import com.znty.sirm.entity.bo.IpAdjustStepBo;
import com.znty.sirm.entity.bo.NodeApprovalConfigBo;
import com.znty.sirm.entity.bo.NodeApprovalHandlerBo;
import com.znty.sirm.entity.securitypooladjust.PoolDto;
import com.znty.sirm.entity.bo.PoolPermissionBo;
import com.znty.sirm.entity.bo.SecurityInfoBo;
import com.znty.sirm.entity.securitypooladjust.SecurityPoolAdjustReq;
import com.znty.sirm.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import com.znty.sirm.exception.BizException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** 验证调库记录应返回流程名称。 */
    @Test
    public void queryAdjustLogListShouldReturnFlowName() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolService investmentPoolService = mock(InvestmentPoolService.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));
        ReflectionTestUtils.setField(service, "investmentPoolService", investmentPoolService);

        IpAdjustLogBo log = new IpAdjustLogBo();
        log.setId(10L);
        log.setTargetPoolId(3L);
        log.setFlowName("信用债入库审批流程");
        when(mapper.queryAdjustLogList("110010123", "BATCH001")).thenReturn(Collections.singletonList(log));
        when(investmentPoolService.queryPoolFullNameMap()).thenReturn(Collections.singletonMap(3L, "信用债大库/二级库"));

        SecurityPoolAdjustReq req = new SecurityPoolAdjustReq();
        req.setSecurityCode("110010123");
        req.setAdjustBatchNo("BATCH001");

        List<AdjustLogDto> result = service.queryAdjustLogList(req);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFlowName()).isEqualTo("信用债入库审批流程");
    }

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
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "investmentPoolMapper", mapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", adjustMapper);
        when(mapper.queryPoolList()).thenReturn(Arrays.asList(
                // 构建投资池测试数据
                buildPool(1L, null, "根库"),
                // 构建投资池测试数据
                buildPool(2L, 1L, "一级库")));
        when(mapper.queryMutexRelationList()).thenReturn(Collections.emptyList());
        PoolDto poolCount = new PoolDto();
        poolCount.setId(2L);
        poolCount.setCurrentCount(3);
        when(adjustMapper.queryPoolCurrentCountList()).thenReturn(Collections.singletonList(poolCount));

        SecurityPoolAdjustReq req = new SecurityPoolAdjustReq();
        req.setCurrentUserId("1001");

        List<PoolDto> result = service.queryAdjustPoolList(req);

        assertThat(result).extracting(PoolDto::getId).containsExactly(1L, 2L);
        assertThat(result).extracting(PoolDto::getCurrentCount).containsExactly(0, 3);
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
        ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));

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
        ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));

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
        ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));
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
        item.setItemTag("manual");
        item.setAdjustGroupKey("manual_10_调入");
        req.setItems(Collections.singletonList(item));

        // 构建调库提交共享数据测试数据
        Object shared = buildSubmitSharedData();

        List<Long> result = ReflectionTestUtils.invokeMethod(service, "executeInboundSubmit", req, shared);

        ArgumentCaptor<IpAdjustLogBo> logCaptor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        ArgumentCaptor<IpAdjustLogBo> statusCaptor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(mapper).addAdjustLog(logCaptor.capture());
        verify(mapper).addPoolStatus(statusCaptor.capture());
        assertThat(logCaptor.getValue().getAuditStatus()).isEqualTo("20");
        assertThat(logCaptor.getValue().getAdjustBatchNo()).endsWith("3001");
        assertThat(statusCaptor.getValue().getAuditStatus()).isEqualTo("20");
        assertThat(statusCaptor.getValue().getAdjustBatchNo()).isEqualTo(logCaptor.getValue().getAdjustBatchNo());
        assertThat(result).containsExactly(99L);
    }

    /** 验证 executeOutboundSubmitShouldCreateLogWhenFlowIsDirect 测试场景。 */
    @Test
    public void executeOutboundSubmitShouldCreateLogWhenFlowIsDirect() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));
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
        item.setItemTag("manual");
        item.setAdjustGroupKey("manual_10_调出");
        req.setItems(Collections.singletonList(item));

        // 构建调库提交共享数据测试数据
        Object shared = buildSubmitSharedData();

        List<Long> result = ReflectionTestUtils.invokeMethod(service, "executeOutboundSubmit", req, shared);

        ArgumentCaptor<IpAdjustLogBo> captor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(mapper).addAdjustLog(captor.capture());
        verify(mapper).deletePoolStatusSoft("S001", 10L);
        assertThat(captor.getValue().getAuditStatus()).isEqualTo("20");
        assertThat(captor.getValue().getAdjustBatchNo()).endsWith("3001");
        assertThat(result).containsExactly(88L);
    }

    /** 验证共享批次号上下文下连续提交应递增批次号。 */
    @Test
    public void executeInboundSubmitShouldIncreaseBatchNoWithSharedContext() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));
        doAnswer(invocation -> {
            IpAdjustLogBo bo = (IpAdjustLogBo) invocation.getArguments()[0];
            bo.setId(99L);
            return 1;
        }).when(mapper).addAdjustLog(any(IpAdjustLogBo.class));

        Object batchNoContext = buildBatchNoContext();
        SecurityPoolAdjustSubmitReq firstReq = buildDirectInboundSubmitReq("S001", "S001_manual_10_调入");
        SecurityPoolAdjustSubmitReq secondReq = buildDirectInboundSubmitReq("S002", "S002_manual_10_调入");

        Object firstShared = buildSubmitSharedData(batchNoContext);
        Object secondShared = buildSubmitSharedData(batchNoContext);

        ReflectionTestUtils.invokeMethod(service, "executeInboundSubmit", firstReq, firstShared);
        ReflectionTestUtils.invokeMethod(service, "executeInboundSubmit", secondReq, secondShared);

        ArgumentCaptor<IpAdjustLogBo> logCaptor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(mapper, times(2)).addAdjustLog(logCaptor.capture());
        assertThat(logCaptor.getAllValues()).extracting(IpAdjustLogBo::getAdjustBatchNo)
                .doesNotHaveDuplicates();
        assertThat(logCaptor.getAllValues().get(0).getAdjustBatchNo()).endsWith("3001");
        assertThat(logCaptor.getAllValues().get(1).getAdjustBatchNo()).endsWith("3002");
    }

    /** 验证普通提交应同时绑定本地上传附件和报告库来源附件。 */
    @Test
    public void bindSubmitAttachmentsShouldBindUploadedFilesAndCopyReportSources() {
        SysAttachmentService attachmentService = mock(SysAttachmentService.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "sysAttachmentService", attachmentService);

        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        item.setCreditReportFileIndexes(Collections.singletonList(0));
        item.setMaterialFileIndexes(Collections.singletonList(1));
        item.setCreditReportSourceAttachmentIds(Collections.singletonList(7L));
        item.setMaterialSourceAttachmentIds(Collections.singletonList(8L));

        ReflectionTestUtils.invokeMethod(service, "bindSubmitAttachments", 88L, item, null, "1001");

        verify(attachmentService).bindAttachments(88L, Collections.singletonList(0),
                AttachmentCategory.CREDIT_REPORT_HAND.getCode(), null);
        verify(attachmentService).bindAttachments(88L, Collections.singletonList(1),
                AttachmentCategory.MATERIAL_HAND.getCode(), null);
        verify(attachmentService).copyReportAttachments(88L, Collections.singletonList(7L),
                AttachmentPurpose.CREDIT_REPORT.getCode(), "1001");
        verify(attachmentService).copyReportAttachments(88L, Collections.singletonList(8L),
                AttachmentPurpose.MATERIAL.getCode(), "1001");
    }

    /** 验证白名单直通流程应记录调入批次号及流程步骤。 */
    @Test
    public void executeInboundSubmitShouldCreateStepsWithBatchNoForWhitelistFlow() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));
        doAnswer(invocation -> {
            IpAdjustLogBo bo = (IpAdjustLogBo) invocation.getArguments()[0];
            bo.setId(106L);
            return 1;
        }).when(mapper).addAdjustLog(any(IpAdjustLogBo.class));

        FlowNodeBo start = buildNode(10601L, "n1", "start", 1);
        FlowNodeBo submitter = buildNode(10602L, "n2", "approval", 2);
        submitter.setLabel("研究员A发起");
        FlowNodeBo end = buildNode(10603L, "n3", "end", 3);
        NodeApprovalConfigBo submitterConfig = buildConfig(106L, submitter.getId(), "initiator");
        Object snapshot = buildSnapshot(
                Arrays.asList(start, submitter, end),
                Arrays.asList(buildEdge(start.getId(), submitter.getId()), buildEdge(submitter.getId(), end.getId())),
                Collections.singletonList(submitterConfig),
                Collections.<Long, List<NodeApprovalHandlerBo>>emptyMap());
        Map<Long, Object> snapshotMap = new HashMap<>();
        snapshotMap.put(106L, snapshot);

        SecurityPoolAdjustSubmitReq req = new SecurityPoolAdjustSubmitReq();
        req.setSecurityCode("S001");
        req.setSecurityShortName("test");
        req.setSecurityType("bond");
        req.setAdjustType("manual");
        req.setAdjusterId("1001");
        req.setAdjusterName("admin");
        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        item.setAdjustMode("调入");
        item.setTargetPoolId(10L);
        item.setTargetPoolName("pool");
        item.setPoolType("credit_bond");
        item.setItemTag("manual");
        item.setAdjustGroupKey("manual_10_调入");
        item.setFlowId(106L);
        item.setFlowKey("bond:whitelist-inbound");
        item.setFlowType("whitelistInbound");
        req.setItems(Collections.singletonList(item));

        // 构建包含白名单流程快照的提交共享数据
        Object shared = buildSubmitSharedData(snapshotMap);
        ReflectionTestUtils.invokeMethod(service, "executeInboundSubmit", req, shared);

        ArgumentCaptor<IpAdjustLogBo> logCaptor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        ArgumentCaptor<IpAdjustStepBo> stepCaptor = ArgumentCaptor.forClass(IpAdjustStepBo.class);
        verify(mapper).addAdjustLog(logCaptor.capture());
        verify(mapper, times(3)).addAdjustStep(stepCaptor.capture());
        String batchNo = logCaptor.getValue().getAdjustBatchNo();
        assertThat(batchNo).endsWith("1001");
        assertThat(stepCaptor.getAllValues()).extracting(IpAdjustStepBo::getAdjustBatchNo)
                .containsOnly(batchNo);
    }

    /** 验证信用债在库调整应按上调或下调流程编码获取流程。 */
    @Test
    public void resolveAdjustFlowOptionsShouldQueryFlowByCreditBondAdjustDirection() {
        FlowMapper flowMapper = mock(FlowMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "flowMapper", flowMapper);
        FlowDefinitionBo upgradeFlow = buildFlowDefinition(101L, "bond:standard-upgrade", "债券标准升库流程");
        FlowDefinitionBo downgradeFlow = buildFlowDefinition(102L, "bond:standard-downgrade", "债券标准降库流程");
        when(flowMapper.queryActiveFlowByKey("bond:standard-upgrade")).thenReturn(upgradeFlow);
        when(flowMapper.queryActiveFlowByKey("bond:standard-downgrade")).thenReturn(downgradeFlow);

        // 校验目标池层级高于当前池时使用上调流程
        AdjustCheckDto.FlowOption upgradeOption = resolveCreditBondFlowOption(service, 1, 2);
        assertThat(upgradeOption.getFlowType()).isEqualTo("upgradeInbound");
        assertThat(upgradeOption.getFlowId()).isEqualTo(101L);
        assertThat(upgradeOption.getFlowKey()).isEqualTo("bond:standard-upgrade");

        // 校验目标池层级低于当前池时使用下调流程
        AdjustCheckDto.FlowOption downgradeOption = resolveCreditBondFlowOption(service, 3, 2);
        assertThat(downgradeOption.getFlowType()).isEqualTo("downgradeInbound");
        assertThat(downgradeOption.getFlowId()).isEqualTo(102L);
        assertThat(downgradeOption.getFlowKey()).isEqualTo("bond:standard-downgrade");
    }

    /** 验证手工调入失败时应同步阻断派生的互斥调出项。 */
    @Test
    public void executeInAdjustCheckShouldBlockMutexOutboundWhenManualInboundFails() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        when(mapper.queryPoolCurrentCount(any(Long.class))).thenReturn(1);

        InvestmentPoolBo rootPool = buildPool(1L, null, "信用债大库");
        InvestmentPoolBo currentPool = buildPool(2L, 1L, "二级库");
        InvestmentPoolBo targetPool = buildPool(3L, 1L, "三级库");
        InvestmentPoolBo forbiddenPool = buildPool(4L, null, "禁投池");
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        poolMap.put(rootPool.getId(), rootPool);
        poolMap.put(currentPool.getId(), currentPool);
        poolMap.put(targetPool.getId(), targetPool);
        poolMap.put(forbiddenPool.getId(), forbiddenPool);

        Map<String, List<Long>> targetRelations = new HashMap<>();
        targetRelations.put("in_restrict", Collections.singletonList(forbiddenPool.getId()));
        targetRelations.put("in_mutex", Collections.singletonList(currentPool.getId()));
        Map<Long, Map<String, List<Long>>> relationMap = new HashMap<>();
        relationMap.put(targetPool.getId(), targetRelations);

        Set<Long> currentPoolIds = new HashSet<>(Arrays.asList(currentPool.getId(), forbiddenPool.getId()));
        AdjustSharedData shared = new AdjustSharedData();
        shared.setSecurityInfo(new SecurityInfoBo());
        shared.setPoolMap(poolMap);
        shared.setCurrentPoolIds(currentPoolIds);
        shared.setPoolRelationMap(relationMap);
        shared.setRequestInPoolIds(Collections.singleton(targetPool.getId()));
        shared.setRequestOutPoolIds(Collections.<Long>emptySet());

        AdjustCheckReq.CheckItem item = new AdjustCheckReq.CheckItem();
        item.setTargetPoolId(targetPool.getId());
        item.setPoolType("credit_bond");
        item.setAdjustMode("调入");
        AdjustCheckReq req = new AdjustCheckReq();
        req.setItems(Collections.singletonList(item));
        Set<String> coveredKeys = new HashSet<>();
        coveredKeys.add(targetPool.getId() + "_调入");

        List<AdjustCheckDto.CheckResultItem> results = ReflectionTestUtils.invokeMethod(
                service, "executeInAdjustCheck", req, shared, coveredKeys);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).isCanAdjust()).isFalse();
        assertThat(results.get(0).getFailReasons()).contains(
                "证券在调入限制池中，无法操作：禁投池");
        assertThat(results.get(1).getItemTag()).isEqualTo("mutex");
        assertThat(results.get(1).isCanAdjust()).isFalse();
        assertThat(results.get(1).getFailReasons().get(0))
                .isEqualTo("关联手工调入项“信用债大库/三级库”校验未通过");
    }

    /** 构造信用债在库调整场景并获取流程候选项。 */
    private AdjustCheckDto.FlowOption resolveCreditBondFlowOption(
            SecurityPoolAdjustService service, int targetSort, int currentSort) {
        InvestmentPoolBo targetPool = buildPool(2L, 1L, "目标池");
        targetPool.setPoolType("credit_bond");
        targetPool.setInnerSort(targetSort);
        InvestmentPoolBo currentPool = buildPool(3L, 1L, "当前池");
        currentPool.setPoolType("credit_bond");
        currentPool.setInnerSort(currentSort);

        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        poolMap.put(targetPool.getId(), targetPool);
        poolMap.put(currentPool.getId(), currentPool);
        AdjustSharedData shared = new AdjustSharedData();
        shared.setPoolMap(poolMap);
        shared.setCurrentPoolIds(Collections.singleton(currentPool.getId()));

        AdjustCheckDto.CheckResultItem item = new AdjustCheckDto.CheckResultItem();
        item.setTargetPoolId(targetPool.getId());
        item.setAdjustMode("调入");
        item.setItemTag("manual");
        item.setCanAdjust(true);

        List<AdjustCheckDto.FlowOption> options = ReflectionTestUtils.invokeMethod(
                service, "resolveAdjustFlowOptionsForItem", new AdjustCheckReq(), shared, item);
        return options.get(0);
    }

    /** 构建流程定义测试数据。 */
    private FlowDefinitionBo buildFlowDefinition(Long id, String flowKey, String name) {
        FlowDefinitionBo flow = new FlowDefinitionBo();
        flow.setId(id);
        flow.setFlowKey(flowKey);
        flow.setName(name);
        return flow;
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
        return buildSubmitSharedData(new HashMap<Long, Object>());
    }

    /** 构建包含流程快照的调库提交共享数据测试数据。 */
    private Object buildSubmitSharedData(Map<Long, Object> snapshotMap) throws Exception {
        Object batchNoContext = buildBatchNoContext();
        return buildSubmitSharedData(snapshotMap, batchNoContext);
    }

    /** 构建指定批次号上下文的调库提交共享数据测试数据。 */
    private Object buildSubmitSharedData(Object batchNoContext) throws Exception {
        return buildSubmitSharedData(new HashMap<Long, Object>(), batchNoContext);
    }

    /** 构建批次号上下文测试数据。 */
    private Object buildBatchNoContext() throws Exception {
        Class<?> batchNoClass = Class.forName("com.znty.sirm.service.SecurityPoolAdjustService$BatchNoContext");
        Constructor<?> batchNoConstructor = batchNoClass.getDeclaredConstructors()[0];
        batchNoConstructor.setAccessible(true);
        return batchNoConstructor.newInstance();
    }

    /** 构建包含流程快照和批次号上下文的调库提交共享数据测试数据。 */
    private Object buildSubmitSharedData(Map<Long, Object> snapshotMap, Object batchNoContext) throws Exception {
        Class<?> sharedClass = Class.forName("com.znty.sirm.service.SecurityPoolAdjustService$SubmitSharedData");
        Constructor<?> constructor = sharedClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(
                new SecurityInfoBo(),
                new HashMap<Long, com.znty.sirm.entity.bo.InvestmentPoolBo>(),
                Collections.<Long>emptySet(),
                new HashMap<Long, Map<String, List<Long>>>(),
                false,
                false,
                false,
                snapshotMap,
                batchNoContext);
    }

    /** 构建直通调入提交请求。 */
    private SecurityPoolAdjustSubmitReq buildDirectInboundSubmitReq(String securityCode, String groupKey) {
        SecurityPoolAdjustSubmitReq req = new SecurityPoolAdjustSubmitReq();
        req.setSecurityCode(securityCode);
        req.setSecurityShortName("test");
        req.setSecurityType("bond");
        req.setAdjustType("manual");
        req.setAdjusterId("1001");
        req.setAdjusterName("admin");

        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        item.setAdjustMode("调入");
        item.setTargetPoolId(10L);
        item.setTargetPoolName("pool");
        item.setPoolType("special_account");
        item.setItemTag("manual");
        item.setAdjustGroupKey(groupKey);
        req.setItems(Collections.singletonList(item));
        return req;
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
    private PoolPermissionBo buildPermission(Long poolId, String handlerType, Long handlerId) {
        PoolPermissionBo permission = new PoolPermissionBo();
        permission.setPoolId(poolId);
        permission.setPermissionType("adjustable");
        permission.setHandlerType(handlerType);
        permission.setHandlerId(handlerId);
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
            handler.setHandlerType("user");
            handler.setHandlerId(i);
            handler.setHandlerName("user" + i);
            handlers.add(handler);
        }
        map.put(configId, handlers);
        return map;
    }
}
