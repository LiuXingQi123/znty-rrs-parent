package com.znty.rrs.service;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.common.SecurityMarketDto;
import com.znty.rrs.entity.common.SecurityMarketMappingDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.AdjustSharedData;
import com.znty.rrs.entity.securitypooladjust.BatchSecurityCheckData;
import com.znty.rrs.entity.securitypooladjust.RelatedRatingCompanyDto;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import com.znty.rrs.mapper.AbsIdentityMapper;
import com.znty.rrs.mapper.BatchSecurityCheckMapper;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import org.junit.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Excel 原规则提示后放行与其他入口隔离测试 */
public class SecurityPoolExcelWarningOnlyTest {

    /** Excel 普通债直通继续放行已有提示，只复核本批证券、池和来源选择 */
    @Test
    public void excelDirectSubmitShouldUseBatchMastersAndKeepWarningOnlySemantics() {
        Fixture f = new Fixture();
        BatchSecurityCheckMapper batch = mock(BatchSecurityCheckMapper.class);
        SysAttachmentService attachments = mock(SysAttachmentService.class);
        ReflectionTestUtils.setField(f.service, "batchSecurityCheckMapper", batch);
        ReflectionTestUtils.setField(f.service, "sysAttachmentService", attachments);
        f.pool.setStatus("disabled");
        List<String> codes = Collections.singletonList("BOND.IB");
        when(batch.querySecurities(codes)).thenReturn(Collections.singletonList(f.security));
        when(f.mapper.addPoolStatus(any())).thenReturn(1);
        doAnswer(call -> {
            IpAdjustLogBo log = call.getArgument(0);
            log.setId(10L);
            return 1;
        }).when(f.mapper).addAdjustLog(any());

        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        item.setSecurityCode("BOND.IB");
        item.setSecurityType("bond");
        item.setSecurityShortName("测试债");
        item.setTargetPoolId(100L);
        item.setAdjustMode("调入");
        item.setAdjustGroupKey("EXCEL_1");
        item.setItemTag("manual");
        SecurityPoolAdjustSubmitReq req = new SecurityPoolAdjustSubmitReq();
        req.setSecurityCode("BOND.IB");
        req.setAdjusterId("1");
        req.setAdjustType("Excel导入");
        req.setItems(Collections.singletonList(item));

        assertThat(f.service.addExcelImportAdjustLogList(Collections.singletonList(req))).hasSize(1);
        verify(f.pools, never()).queryExistingPoolIdList(any());
        verify(batch).querySecurities(codes);
        verify(batch).queryActiveStates(codes);
        verify(f.mapper, never()).querySecurityBoByCode(anyString());
        verify(f.mapper, never()).queryPoolCurrentCount(anyLong());
        InOrder writes = inOrder(f.mapper);
        verify(f.mapper, never()).deletePoolStatusSoft("BOND.IB", 100L);
        writes.verify(f.mapper).addPoolStatus(any());
    }

    @Test
    public void excelShouldKeepActualForbiddenFailureAndBuildFlowWhileSingleStillRejects() {
        Fixture f = new Fixture();
        when(f.mapper.querySecurityInForbiddenPool("BOND.IB")).thenReturn(true);
        AdjustCheckReq req = new AdjustCheckReq();
        req.setSecurityCode("BOND.IB");
        AdjustCheckReq.CheckItem item = new AdjustCheckReq.CheckItem();
        item.setTargetPoolId(100L);
        item.setAdjustMode("调入");
        req.setItems(Collections.singletonList(item));

        AdjustCheckDto normal = f.service.checkAdjust(req);
        AdjustCheckDto excel = f.service.checkExcelAdjust(req);

        assertThat(normal.getItems().get(0).isCanAdjust()).isFalse();
        assertThat(normal.getItems().get(0).getFlowOptions()).isEmpty();
        assertThat(excel.getItems().get(0).isCanAdjust()).isTrue();
        assertThat(excel.getItems().get(0).getFailReasons()).contains("证券当前在禁止池中");
        assertThat(excel.getItems().get(0).getFailReasons()).isEqualTo(normal.getItems().get(0).getFailReasons());
        assertThat(excel.getItems().get(0).getFlowOptions()).isNotEmpty();
    }

