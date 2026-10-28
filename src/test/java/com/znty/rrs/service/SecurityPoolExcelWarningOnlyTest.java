package com.znty.rrs.service;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.common.SecurityMarketDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.AdjustSharedData;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Excel 原规则提示后放行与其他入口隔离测试 */
public class SecurityPoolExcelWarningOnlyTest {

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
        Fixture f = new Fixture();
        f.pool.setLockFlag(1);
        when(f.mapper.querySecurityInForbiddenPool("BOND.IB")).thenReturn(true);
        List<IpAdjustLogBo> logs = Arrays.asList(log("Excel导入", "E1", "调入"), log("关联调整", "E1", "调入"));
        f.service.recheckBeforeFinalApproval(logs);
        verify(f.mapper, never()).querySecurityInForbiddenPool("BOND.IB");
    }

    @Test
    public void normalAndExcelClearShouldStillBeBlockedByForbiddenPool() {
        Fixture f = new Fixture();
        when(f.mapper.querySecurityInForbiddenPool("BOND.IB")).thenReturn(true);
        for (String type : Arrays.asList("手工调整", "手动批量调整", "Excel清空")) {
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
    public void repeatedExcelInboundShouldRetireOldStatusBeforeAddingNewStatus() {
        Fixture f = new Fixture();
        IpAdjustLogBo log = log("Excel导入", "E1", "调入");
        when(f.mapper.addPoolStatus(log)).thenReturn(1);
        f.service.applyPoolStatusChanges(Collections.singletonList(log));
        InOrder order = inOrder(f.mapper);
        order.verify(f.mapper).deletePoolStatusSoft("BOND.IB", 100L);
        order.verify(f.mapper).addPoolStatus(log);
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
        final SecurityPoolAdjustService service = new SecurityPoolAdjustService();
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
