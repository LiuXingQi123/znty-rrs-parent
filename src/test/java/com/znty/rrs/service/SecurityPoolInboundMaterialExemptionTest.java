package com.znty.rrs.service;

import com.znty.rrs.common.constants.ProductInboundPoolCodes;
import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.AdjustSharedData;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 信用债和产品库快速入库的材料豁免与原入口隔离测试 */
public class SecurityPoolInboundMaterialExemptionTest {

    /** 六项来源均可使用，来源判断不查询数据库 */
    @Test
    public void productShouldAcceptEachExistingSourceWithoutQueries() {
        Fixture f = new Fixture();
        for (String code : ProductInboundPoolCodes.SOURCES) {
            f.source.setPoolCode(code);
            assertThat(f.productMatches()).isTrue();
        }
        verifyNoInteractions(f.mapper, f.flowMapper);
    }

    /** 当前不在库时，本次申请进入来源库不能代替已生效状态 */
    @Test
    public void requestedSourceShouldNotCountAsCurrentMembership() {
        Fixture f = new Fixture();
        f.check.setCurrentPoolIds(Collections.<Long>emptySet());
        f.check.setRequestInPoolIds(Collections.singleton(3L));
        assertThat(f.productMatches()).isFalse();
        assertThat(f.options().get(0).isSelectable()).isFalse();
        assertThat(f.options().get(1).isRecommended()).isTrue();
        verifyNoInteractions(f.mapper);
    }