    /** 主体 Excel 决定绕过普通门槛，仍生成联动和互斥项并阻断在途冲突 */
    @Test
    public void excelCompanyFollowShouldKeepLinkageMutexAndPendingChecks() {
        SecurityPoolCheckRules rules = AbsIdentityTestSupport.withIdentity(new SecurityPoolCheckRules());
        ReflectionTestUtils.setField(rules, "ratingDowngradeChecker", mock(RatingDowngradeChecker.class));
        BatchSecurityCheckData data = new BatchSecurityCheckData();
        data.scheduled = true;
        SecurityInfoBo bond = new SecurityInfoBo();
        bond.setWindCode("BOND.IB");
        bond.setSecurityType("mtn");
        bond.setIssuerCode("C10003");
        data.securities.put(bond.getWindCode(), bond);
        SecurityMarketMappingDto market = new SecurityMarketMappingDto();
        market.setRawMarketCode("NIB");
        data.markets.put(bond.getWindCode(), Collections.singletonList(market));
        for (long poolId : new long[]{16L, 17L, 23L}) {
            InvestmentPoolBo pool = new InvestmentPoolBo();
            pool.setId(poolId);
            pool.setPoolName("池" + poolId);
            data.pools.put(poolId, pool);
        }
        data.activePools.put(bond.getWindCode(), Collections.singleton(16L));
        data.relations.put(23L, new HashMap<>());
        data.relations.get(23L).put("in_linked", Collections.singletonList(17L));
        data.relations.get(23L).put("in_mutex", Collections.singletonList(16L));
        AdjustCheckReq req = new AdjustCheckReq();
        req.setSecurityCode(bond.getWindCode());
        AdjustCheckReq.CheckItem item = new AdjustCheckReq.CheckItem();
        item.setTargetPoolId(23L);
        item.setAdjustMode("调入");
        req.setItems(Collections.singletonList(item));

        List<AdjustCheckDto.CheckResultItem> items = rules.checkExcelCompanyFollowItem(req, data).getItems();
        assertThat(items).extracting(AdjustCheckDto.CheckResultItem::getTargetPoolId).containsExactly(23L, 17L, 16L);
        assertThat(items).extracting(AdjustCheckDto.CheckResultItem::getAdjustMode).containsExactly("调入", "调入", "调出");
        assertThat(items).allMatch(AdjustCheckDto.CheckResultItem::isCanAdjust);

        data.pendingPools.put(bond.getWindCode(), Collections.singleton(16L));
        assertThat(rules.checkExcelCompanyFollowItem(req, data).getItems())
                .filteredOn(row -> row.getTargetPoolId() == 23L).noneMatch(AdjustCheckDto.CheckResultItem::isCanAdjust);
    }

    /** CRMW 只限制候选列表，通用规则不额外按凭证类型拦截 */
    @Test
    public void crmwShouldNotAddExtraManualTypeFailures() {
        for (String direction : Arrays.asList("调入", "调出")) {
            Fixture f = new Fixture();
            f.security.setSecurityType("crmw");
            when(f.mapper.querySecurityCurrentPoolIdList("BOND.IB"))
                    .thenReturn("调出".equals(direction) ? Collections.singletonList(100L) : Collections.emptyList());
            AdjustCheckReq req = new AdjustCheckReq();
            req.setSecurityCode("BOND.IB");
            req.setSecurityInfo(f.security);
            AdjustCheckReq.CheckItem selected = new AdjustCheckReq.CheckItem();
            selected.setTargetPoolId(100L);
            selected.setAdjustMode(direction);
            req.setItems(Collections.singletonList(selected));
            for (AdjustCheckDto result : Arrays.asList(f.service.checkAdjust(req), f.service.checkBatchAdjust(req))) {
                assertThat(result.getItems()).isNotEmpty().allSatisfy(item ->
                        assertThat(item.getFailReasons()).noneMatch(reason -> reason.contains("CRMW凭证不允许")));
            }
            f.service.recheckBeforeFinalApproval(Collections.singletonList(log("手工调整", "N1", direction)));
        }
    }

