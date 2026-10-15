package com.znty.rrs.service;

import com.znty.rrs.entity.bo.AdjustSecuritySnapshotCrmwBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.bo.FlowVersionBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpAdjustStepBo;
import com.znty.rrs.entity.bo.NodeApprovalConfigBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.common.SecurityMarketDto;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckDto;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckReq;
import com.znty.rrs.entity.crmwpooladjust.CrmwPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.CrmwPoolAdjustMapper;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CRMW 仅展开标的债券同券代码，覆盖逐码校验、提交、快照和审批落池。 */
public class CrmwRelatedMarketAdjustTest {

    /** CRMW 调库服务。 */
    private CrmwPoolAdjustService service;
    /** CRMW 数据访问组件。 */
    private CrmwPoolAdjustMapper mapper;
    /** 市场及同券关系服务。 */
    private SecurityMarketService markets;
    /** 投资池数据访问组件。 */
    private InvestmentPoolMapper pools;
    /** 测试目标池。 */
    private InvestmentPoolBo pool;
    /** 记录本次提交生成的日志。 */
    private List<IpAdjustLogBo> logs;

    /** 配置三市场标的主档及单一 CRMW 凭证，不连接外部数据库。 */
    @Before
    public void setUp() {
        service = new CrmwPoolAdjustService();
        mapper = mock(CrmwPoolAdjustMapper.class);
        markets = mock(SecurityMarketService.class);
        pools = mock(InvestmentPoolMapper.class);
        ReflectionTestUtils.setField(service, "crmwPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "securityMarketService", markets);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", pools);
        ReflectionTestUtils.setField(service, "flowMapper", mock(FlowMapper.class));
        ReflectionTestUtils.setField(service, "ratingDowngradeChecker", mock(RatingDowngradeChecker.class));
        ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));
        ReflectionTestUtils.setField(service, "reportService", mock(ReportService.class));
        SecurityPoolAdjustService securities = mock(SecurityPoolAdjustService.class);
        when(securities.applySelectedRatingCompany(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new SecurityPoolAdjustService.SelectedRatingCompanyData());
        ReflectionTestUtils.setField(service, "securityPoolAdjustService", securities);
        pool = new InvestmentPoolBo();
        pool.setId(18L);
        pool.setPoolName("CRMW库");
        pool.setPoolType("crmw");
        pool.setStatus("enabled");
        when(pools.queryPoolList()).thenReturn(Collections.singletonList(pool));
        for (String code : Arrays.asList("A.IB", "A.SH", "A.SZ", "C.IB")) {
            SecurityInfoBo info = new SecurityInfoBo();
            info.setWindCode(code);
            info.setShortName("名称" + code);
            info.setSecurityType("C.IB".equals(code) ? "crmw" : "corporate_bond");
            when(mapper.querySecurityBoByCode(code)).thenReturn(info);
        }
        when(mapper.queryCategoryTypeBySecurityType("corporate_bond")).thenReturn("bond");
        SecurityMarketDto sh = new SecurityMarketDto();
        sh.setWindCode("A.SH");
        sh.setMarketCode("SSE");
        SecurityMarketDto sz = new SecurityMarketDto();
        sz.setWindCode("A.SZ");
        sz.setMarketCode("SZSE");
        when(markets.queryRelatedMarketCodes("A.IB")).thenReturn(Arrays.asList(sh, sz));
        logs = new ArrayList<>();
        AtomicLong sequence = new AtomicLong();
        when(mapper.addAdjustLog(any())).thenAnswer(call -> {
            IpAdjustLogBo log = call.getArgument(0);
            log.setId(sequence.incrementAndGet());
            logs.add(log);
            return 1;
        });
        when(mapper.addPoolStatus(any())).thenReturn(1);
    }

    /** 一张凭证绑定三市场同券代码，共用主项分组且不展开凭证。 */
    @Test
    public void checkShouldExpandOnlyUnderlyingBondCodes() {
        // 构建并执行完整单笔校验入口
        AdjustCheckDto result = service.checkCrmwAdjust(checkReq("调入"));
        assertThat(result.getItems()).extracting(AdjustCheckDto.CheckResultItem::getSecurityCode)
                .containsExactly("A.IB", "A.SH", "A.SZ");
        assertThat(result.getItems()).allMatch(AdjustCheckDto.CheckResultItem::isCanAdjust);
        assertThat(result.getItems()).extracting(AdjustCheckDto.CheckResultItem::getCrmwScode)
                .containsOnly("C.IB");
        assertThat(result.getItems()).extracting(AdjustCheckDto.CheckResultItem::getAdjustGroupKey)
                .containsOnly(result.getItems().get(0).getAdjustGroupKey());
        assertThat(result.getItems().get(1).getItemTag()).isEqualTo("related");
        assertThat(result.getItems().get(1).getSourceSecurityCode()).isEqualTo("C.IB|A.IB");
        verify(markets, never()).queryRelatedMarketCodes("C.IB");
    }

    /** 标的关联码同时展开主券触发的联动池，保持原分组。 */
    @Test
    public void relatedCodesShouldExpandLinkedPoolItems() {
        InvestmentPoolBo linked = new InvestmentPoolBo();
        linked.setId(19L);
        linked.setPoolType("crmw");
        linked.setPoolName("CRMW联动库");
        when(pools.queryPoolList()).thenReturn(Arrays.asList(pool, linked));
        PoolRelationBo relation = new PoolRelationBo();
        relation.setPoolId(18L);
        relation.setRelationType("in_linked");
        relation.setRelationPoolId(19L);
        when(mapper.queryAllPoolRelationList()).thenReturn(Collections.singletonList(relation));
        // 执行主项、联动项及其两市场关联项的完整校验
        List<AdjustCheckDto.CheckResultItem> items = service.checkCrmwAdjust(checkReq("调入")).getItems();
        assertThat(items).hasSize(6);
        assertThat(items).filteredOn(item -> "A.SH".equals(item.getSecurityCode()))
                .extracting(AdjustCheckDto.CheckResultItem::getTargetPoolId).containsExactly(18L, 19L);
        assertThat(items).extracting(AdjustCheckDto.CheckResultItem::getAdjustGroupKey)
                .containsOnly(items.get(0).getAdjustGroupKey());
    }

    /** 关联项累计主项和此前关联项的容量占用。 */
    @Test
    public void checkShouldAccumulateCapacityAcrossCodes() {
        pool.setMaxCapacity(2L);
        // 执行完整调入校验
        List<AdjustCheckDto.CheckResultItem> items = service.checkCrmwAdjust(checkReq("调入")).getItems();
        assertThat(items).extracting(AdjustCheckDto.CheckResultItem::isCanAdjust)
                .containsExactly(true, true, false);
        assertThat(items.get(2).getFailReasons()).anyMatch(reason -> reason.contains("持仓上限"));
    }

    /** 每个代码使用自身市场，并独立检查组合是否已在池。 */
    @Test
    public void checkShouldUseEachCodesMarketAndPoolStatus() {
        pool.setMarketCodes("[\"NIB\"]");
        when(markets.matchesPoolMarkets(anyString(), any()))
                .thenAnswer(call -> "A.IB".equals(((SecurityInfoBo) call.getArgument(1)).getWindCode()));
        when(mapper.querySecurityCurrentPoolIdList("A.SZ", "C.IB", "crmw"))
                .thenReturn(Collections.singletonList(18L));
        // 执行完整调入校验
        List<AdjustCheckDto.CheckResultItem> items = service.checkCrmwAdjust(checkReq("调入")).getItems();
        assertThat(items.get(0).isCanAdjust()).isTrue();
        assertThat(items.get(1).getFailReasons()).contains("证券不在本池投资市场范围内");
        assertThat(items.get(2).getFailReasons()).contains("信用缓释凭证与标的证券组合已在目标投资池中");
    }

    /** 调出按实际组合的在池状态与冻结期判断。 */
    @Test
    public void outboundShouldCheckEachCombinationAndEntryTime() {
        pool.setFrozenPeriodIn(10);
        for (String code : Arrays.asList("A.IB", "A.SH")) {
            when(mapper.querySecurityCurrentPoolIdList(code, "C.IB", "crmw"))
                    .thenReturn(Collections.singletonList(18L));
        }
        when(mapper.queryCrmwComboInPool("C.IB", "crmw", "A.IB", 18L)).thenReturn(true);
        when(mapper.queryCrmwPoolEntryTime("A.IB", "C.IB", "crmw", 18L))
                .thenReturn(new Date(0));
        when(mapper.queryCrmwPoolEntryTime("A.SH", "C.IB", "crmw", 18L)).thenReturn(new Date());
        // 执行完整调出校验
        List<AdjustCheckDto.CheckResultItem> items = service.checkCrmwAdjust(checkReq("调出")).getItems();
        assertThat(items.get(0).isCanAdjust()).isTrue();
        assertThat(items.get(1).getFailReasons()).contains("证券仍在目标投资池冻结期内");
        assertThat(items.get(2).isCanAdjust()).isFalse();
        verify(mapper).queryCrmwPoolEntryTime("A.SH", "C.IB", "crmw", 18L);
    }

    /** 空关联不扩批，缺失主档保留明确失败项，主项失败阻断关联项。 */
    @Test
    public void checkShouldHandleMissingRelatedMasterAndFailedManual() {
        when(mapper.querySecurityBoByCode("A.SH")).thenReturn(null);
        // 缺失关联主档必须在校验表明确失败
        List<AdjustCheckDto.CheckResultItem> items = service.checkCrmwAdjust(checkReq("调入")).getItems();
        assertThat(items.get(1).getFailReasons()).contains("关联证券代码A.SH在系统中不存在");
        pool.setLockFlag(1);
        // 主项失败时所有关联项均不可提交
        assertThat(service.checkCrmwAdjust(checkReq("调入")).getItems())
                .noneMatch(AdjustCheckDto.CheckResultItem::isCanAdjust);
        when(markets.queryRelatedMarketCodes("A.IB")).thenReturn(Collections.emptyList());
        // 无同券关联时保持原单组合行为
        assertThat(service.checkCrmwAdjust(checkReq("调入")).getItems()).hasSize(1);
    }

    /** 关联组合的活动池组既阻断校验，也阻断绕过预校验的提交。 */
    @Test
    public void relatedPendingProcessShouldBlockCheckAndSubmit() {
        when(mapper.queryPendingManualTargetPoolIdList("A.SH", "C.IB", "crmw", null))
                .thenReturn(Collections.singletonList(18L));
        // 执行校验并检查关联项失败原因
        List<AdjustCheckDto.CheckResultItem> items = service.checkCrmwAdjust(checkReq("调入")).getItems();
        assertThat(items.get(1).getFailReasons()).anyMatch(reason -> reason.contains("进行中的调库流程"));
        // 模拟客户端强行提交该关联项，提交阶段仍应拒绝
        assertThatThrownBy(() -> service.submitAdjustLog(submitReq("调入"), null,
                new CrmwPoolAdjustService.BatchNoContext())).isInstanceOf(BizException.class)
                .hasMessageContaining("C.IB/A.SH");
        verify(mapper, never()).addAdjustLog(any());
    }

    /** 直通三代码同批落池，快照必须保留实际代码及证券身份。 */
    @Test
    public void directInboundShouldPreserveActualCodeInLogsStatusAndSnapshots() {
        // 构建带证券信息的三代码提交请求
        CrmwPoolAdjustSubmitReq req = submitReq("调入");
        SecurityInfoBo edited = new SecurityInfoBo();
        edited.setWindCode("A.IB");
        edited.setShortName("名称A.IB");
        edited.setAnalysis("本次分析");
        req.setSecurityInfo(edited);
        service.submitAdjustLog(req, null, new CrmwPoolAdjustService.BatchNoContext());
        assertThat(logs).extracting(IpAdjustLogBo::getSecurityCode).containsExactly("A.IB", "A.SH", "A.SZ");
        assertThat(logs).extracting(IpAdjustLogBo::getAdjustBatchNo).containsOnly(logs.get(0).getAdjustBatchNo());
        assertThat(logs).extracting(IpAdjustLogBo::getAuditStatus).containsOnly("20");
        assertThat(logs.get(1).getAdjustType()).isEqualTo("关联调整");
        ArgumentCaptor<IpAdjustLogBo> status = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(mapper, times(3)).addPoolStatus(status.capture());
        assertThat(status.getAllValues()).extracting(IpAdjustLogBo::getSecurityCode)
                .containsExactly("A.IB", "A.SH", "A.SZ");
        ArgumentCaptor<AdjustSecuritySnapshotCrmwBo> snapshots = ArgumentCaptor.forClass(AdjustSecuritySnapshotCrmwBo.class);
        verify(mapper, times(3)).addAdjustSecuritySnapshotCrmw(snapshots.capture());
        assertThat(snapshots.getAllValues()).extracting(AdjustSecuritySnapshotCrmwBo::getWindCode)
                .containsExactly("A.IB", "A.SH", "A.SZ");
        assertThat(snapshots.getAllValues()).extracting(AdjustSecuritySnapshotCrmwBo::getAnalysis).containsOnly("本次分析");
    }

    /** 直通出池只删除相同凭证下每个实际标的组合。 */
    @Test
    public void directOutboundShouldDeleteEachActualCombination() {
        for (String code : Arrays.asList("A.IB", "A.SH", "A.SZ")) {
            when(mapper.queryCrmwComboInPool("C.IB", "crmw", code, 18L)).thenReturn(true);
            when(mapper.deletePoolStatusSoft(code, "C.IB", "crmw", 18L)).thenReturn(1);
        }
        // 提交三市场组合出池
        service.submitAdjustLog(submitReq("调出"), null, new CrmwPoolAdjustService.BatchNoContext());
        for (String code : Arrays.asList("A.IB", "A.SH", "A.SZ")) {
            verify(mapper).deletePoolStatusSoft(code, "C.IB", "crmw", 18L);
        }
        assertThat(logs).hasSize(3);
        verify(mapper, never()).deletePoolStatusSoft(anyString(), eq("OTHER.IB"), anyString(), any());
    }

    /** 非直通申请只创建主项的流程步骤，关联项同批等待审批而不提前落池。 */
    @Test
    public void pendingSubmissionShouldShareOnlyManualSteps() {
        FlowMapper flows = (FlowMapper) ReflectionTestUtils.getField(service, "flowMapper");
        FlowDefinitionBo definition = new FlowDefinitionBo();
        definition.setId(9L);
        when(flows.queryFlowById(9L)).thenReturn(definition);
        FlowVersionBo version = new FlowVersionBo();
        version.setId(90L);
        version.setStatus("active");
        when(flows.queryFlowVersionByFlowIdList(9L, null)).thenReturn(Collections.singletonList(version));
        List<FlowNodeBo> nodes = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            FlowNodeBo node = new FlowNodeBo();
            node.setId((long) index + 1);
            node.setNodeType(Arrays.asList("start", "approval", "end").get(index));
            node.setSortOrder(index + 1);
            nodes.add(node);
        }
        when(flows.queryFlowNodeListByVersionId(90L)).thenReturn(nodes);
        List<FlowEdgeBo> edges = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            FlowEdgeBo edge = new FlowEdgeBo();
            edge.setFromNodeId((long) index + 1);
            edge.setToNodeId((long) index + 2);
            edge.setRouteAction("approve");
            edges.add(edge);
        }
        when(flows.queryFlowEdgeListByVersionId(90L)).thenReturn(edges);
        NodeApprovalConfigBo config = new NodeApprovalConfigBo();
        config.setId(20L);
        config.setNodeId(2L);
        config.setApprovalStrategy("preempt");
        when(flows.queryApprovalConfigListByVersionId(90L)).thenReturn(Collections.singletonList(config));
        // 构建同组三代码提交，仅主项选择流程，关联项从主项继承
        CrmwPoolAdjustSubmitReq req = submitReq("调入");
        req.getItems().get(0).setFlowId(9L);
        service.submitAdjustLog(req, null, new CrmwPoolAdjustService.BatchNoContext());
        assertThat(logs).hasSize(3);
        assertThat(logs).extracting(IpAdjustLogBo::getAuditStatus).containsOnly("00");
        assertThat(logs).extracting(IpAdjustLogBo::getFlowId).containsOnly(9L);
        assertThat(logs).extracting(IpAdjustLogBo::getAdjustBatchNo).containsOnly(logs.get(0).getAdjustBatchNo());
        ArgumentCaptor<IpAdjustStepBo> steps = ArgumentCaptor.forClass(IpAdjustStepBo.class);
        verify(mapper, times(2)).addAdjustStep(steps.capture());
        assertThat(steps.getAllValues()).extracting(IpAdjustStepBo::getAdjustLogId).containsOnly(logs.get(0).getId());
        assertThat(steps.getAllValues()).extracting(IpAdjustStepBo::getStepStatus).contains("pending");
        verify(mapper, never()).addPoolStatus(any());
    }

    /** 人工审批完成时同批关联日志一起复核、落池。 */
    @Test
    public void finalApprovalShouldApplyAllRelatedLogsTogether() {
        // 构建已提交的同批主项和关联项
        for (String code : Arrays.asList("A.IB", "A.SH", "A.SZ")) {
            IpAdjustLogBo log = new IpAdjustLogBo();
            log.setId((long) logs.size() + 1);
            log.setSecurityCode(code);
            log.setCrmwScode("C.IB");
            log.setCrmwStype("crmw");
            log.setTargetPoolId(18L);
            log.setAdjustMode("调入");
            log.setAuditStatus("00");
            logs.add(log);
        }
        CrmwPoolAdjustFlowService flowService = new CrmwPoolAdjustFlowService();
        ReflectionTestUtils.setField(flowService, "crmwPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(flowService, "crmwPoolAdjustService", service);
        ReflectionTestUtils.setField(flowService, "reportService", mock(ReportService.class));
        IpAdjustStepBo step = new IpAdjustStepBo();
        step.setAdjustLogId(1L);
        step.setAdjustBatchNo("CRMW_BATCH");
        when(mapper.queryAdjustLogListForAudit(1L, "CRMW_BATCH")).thenReturn(logs);
        when(mapper.editActiveAdjustLogAuditStatus(1L, "CRMW_BATCH", "20")).thenReturn(3);
        ReflectionTestUtils.invokeMethod(flowService, "finishAdjustBatch", step);
        verify(mapper, times(3)).addPoolStatus(any());
        assertThat(logs).extracting(IpAdjustLogBo::getAuditStatus).containsOnly("20");
    }

    /** 构建单组合校验请求。 */
    private AdjustCheckReq checkReq(String direction) {
        AdjustCheckReq req = new AdjustCheckReq();
        req.setSecurityCode("A.IB");
        req.setCrmwScode("C.IB");
        req.setCrmwStype("crmw");
        AdjustCheckReq.CheckItem item = new AdjustCheckReq.CheckItem();
        item.setTargetPoolId(18L);
        item.setPoolType("crmw");
        item.setAdjustMode(direction);
        req.setItems(Collections.singletonList(item));
        return req;
    }

    /** 构建主项及两条关联项提交请求。 */
    private CrmwPoolAdjustSubmitReq submitReq(String direction) {
        CrmwPoolAdjustSubmitReq req = new CrmwPoolAdjustSubmitReq();
        req.setSecurityCode("A.IB");
        req.setCrmwScode("C.IB");
        req.setCrmwName("凭证C");
        req.setCrmwStype("crmw");
        req.setAdjustType("手工调整");
        req.setAdjusterId("1");
        req.setAdjusterName("测试用户");
        List<CrmwPoolAdjustSubmitReq.AdjustItem> items = new ArrayList<>();
        for (String code : Arrays.asList("A.IB", "A.SH", "A.SZ")) {
            CrmwPoolAdjustSubmitReq.AdjustItem item = new CrmwPoolAdjustSubmitReq.AdjustItem();
            item.setSecurityCode(code);
            item.setTargetPoolId(18L);
            item.setPoolType("crmw");
            item.setAdjustMode(direction);
            item.setItemTag("A.IB".equals(code) ? "manual" : "related");
            item.setAdjustGroupKey("GROUP");
            items.add(item);
        }
        req.setItems(items);
        return req;
    }
}