    /** 同类型的另一棵树、根节点、非叶子、停用和删除目标均不在范围内 */
    @Test
    public void productShouldUseExactEnabledLeafTree() {
        Fixture f = new Fixture();
        f.root.setPoolCode("bond_product_root_1");
        assertThat(f.productMatches()).isFalse();
        f.root.setPoolCode(ProductInboundPoolCodes.ROOT);
        f.target.setStatus("disabled");
        assertThat(f.productMatches()).isFalse();
        f.target.setStatus("enabled");
        f.target.setIsDeleted(1);
        assertThat(f.productMatches()).isFalse();
        f.target.setIsDeleted(0);
        f.pools.put(4L, f.pool(4L, 2L, "child"));
        assertThat(f.productMatches()).isFalse();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(f.service, "isProductInboundTarget", f.root, f.pools))
                .isFalse();
    }

    /** 来源资格满足时快速优先，但保留特殊流程供改选 */
    @Test
    public void productFastShouldBeRecommendedBeforeSpecialFlow() {
        Fixture f = new Fixture();
        Map<String, List<Long>> relations = new HashMap<>();
        relations.put("in_mutex", Collections.singletonList(3L));
        f.check.getPoolRelationMap().put(2L, relations);
        FlowDefinitionBo special = new FlowDefinitionBo();
        special.setId(109L);
        special.setName("特殊流程");
        when(f.flowMapper.queryActiveFlowByKey("bond:special-inbound")).thenReturn(special);
        List<AdjustCheckDto.FlowOption> options = f.options();
        assertThat(options).hasSize(2);
        assertThat(options.get(0).getMaterialExemptionType()).isEqualTo("productSource");
        assertThat(options.get(0).isRecommended()).isTrue();
        assertThat(options.get(1).getFlowType()).isEqualTo("specialInbound");
        assertThat(options.get(1).isSelectable()).isTrue();
        assertThat(options.get(1).isRecommended()).isFalse();
    }

    /** 关联代码未在来源库时禁用整组快速候选，保留普通流程 */
    @Test
    public void relatedCodeShouldNotBorrowMainSourceMembership() {
        Fixture f = new Fixture();
        AdjustSharedData related = new AdjustSharedData();
        related.setPoolMap(f.pools);
        related.setCurrentPoolIds(Collections.<Long>emptySet());
        f.check.getRelatedSecurityData().put("RELATED.SH", related);
        List<AdjustCheckDto.FlowOption> options = f.options();
        ReflectionTestUtils.invokeMethod(f.service, "restrictRelatedFastFlowOptions",
                f.check, f.checkItem(), options);
        assertThat(options.get(0).isSelectable()).isFalse();
        assertThat(options.get(0).getMaterialExemptionType()).isNull();
        assertThat(options.get(0).getUnmatchReasons()).anyMatch(reason -> reason.contains("RELATED.SH"));
        assertThat(options.get(1).isRecommended()).isTrue();
        verifyNoInteractions(f.mapper);
    }

    /** 单笔匹配不会因配置了批量流程而额外展示批量候选 */
    @Test
    public void singleCheckShouldKeepMatchedFlowsWithoutBatchCandidate() {
        Fixture f = new Fixture();
        f.target.setBatchInFlowId(107L);
        f.target.setBatchInFlowKey("bond:batch-inbound");
        List<AdjustCheckDto.FlowOption> options = f.options();
        assertThat(options).hasSize(2);
        assertThat(options.get(0).getFlowType()).isEqualTo("simpleInbound");
        assertThat(options.get(0).getMaterialExemptionType()).isEqualTo("productSource");
    }

    /** 批量入口保留快速及一般候选，再额外推荐批量入库，不重新查询投资池 */
    @Test
    public void batchCheckShouldAppendConfiguredFlowAndPreserveFastExemption() {
        Fixture f = new Fixture();
        f.check.setBatchFlowOptionsEnabled(true);
        f.target.setBatchInFlowId(107L);
        f.target.setBatchInFlowKey("bond:batch-inbound");
        AdjustCheckDto.CheckResultItem item = f.checkItem();
        ReflectionTestUtils.invokeMethod(f.service, "resolveAdjustFlowOptions", new AdjustCheckReq(),
                f.check, Collections.singletonList(item));
        assertThat(item.getFlowOptions()).extracting(AdjustCheckDto.FlowOption::getFlowType)
                .containsExactly("batchInbound", "simpleInbound", "normalInbound");
        assertThat(item.getFlowOptions().get(0).isRecommended()).isTrue();
        assertThat(item.getFlowOptions().get(0).getMaterialExemptionType()).isNull();
        assertThat(item.getFlowOptions().get(1).getMaterialExemptionType()).isEqualTo("productSource");
        assertThat(item.getMaterialExemptionType()).isEqualTo("productSource");
        verifyNoInteractions(f.mapper);
    }

    /** 无快速资格时，即使选中快速或批量流程也继续要求报告 */
    @Test
    public void unqualifiedItemShouldRequireReportRegardlessOfSelectedFlow() throws Exception {
        Fixture f = new Fixture();
        f.manual.setMaterialExemptionType(null);
        for (String type : Arrays.asList("simpleInbound", "normalInbound", "batchInbound")) {
            f.manual.setFlowType(type);
            Object shared = f.submitData();
            assertThat(f.exempt(f.manual, shared)).isFalse();
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(f.service, "executeInboundSubmit", f.request,
                    shared, null, new ArrayList<IpAdjustLogBo>())).isInstanceOf(BizException.class)
                    .hasMessageContaining("要求研究报告");
        }
        verify(f.mapper, never()).addAdjustLog(any(IpAdjustLogBo.class));
    }

    /** 无资格券即使批量流程是推荐项，行级材料豁免仍为空 */
    @Test
    public void batchRecommendationShouldNotCreateQualification() {
        Fixture f = new Fixture();
        f.check.setCurrentPoolIds(Collections.emptySet());
        f.check.setBatchFlowOptionsEnabled(true);
        f.target.setBatchInFlowId(107L);
        AdjustCheckDto.CheckResultItem row = f.checkItem();
        ReflectionTestUtils.invokeMethod(f.service, "resolveAdjustFlowOptions", new AdjustCheckReq(),
                f.check, Collections.singletonList(row));
        assertThat(row.getMaterialExemptionType()).isNull();
        assertThat(row.getFlowOptions().get(0).getFlowType()).isEqualTo("batchInbound");
        assertThat(row.getFlowOptions().get(0).isRecommended()).isTrue();
    }

    /** 信用债取得简易资格后，改选任意审批流程均免材料，不重新查询资格 */
    @Test
    public void creditQualificationShouldApplyToEverySelectedInboundFlow() throws Exception {
        Fixture f = new Fixture();
        f.target.setPoolType("credit_bond");
        f.manual.setMaterialExemptionType("creditBondSimple");
        for (String type : Arrays.asList("simpleInbound", "normalInbound", "specialInbound", "batchInbound")) {
            f.manual.setFlowType(type);
            assertThat(f.exempt(f.manual, f.submitData())).isTrue();
        }
        verifyNoInteractions(f.mapper, f.flowMapper);
    }

    /** 快速主项和配套互斥调出均无材料时可以生成已通过记录 */
    @Test
    public void productFastShouldExemptGroupAndKeepIndependentOutboundUnexempt() throws Exception {
        Fixture f = new Fixture();
        SecurityPoolAdjustSubmitReq.AdjustItem mutex = f.item(3L, "调出", "mutex", "fast-group");
        f.request.getItems().add(mutex);
        Object shared = f.submitData();
        List<IpAdjustLogBo> directLogs = new ArrayList<>();
        ReflectionTestUtils.invokeMethod(f.service, "executeInboundSubmit", f.request, shared, null, directLogs);
        ReflectionTestUtils.invokeMethod(f.service, "executeOutboundSubmit", f.request, shared, null, directLogs);
        assertThat(directLogs).hasSize(2).allMatch(log -> "20".equals(log.getAuditStatus()));
        SecurityPoolAdjustSubmitReq.AdjustItem independent = f.item(3L, "调出", "manual", "out-group");
        assertThat(f.exempt(independent, shared)).isFalse();
        verify(f.mapper, never()).queryHasRecentInboundWithReport(anyString());
        verify(f.mapper, never()).querySecurityCurrentPoolIdList(anyString());
    }

    /** 信用债快速提交不重查矩阵、历史入库或历史报告，并免内部报告要求 */
    @Test
    public void creditFastShouldExemptInternalReportWithoutRepeatingQualification() throws Exception {
        Fixture f = new Fixture();
        f.target.setPoolType("credit_bond");
        f.manual.setMaterialExemptionType("creditBondSimple");
        f.target.setInReportRestriction("internal");
        Object shared = f.submitData();
        List<IpAdjustLogBo> directLogs = new ArrayList<>();
        ReflectionTestUtils.invokeMethod(f.service, "executeInboundSubmit", f.request, shared, null, directLogs);
        assertThat(directLogs).hasSize(1);
        verify(f.mapper, never()).queryIssuerHasNonSimpleCreditBondInboundWithinDays(anyString(), anyInt());
        verify(f.mapper, never()).queryIssuerHasRecentCreditReportWithinDays(anyString(), anyInt());
        verify(f.mapper, never()).queryHasRecentInboundWithReport(anyString());
        verifyNoInteractions(f.flowMapper);
    }

    /** 有资格与无资格主项混合时，无资格项仍要求报告 */
    @Test
    public void unqualifiedGroupShouldKeepReportRequirementInMixedRequest() throws Exception {
        Fixture f = new Fixture();
        SecurityPoolAdjustSubmitReq.AdjustItem normal = f.item(3L, "调入", "manual", "normal-group");
        normal.setFlowType("normalInbound");
        f.source.setInReportRestriction("any");
        f.request.getItems().add(normal);
        Object shared = f.submitData();
        assertThat(f.exempt(f.manual, shared)).isTrue();
        assertThat(f.exempt(normal, shared)).isFalse();
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(f.service, "executeInboundSubmit", f.request,
                shared, null, new ArrayList<IpAdjustLogBo>())).isInstanceOf(BizException.class)
                .hasMessageContaining("要求研究报告");
    }

    /** 有快速资格时选择批量流程仍免材料，流程类型和日志保持用户所选 */
    @Test
    public void batchChoiceShouldKeepQualificationMaterialExemption() throws Exception {
        Fixture f = new Fixture();
        f.manual.setFlowType("batchInbound");
        Object shared = f.submitData();
        assertThat(f.exempt(f.manual, shared)).isTrue();
        List<IpAdjustLogBo> logs = new ArrayList<>();
        ReflectionTestUtils.invokeMethod(f.service, "executeInboundSubmit", f.request, shared, null, logs);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getFlowType()).isEqualTo("batchInbound");
        assertThat(logs.get(0).getFlowId()).isEqualTo(105L);
    }

    /** 材料资格与所选流程标识独立，改选一般或特殊流程仍保留豁免 */
    @Test
    public void materialExemptionShouldBeIndependentOfSelectedFlow() throws Exception {
        Fixture f = new Fixture();
        String[] types = {"simpleInbound", "normalInbound", "specialInbound", "batchInbound"};
        for (int i = 0; i < types.length; i++) {
            f.manual.setFlowType(types[i]);
            f.manual.setFlowId(103L + i);
            f.manual.setFlowKey("bond:" + types[i]);
            assertThat(f.exempt(f.manual, f.submitData())).isTrue();
            assertThat(f.manual.getFlowId()).isEqualTo(103L + i);
            assertThat(f.manual.getFlowType()).isEqualTo(types[i]);
        }
        verifyNoInteractions(f.mapper, f.flowMapper);
    }

    /** 材料资格不要求投资池配置快速审批流程 */
    @Test
    public void materialExemptionShouldNotDependOnQuickFlowConfiguration() throws Exception {
        Fixture f = new Fixture();
        f.target.setSimpleInFlowId(null);
        f.target.setSimpleInFlowKey(null);
        f.manual.setFlowType("normalInbound");
        f.manual.setFlowId(103L);
        Object shared = f.submitData();
        ReflectionTestUtils.setField(shared, "flowSnapshotMap", Collections.emptyMap());
        assertThat(f.exempt(f.manual, shared)).isTrue();
        verifyNoInteractions(f.mapper, f.flowMapper);
    }

    /** 仅传流程 Key 时复用已有快照，提交不重复解析查询 */
    @Test
    public void keyOnlyFastFlowShouldReuseLoadedSnapshot() throws Exception {
        Fixture f = new Fixture();
        f.manual.setFlowId(null);
        Object shared = f.submitData();
        List<IpAdjustLogBo> directLogs = new ArrayList<>();
        ReflectionTestUtils.invokeMethod(f.service, "executeInboundSubmit", f.request, shared, null, directLogs);
        assertThat(directLogs).hasSize(1);
        verifyNoInteractions(f.flowMapper);
    }

    /** 普通流程即使没有快照，也保留已解析的 ID，不能误当成无流程直通 */
    @Test
    public void resolvedNormalFlowShouldKeepIdentityWhenSnapshotMissing() throws Exception {
        Fixture f = new Fixture();
        f.manual.setFlowId(null);
        f.manual.setFlowKey("bond:normal-inbound");
        Object shared = f.submitData();
        ReflectionTestUtils.setField(shared, "flowSnapshotMap", Collections.emptyMap());
        ReflectionTestUtils.setField(shared, "resolvedFlowIdsByKey",
                Collections.singletonMap("bond:normal-inbound", 103L));
        Long resolvedId = ReflectionTestUtils.invokeMethod(f.service, "resolveLoadedFlowId", f.manual, shared);
        assertThat(resolvedId).isEqualTo(103L);
        verifyNoInteractions(f.flowMapper);
    }

    /** 自愿提供无效报告时仍明确拒绝，豁免不等于忽略错误材料 */
    @Test
    public void invalidVoluntaryReportShouldStopSubmission() throws Exception {
        Fixture f = new Fixture();
        f.manual.setCreditReportSourceAttachmentIds(Collections.singletonList(99L));
        doThrow(new BizException("报告校验失败：无效报告引用"))
                .when(f.attachments).validateCreditReportSources(Collections.singletonList(99L), false);
        Object shared = f.submitData();
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(f.service, "executeInboundSubmit", f.request,
                shared, null, new ArrayList<IpAdjustLogBo>())).isInstanceOf(BizException.class)
                .hasMessageContaining("无效报告引用");
        verify(f.mapper, never()).addAdjustLog(any(IpAdjustLogBo.class));
    }

    /** 选择含人工审批的流程仍可免材料，但必须生成待审批记录而不能直接生效 */
    @Test
    public void qualifiedHumanApprovalFlowShouldExemptMaterialsAndRemainPending() throws Exception {
        Fixture f = new Fixture();
        f.manual.setFlowType("normalInbound");
        Object shared = f.submitData();
        Object snapshot = ((Map<?, ?>) ReflectionTestUtils.getField(shared, "flowSnapshotMap")).get(105L);
        Map<Long, FlowNodeBo> nodes = new HashMap<>();
        FlowNodeBo start = f.node(1L, "start");
        FlowNodeBo approval = f.node(2L, "approval");
        approval.setLabel("人工复核");
        FlowNodeBo end = f.node(3L, "end");
        nodes.put(1L, start);
        nodes.put(2L, approval);
        nodes.put(3L, end);
        ReflectionTestUtils.setField(snapshot, "nodeMap", nodes);
        ReflectionTestUtils.setField(snapshot, "edges", Arrays.asList(f.edge(1L, 2L), f.edge(2L, 3L)));
        assertThat(f.exempt(f.manual, shared)).isTrue();
        List<IpAdjustLogBo> directLogs = new ArrayList<>();
        ReflectionTestUtils.invokeMethod(f.service, "executeInboundSubmit", f.request, shared, null, directLogs);
        assertThat(directLogs).isEmpty();
        ArgumentCaptor<IpAdjustLogBo> captor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(f.mapper).addAdjustLog(captor.capture());
        assertThat(captor.getValue().getAuditStatus()).isEqualTo("00");
        assertThat(captor.getValue().getFlowType()).isEqualTo("normalInbound");
    }

    /** 来源变化使用本次已经加载的集合判断，不再发起来源查询 */
    @Test
    public void lostSourceShouldFailWithoutAdditionalDatabaseQuery() throws Exception {
        Fixture f = new Fixture();
        Object shared = f.submitData();
        ReflectionTestUtils.setField(shared, "currentPoolIds", Collections.emptySet());
        assertThatThrownBy(() -> f.exempt(f.manual, shared)).isInstanceOf(BizException.class)
                .hasMessageContaining("来源资格已失效");
        verifyNoInteractions(f.mapper, f.flowMapper);
    }

    /** 自愿补充报告仍检查有效引用，普通申请按资格豁免 */
    @Test
    public void voluntaryReportShouldBeValidatedAndSharedSubmitShouldExemptFastFlow() throws Exception {
        Fixture f = new Fixture();
        f.manual.setCreditReportSourceAttachmentIds(Collections.singletonList(99L));
        ReflectionTestUtils.invokeMethod(f.service, "executeInboundSubmit", f.request, f.submitData(), null,
                new ArrayList<IpAdjustLogBo>());
        verify(f.attachments).validateCreditReportSources(Collections.singletonList(99L), false);
        f.manual.setCreditReportSourceAttachmentIds(null);
        Object excelShared = f.submitData();
        List<IpAdjustLogBo> directLogs = new ArrayList<>();
        ReflectionTestUtils.invokeMethod(f.service, "executeInboundSubmit", f.request,
                excelShared, null, directLogs);
        assertThat(directLogs).hasSize(1);
    }

    /** Excel 业务校验只作提示，未取得快速资格也不能被报告必填再次阻断 */
    @Test
    public void excelImportShouldSubmitWithoutRequiredReportOrFastQualification() throws Exception {
        Fixture f = new Fixture();
        f.request.setAdjustType(SecurityPoolAdjustService.EXCEL_IMPORT_ADJUST_TYPE);
        f.manual.setMaterialExemptionType(null);
        List<IpAdjustLogBo> directLogs = new ArrayList<>();
        ReflectionTestUtils.invokeMethod(f.service, "executeInboundSubmit", f.request, f.submitData(), null, directLogs);
        assertThat(directLogs).hasSize(1);
        assertThat(directLogs.get(0).getAdjustType()).isEqualTo("Excel导入");
    }

    /** 构造完整的产品库、当前来源和直接生效流程，供各业务场景复用 */
    private static class Fixture {
        final SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        final SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        final FlowMapper flowMapper = mock(FlowMapper.class);
        final SysAttachmentService attachments = mock(SysAttachmentService.class);
        final Map<Long, InvestmentPoolBo> pools = new HashMap<>();
        final InvestmentPoolBo root = pool(1L, null, ProductInboundPoolCodes.ROOT);
        final InvestmentPoolBo target = pool(2L, 1L, "product_leaf");
        final InvestmentPoolBo source = pool(3L, null, "credit_bond_level_2");
        final AdjustSharedData check = new AdjustSharedData();
        final SecurityPoolAdjustSubmitReq request = new SecurityPoolAdjustSubmitReq();
        final SecurityPoolAdjustSubmitReq.AdjustItem manual = item(2L, "调入", "manual", "fast-group");

        Fixture() {
            ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
            ReflectionTestUtils.setField(service, "flowMapper", flowMapper);
            ReflectionTestUtils.setField(service, "sysAttachmentService", attachments);
            pools.put(1L, root);
            pools.put(2L, target);
            pools.put(3L, source);
            target.setSimpleInFlowId(105L);
            target.setSimpleInFlowKey("bond:fast-inbound");
            target.setInFlowId(103L);
            target.setInFlowKey("bond:normal-inbound");
            target.setInReportRestriction("internal");
            source.setOutReportRestriction("any");
            check.setPoolMap(pools);
            check.setCurrentPoolIds(new HashSet<>(Collections.singletonList(3L)));
            check.setPoolRelationMap(new HashMap<Long, Map<String, List<Long>>>());
            request.setSecurityCode("BOND.IB");
            request.setAdjusterId("1");
            request.setAdjusterName("测试研究员");
            request.setAdjustType("手动批量调整");
            request.setItems(new ArrayList<>(Collections.singletonList(manual)));
            manual.setFlowType("simpleInbound");
            manual.setMaterialExemptionType("productSource");
            manual.setFlowId(105L);
            manual.setFlowKey("bond:fast-inbound");
            doAnswer(invocation -> {
                ((IpAdjustLogBo) invocation.getArgument(0)).setId(10L);
                return null;
            }).when(mapper).addAdjustLog(any(IpAdjustLogBo.class));
        }

        InvestmentPoolBo pool(Long id, Long parent, String code) {
            InvestmentPoolBo pool = new InvestmentPoolBo();
            pool.setId(id);
            pool.setParentId(parent);
            pool.setPoolCode(code);
            pool.setPoolName(code);
            pool.setStatus("enabled");
            pool.setPoolType("bond_product");
            return pool;
        }

        SecurityPoolAdjustSubmitReq.AdjustItem item(Long poolId, String mode, String tag, String group) {
            SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
            item.setTargetPoolId(poolId);
            item.setAdjustMode(mode);
            item.setItemTag(tag);
            item.setAdjustGroupKey(group);
            return item;
        }

        AdjustCheckDto.CheckResultItem checkItem() {
            AdjustCheckDto.CheckResultItem item = new AdjustCheckDto.CheckResultItem();
            item.setTargetPoolId(2L);
            item.setAdjustMode("调入");
            item.setItemTag("manual");
            item.setCanAdjust(true);
            return item;
        }

        boolean productMatches() {
            return ReflectionTestUtils.invokeMethod(service, "isProductInboundFlowMatched", target, pools,
                    check.getCurrentPoolIds());
        }

        List<AdjustCheckDto.FlowOption> options() {
            return ReflectionTestUtils.invokeMethod(service, "resolveAdjustFlowOptionsForItem", new AdjustCheckReq(),
                    check, checkItem());
        }

        boolean exempt(SecurityPoolAdjustSubmitReq.AdjustItem item, Object shared) {
            return ReflectionTestUtils.invokeMethod(service, "isInboundMaterialExempt", request, item, shared);
        }

        FlowNodeBo node(Long id, String type) {
            FlowNodeBo node = new FlowNodeBo();
            node.setId(id);
            node.setNodeType(type);
            node.setLabel(type);
            return node;
        }

        FlowEdgeBo edge(Long from, Long to) {
            FlowEdgeBo edge = new FlowEdgeBo();
            edge.setFromNodeId(from);
            edge.setToNodeId(to);
            return edge;
        }

        Object submitData() throws Exception {
            Map<Long, FlowNodeBo> nodes = new HashMap<>();
            nodes.put(1L, node(1L, "start"));
            nodes.put(3L, node(3L, "end"));
            FlowDefinitionBo definition = new FlowDefinitionBo();
            definition.setId(105L);
            definition.setFlowKey("bond:fast-inbound");
            definition.setStatus("active");
            Constructor<?> snapshotConstructor = Class.forName(
                    "com.znty.rrs.service.SecurityPoolAdjustService$FlowSnapshot").getDeclaredConstructors()[0];
            snapshotConstructor.setAccessible(true);
            Object snapshot = snapshotConstructor.newInstance(definition, null, nodes,
                    Collections.singletonList(edge(1L, 3L)), Collections.emptyMap(), Collections.emptyMap());
            Map<Long, Object> snapshots = Collections.singletonMap(105L, snapshot);
            Constructor<?> sharedConstructor = Class.forName(
                    "com.znty.rrs.service.SecurityPoolAdjustService$SubmitSharedData").getDeclaredConstructors()[0];
            sharedConstructor.setAccessible(true);
            SecurityInfoBo security = new SecurityInfoBo();
            security.setWindCode("BOND.IB");
            security.setSecurityType("bond");
            return sharedConstructor.newInstance(security, pools, check.getCurrentPoolIds(),
                    Collections.emptyMap(), false, false, false, snapshots,
                    new SecurityPoolAdjustService.BatchNoContext(), null);
        }
    }
}