    /** ABS 身份冲突在单券、批量及 Excel 下一步均显示为不可调整 */
    @Test
    public void absIdentityConflictShouldRemainHardFailureAfterExcelWarningConversion() {
        Fixture f = new Fixture();
        f.security.setSecurityType("abs");
        f.security.setAbsFlag(1);
        AbsIdentityService identity = new AbsIdentityService();
        AbsIdentityMapper identities = mock(AbsIdentityMapper.class);
        ReflectionTestUtils.setField(identity, "absIdentityMapper", identities);
        ReflectionTestUtils.setField(identity, "securityPoolAdjustMapper", f.mapper);
        ReflectionTestUtils.setField(f.service, "absIdentityService", identity);
        when(identities.queryOccupiedIdentityCodes("BOND.IB")).thenReturn(Collections.singletonList("A"));
        RelatedRatingCompanyDto candidate = new RelatedRatingCompanyDto();
        candidate.setCompanyCode("B");
        candidate.setCompanyName("主体B");
        identity.rememberRelated("BOND.IB", Collections.singletonList(candidate));
        AdjustCheckReq req = new AdjustCheckReq();
        req.setSecurityCode("BOND.IB");
        req.setRightsHolderCode("B");
        req.setReleaseRules(true);
        AdjustCheckReq.CheckItem selected = new AdjustCheckReq.CheckItem();
        selected.setTargetPoolId(100L);
        selected.setAdjustMode("调入");
        req.setItems(Collections.singletonList(selected));
        for (AdjustCheckDto result : Arrays.asList(f.service.checkAdjust(req), f.service.checkBatchAdjust(req),
                f.service.checkExcelAdjust(req))) {
            assertThat(result.getItems()).isNotEmpty().allSatisfy(item -> {
                assertThat(item.isCanAdjust()).isFalse();
                assertThat(item.getFailReasons()).anyMatch(reason -> reason.contains("主体[A]") && reason.contains("主体[B]"));
                assertThat(item.getFlowOptions()).isEmpty();
            });
        }
        assertThatThrownBy(() -> identity.validateIdentity("BOND.IB", "B")).isInstanceOf(BizException.class);
    }

    @Test
    public void excelShouldAllowEveryKnownGroupItemWithoutRemovingReasons() {
        Fixture f = new Fixture();
        AdjustSharedData shared = f.shared();
        AdjustCheckDto.CheckResultItem manual = result("BOND.IB", "G", "manual");
        AdjustCheckDto.CheckResultItem mutex = result("BOND.IB", "G", "mutex");
        List<String> reasons = new ArrayList<>(Arrays.asList("证券当前在禁止池中", "目标投资池容量已满"));
        manual.setFailReasons(reasons);
        ReflectionTestUtils.invokeMethod(f.service, "allowExcelImportResults", shared, Arrays.asList(manual, mutex));
        assertThat(manual.isCanAdjust()).isTrue();
        assertThat(mutex.isCanAdjust()).isTrue();
        assertThat(manual.getFailReasons()).isSameAs(reasons);
    }

    @Test
    public void actualRelatedMarketExpansionShouldAlsoKeepFailedItemsSubmittable() {
        Fixture f = new Fixture();
        SecurityMarketDto related = new SecurityMarketDto();
        related.setWindCode("RELATED.SH");
        when(f.markets.queryRelatedMarketCodes("BOND.IB")).thenReturn(Collections.singletonList(related));
        SecurityInfoBo security = new SecurityInfoBo();
        security.setWindCode("RELATED.SH");
        security.setSecurityType("bond");
        when(f.mapper.querySecurityBoByCode("RELATED.SH")).thenReturn(security);
        when(f.mapper.querySecurityInForbiddenPool("BOND.IB")).thenReturn(true);
        when(f.mapper.querySecurityInForbiddenPool("RELATED.SH")).thenReturn(true);
        AdjustCheckReq req = new AdjustCheckReq();
        req.setSecurityCode("BOND.IB");
        AdjustCheckReq.CheckItem item = new AdjustCheckReq.CheckItem();
        item.setTargetPoolId(100L);
        item.setAdjustMode("调入");
        req.setItems(Collections.singletonList(item));

        AdjustCheckDto result = f.service.checkExcelAdjust(req);

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems()).allMatch(AdjustCheckDto.CheckResultItem::isCanAdjust);
        assertThat(result.getItems()).allSatisfy(row -> assertThat(row.getFailReasons()).isNotEmpty());
    }

    @Test
    public void unknownRelatedSecurityShouldKeepWholeGroupUnsubmittable() {
        Fixture f = new Fixture();
        AdjustCheckDto.CheckResultItem manual = result("BOND.IB", "G", "manual");
        AdjustCheckDto.CheckResultItem related = result("MISSING.SH", "G", "related");
        AdjustCheckDto.CheckResultItem other = result("BOND.IB", "G2", "manual");
        ReflectionTestUtils.invokeMethod(f.service, "allowExcelImportResults", f.shared(), Arrays.asList(manual, related, other));
        assertThat(manual.isCanAdjust()).isFalse();
        assertThat(related.isCanAdjust()).isFalse();
        assertThat(other.isCanAdjust()).isTrue();
    }

    @Test
    public void persistedExcelBatchShouldPassFinalApprovalForManualAndCompanionItems() {
        for (String type : Arrays.asList("Excel导入", "清空目标池")) {
            Fixture f = new Fixture();
            f.pool.setLockFlag(1);
            when(f.mapper.querySecurityInForbiddenPool("BOND.IB")).thenReturn(true);
            List<IpAdjustLogBo> logs = Arrays.asList(log(type, "E1", "调入"), log("关联调整", "E1", "调入"));
            f.service.recheckBeforeFinalApproval(logs);
            verify(f.mapper, never()).querySecurityInForbiddenPool("BOND.IB");
        }
    }

    @Test
    public void normalManualShouldStillBeBlockedByForbiddenPool() {
        Fixture f = new Fixture();
        when(f.mapper.querySecurityInForbiddenPool("BOND.IB")).thenReturn(true);
        for (String type : Arrays.asList("手工调整", "手动批量调整")) {
            assertThatThrownBy(() -> f.service.recheckBeforeFinalApproval(Collections.singletonList(log(type, "N1", "调入"))))
                    .isInstanceOf(BizException.class).hasMessageContaining("禁止池");
        }
    }

    @Test
    public void excelBatchMustNotReleaseAnotherNormalBatch() {
        Fixture f = new Fixture();
        when(f.mapper.querySecurityInForbiddenPool("BOND.IB")).thenReturn(true);
        assertThatThrownBy(() -> f.service.recheckBeforeFinalApproval(Arrays.asList(
                log("Excel导入", "E1", "调入"), log("手工调整", "N1", "调入"))))
                .isInstanceOf(BizException.class).hasMessageContaining("禁止池");
    }

    @Test
    public void excelStillRequiresExistingSecurityAndTargetPool() {
        Fixture f = new Fixture();
        when(f.mapper.querySecurityBoByCode("BOND.IB")).thenReturn(null);
        assertThatThrownBy(() -> f.service.recheckBeforeFinalApproval(Collections.singletonList(log("Excel导入", "E1", "调入"))))
                .isInstanceOf(BizException.class).hasMessageContaining("证券不存在");
        when(f.pools.queryPoolList()).thenReturn(Collections.emptyList());
        assertThatThrownBy(() -> f.service.recheckBeforeFinalApproval(Collections.singletonList(log("Excel导入", "E1", "调入"))))
                .isInstanceOf(BizException.class).hasMessageContaining("投资池不存在");
    }

    @Test
    public void repeatedExcelInboundShouldFailFinalRecheckAndPreserveCurrentStatus() {
        Fixture f = new Fixture();
        when(f.mapper.querySecurityCurrentPoolIdList("BOND.IB")).thenReturn(Collections.singletonList(100L));
        assertThatThrownBy(() -> f.service.recheckBeforeFinalApproval(
                Collections.singletonList(log("Excel导入", "E1", "调入"))))
                .isInstanceOf(BizException.class).hasMessageContaining("已在目标投资池");
        verify(f.mapper, never()).deletePoolStatusSoft("BOND.IB", 100L);
        verify(f.mapper, never()).addPoolStatus(any());
    }

    @Test
    public void pendingAndAlreadyInPoolShouldBlockTheWholeExcelGroup() {
        Fixture f = new Fixture();
        for (String reason : Arrays.asList(SecurityPoolCheckRules.PENDING_PROCESS_FAILURE + "（当前节点：审批）",
                SecurityPoolCheckRules.ALREADY_IN_POOL_FAILURE)) {
            AdjustCheckDto.CheckResultItem manual = result("BOND.IB", "G", "manual");
            AdjustCheckDto.CheckResultItem linked = result("BOND.IB", "G", "linkage");
            linked.setFailReasons(Collections.singletonList(reason));
            ReflectionTestUtils.invokeMethod(f.service, "allowExcelImportResults", f.shared(), Arrays.asList(manual, linked));
            assertThat(manual.isCanAdjust()).isFalse();
            assertThat(linked.isCanAdjust()).isFalse();
        }
    }

    @Test
    public void alreadyAbsentExcelOutboundShouldBeIdempotentButNormalOutboundStillFails() {
        Fixture f = new Fixture();
        f.service.applyPoolStatusChanges(Collections.singletonList(log("Excel导入", "E1", "调出")));
        assertThatThrownBy(() -> f.service.applyPoolStatusChanges(Collections.singletonList(log("手工调整", "N1", "调出"))))
                .isInstanceOf(BizException.class).hasMessageContaining("调出失败");
    }

    private static AdjustCheckDto.CheckResultItem result(String code, String group, String tag) {
        AdjustCheckDto.CheckResultItem item = new AdjustCheckDto.CheckResultItem();
        item.setSecurityCode(code);
        item.setTargetPoolId(100L);
        item.setAdjustGroupKey(group);
        item.setItemTag(tag);
        return item;
    }

    private static IpAdjustLogBo log(String type, String batch, String direction) {
        IpAdjustLogBo log = new IpAdjustLogBo();
        log.setId(1L);
        log.setSecurityCode("BOND.IB");
        log.setAdjustMode(direction);
        log.setAdjustType(type);
        log.setAdjustBatchNo(batch);
        log.setTargetPoolId(100L);
        log.setTargetPoolName("测试池");
        return log;
    }

    private static class Fixture {
        final SecurityPoolAdjustService service = AbsIdentityTestSupport.withIdentity(new SecurityPoolAdjustService());
        final SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        final InvestmentPoolMapper pools = mock(InvestmentPoolMapper.class);
        final SecurityMarketService markets = mock(SecurityMarketService.class);
        final InvestmentPoolBo pool = new InvestmentPoolBo();
        final SecurityInfoBo security = new SecurityInfoBo();

        Fixture() {
            ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
            ReflectionTestUtils.setField(service, "investmentPoolMapper", pools);
            ReflectionTestUtils.setField(service, "flowMapper", mock(FlowMapper.class));
            ReflectionTestUtils.setField(service, "ratingDowngradeChecker", mock(RatingDowngradeChecker.class));
            ReflectionTestUtils.setField(service, "securityMarketService", markets);
            pool.setId(100L);
            pool.setStatus("enabled");
            pool.setPoolType("other");
            pool.setPoolName("测试池");
            security.setWindCode("BOND.IB");
            security.setSecurityType("bond");
            when(pools.queryPoolList()).thenReturn(Collections.singletonList(pool));
            when(mapper.querySecurityBoByCode("BOND.IB")).thenReturn(security);
        }

        AdjustSharedData shared() {
            AdjustSharedData shared = new AdjustSharedData();
            shared.setSecurityInfo(security);
            shared.setPoolMap(new HashMap<>());
            shared.getPoolMap().put(100L, pool);
            return shared;
        }
    }
}
