package com.znty.rrs.service;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.common.SecurityMarketDto;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckDto;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckReq;
import com.znty.rrs.entity.crmwpooladjust.CrmwPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.CrmwPoolAdjustMapper;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import org.junit.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** CRMW 批量流程与 Excel 提示放行的完整服务行为测试 */
public class CrmwPoolExcelWarningOnlyTest {
    /** 原规则原因保留，普通单笔和批量仍阻断，Excel 可选流程并提交 */
    @Test
    public void excelShouldKeepFailuresWhileSingleAndBatchStillReject() {
        Fixture f = new Fixture();
        f.pool.setLockFlag(1);
        for (AdjustCheckDto result : Arrays.asList(f.service.checkCrmwAdjust(f.request()),
                f.service.checkBatchAdjust(f.request()))) {
            assertThat(result.getItems()).allMatch(item -> !item.isCanAdjust());
            assertThat(result.getItems().get(0).getFlowOptions()).isEmpty();
        }
        AdjustCheckDto excel = f.service.checkExcelAdjust(f.request());
        assertThat(excel.getItems()).allMatch(AdjustCheckDto.CheckResultItem::isCanAdjust);
        assertThat(excel.getItems().get(0).getFailReasons()).isNotEmpty();
        assertThat(excel.getItems().get(0).getFlowOptions()).isNotEmpty();
    }

    /** 配置批量流程时保留单笔候选，未配置时保持单笔推荐 */
    @Test
    public void batchAndExcelShouldAppendConfiguredFlowInBothDirections() {
        for (String direction : Arrays.asList("调入", "调出")) {
            Fixture f = new Fixture();
            when(f.mapper.queryCrmwComboInPool("C.IB", "crmw", "A.IB", 10L))
                    .thenReturn("调出".equals(direction));
            when(f.mapper.querySecurityCurrentPoolIdList("A.IB", "C.IB", "crmw"))
                    .thenReturn("调出".equals(direction) ? Collections.singletonList(10L) : Collections.emptyList());
            AdjustCheckReq req = f.request();
            req.getItems().get(0).setAdjustMode(direction);
            AdjustCheckDto original = f.service.checkBatchAdjust(req);
            assertThat(original.getItems().get(0).getFlowOptions()).hasSize(1);
            assertThat(original.getItems().get(0).getFlowOptions().get(0).isRecommended()).isTrue();
            f.pool.setBatchInFlowId(107L);
            f.pool.setBatchOutFlowId(108L);
            for (AdjustCheckDto result : Arrays.asList(f.service.checkBatchAdjust(req), f.service.checkExcelAdjust(req))) {
                List<AdjustCheckDto.FlowOption> options = result.getItems().get(0).getFlowOptions();
                assertThat(options).hasSize(2);
                assertThat(options.get(0).getFlowType()).isEqualTo("调入".equals(direction) ? "batchInbound" : "batchOutbound");
                assertThat(options.get(0).isRecommended()).isTrue();
                assertThat(options.get(1).isRecommended()).isFalse();
            }
            assertThat(f.service.checkCrmwAdjust(req).getItems().get(0).getFlowOptions()).hasSize(1);
        }
    }

    /** 关联市场业务失败可提交，关联主档缺失仍阻断整个来源组 */
    @Test
    public void relatedBusinessFailuresShouldPassButMissingMasterShouldBlockGroup() {
        Fixture f = new Fixture();
        SecurityMarketDto related = new SecurityMarketDto();
        related.setWindCode("A.SH");
        when(f.markets.queryRelatedMarketCodes("A.IB")).thenReturn(Collections.singletonList(related));
        SecurityInfoBo other = new SecurityInfoBo();
        other.setWindCode("A.SH");
        other.setSecurityType("bond");
        when(f.mapper.querySecurityBoByCode("A.SH")).thenReturn(other);
        f.pool.setLockFlag(1);
        assertThat(f.service.checkExcelAdjust(f.request()).getItems()).hasSize(2)
                .allMatch(AdjustCheckDto.CheckResultItem::isCanAdjust)
                .allSatisfy(item -> assertThat(item.getFailReasons()).isNotEmpty());
        when(f.mapper.querySecurityBoByCode("A.SH")).thenReturn(null);
        assertThat(f.service.checkExcelAdjust(f.request()).getItems()).hasSize(2)
                .allMatch(item -> !item.isCanAdjust());
    }

    /** Excel 转换后仍执行 ABS 身份校验，不能用提示放行覆盖身份冲突 */
    @Test
    public void absIdentityConflictShouldRemainBlocking() {
        Fixture f = new Fixture();
        f.security.setAbsFlag(1);
        when(f.identity.queryIdentityGroupFailures(any(), anyMap())).thenAnswer(call -> {
            Map<String, Set<String>> groups = call.getArgument(1);
            Map<String, Set<String>> failures = new HashMap<>();
            groups.keySet().forEach(group -> failures.put(group, Collections.singleton("ABS 主体身份冲突")));
            return failures;
        });
        assertThat(f.service.checkExcelAdjust(f.request()).getItems()).allSatisfy(item -> {
            assertThat(item.isCanAdjust()).isFalse();
            assertThat(item.getFailReasons()).contains("ABS 主体身份冲突");
            assertThat(item.getFlowOptions()).isEmpty();
        });
    }

    /** Excel 主项及配套项在终审放行，其他批次仍阻断 */
    @Test
    public void finalApprovalShouldReleaseOnlyExcelBatchAndRequireRealData() {
        Fixture f = new Fixture();
        f.pool.setLockFlag(1);
        f.service.recheckBeforeFinalApproval(Arrays.asList(log("Excel导入", "E", "调入"), log("关联调整", "E", "调入")));
        assertThatThrownBy(() -> f.service.recheckBeforeFinalApproval(Arrays.asList(
                log("Excel导入", "E", "调入"), log("手动批量调整", "N", "调入"))))
                .isInstanceOf(BizException.class);
        when(f.mapper.querySecurityBoByCode("C.IB")).thenReturn(null);
        assertThatThrownBy(() -> f.service.recheckBeforeFinalApproval(Collections.singletonList(log("Excel导入", "E", "调入"))))
                .isInstanceOf(BizException.class).hasMessageContaining("凭证不存在");
        when(f.mapper.querySecurityBoByCode("A.IB")).thenReturn(null);
        assertThatThrownBy(() -> f.service.recheckBeforeFinalApproval(Collections.singletonList(log("Excel导入", "E", "调入"))))
                .isInstanceOf(BizException.class).hasMessageContaining("标的证券不存在");
        when(f.pools.queryPoolList()).thenReturn(Collections.emptyList());
        assertThatThrownBy(() -> f.service.recheckBeforeFinalApproval(Collections.singletonList(log("Excel导入", "E", "调入"))))
                .isInstanceOf(BizException.class).hasMessageContaining("投资池不存在");
    }

    /** 重复入池仅替换原组合，已出库的 Excel 调出可以完成 */
    @Test
    public void excelShouldReplaceOnlyExactCombinationAndAllowAbsentOutbound() {
        Fixture f = new Fixture();
        IpAdjustLogBo inbound = log("Excel导入", "E", "调入");
        when(f.mapper.addPoolStatus(inbound)).thenReturn(1);
        f.service.applyPoolStatusChanges(Collections.singletonList(inbound));
        InOrder order = inOrder(f.mapper);
        order.verify(f.mapper).deletePoolStatusSoft("A.IB", "C.IB", "crmw", 10L);
        order.verify(f.mapper).addPoolStatus(inbound);
        f.service.applyPoolStatusChanges(Collections.singletonList(log("Excel导入", "E", "调出")));
        assertThatThrownBy(() -> f.service.applyPoolStatusChanges(Collections.singletonList(log("手动批量调整", "N", "调出"))))
                .isInstanceOf(BizException.class);
    }

    /** Excel 的实际直通提交不再被在途、报告必填或组合状态阻断 */
    @Test
    public void excelSubmitShouldReachPoolStatusWithRequiredReportsAndPendingProcess() {
        Fixture f = new Fixture();
        f.pool.setLockFlag(1);
        f.pool.setInReportRestriction("internal");
        when(f.mapper.queryCrmwComboInPool("C.IB", "crmw", "A.IB", 10L)).thenReturn(true);
        when(f.mapper.queryPendingManualTargetPoolIdList("A.IB", "C.IB", "crmw", null))
                .thenReturn(Collections.singletonList(10L));
        when(f.mapper.addPoolStatus(any())).thenReturn(1);
        when(f.mapper.addAdjustLog(any())).thenAnswer(call -> {
            IpAdjustLogBo log = call.getArgument(0);
            log.setId(100L);
            return 1;
        });
        CrmwPoolAdjustSubmitReq req = new CrmwPoolAdjustSubmitReq();
        req.setSecurityCode("A.IB");
        req.setSecurityType("bond");
        req.setCrmwScode("C.IB");
        req.setCrmwName("测试凭证");
        req.setCrmwStype("crmw");
        req.setAdjusterId("1");
        req.setAdjusterName("测试用户");
        req.setAdjustType("Excel导入");
        CrmwPoolAdjustSubmitReq.AdjustItem item = new CrmwPoolAdjustSubmitReq.AdjustItem();
        item.setTargetPoolId(10L);
        item.setPoolType("crmw");
        item.setAdjustMode("调入");
        item.setItemTag("manual");
        item.setAdjustGroupKey("G");
        req.setItems(Collections.singletonList(item));
        f.service.submitAdjustLog(req, mock(SysAttachmentService.SubmissionFiles.class), new CrmwPoolAdjustService.BatchNoContext());
        verify(f.mapper).addPoolStatus(any());
        verify(f.mapper).addAdjustSecuritySnapshotCrmw(argThat(snapshot ->
                "A.IB".equals(snapshot.getWindCode()) && "C.IB".equals(snapshot.getCrmwScode())
                        && Long.valueOf(100L).equals(snapshot.getAdjustLogId())));
    }

    /** 构造指定来源和批次的完整 CRMW 组合日志 */
    private static IpAdjustLogBo log(String type, String batch, String direction) {
        IpAdjustLogBo log = new IpAdjustLogBo();
        log.setId(1L);
        log.setSecurityCode("A.IB");
        log.setCrmwScode("C.IB");
        log.setCrmwStype("crmw");
        log.setTargetPoolId(10L);
        log.setAdjustType(type);
        log.setAdjustBatchNo(batch);
        log.setAdjustMode(direction);
        return log;
    }

    /** 提供真实服务和内存查询桩 */
    private static class Fixture {
        /** 待测组合调库服务 */
        final CrmwPoolAdjustService service = AbsIdentityTestSupport.withIdentity(new CrmwPoolAdjustService());
        /** 组合主档和池状态查询桩 */
        final CrmwPoolAdjustMapper mapper = mock(CrmwPoolAdjustMapper.class);
        /** 投资池查询桩 */
        final InvestmentPoolMapper pools = mock(InvestmentPoolMapper.class);
        /** 市场及同券查询桩 */
        final SecurityMarketService markets = mock(SecurityMarketService.class);
        /** 主体身份校验桩 */
        final AbsIdentityService identity = mock(AbsIdentityService.class);
        /** 目标组合池 */
        final InvestmentPoolBo pool = new InvestmentPoolBo();
        /** 主标的主档 */
        final SecurityInfoBo security = new SecurityInfoBo();

        Fixture() {
            ReflectionTestUtils.setField(service, "crmwPoolAdjustMapper", mapper);
            ReflectionTestUtils.setField(service, "investmentPoolMapper", pools);
            ReflectionTestUtils.setField(service, "securityMarketService", markets);
            ReflectionTestUtils.setField(service, "absIdentityService", identity);
            ReflectionTestUtils.setField(service, "flowMapper", mock(FlowMapper.class));
            ReflectionTestUtils.setField(service, "ratingDowngradeChecker", mock(RatingDowngradeChecker.class));
            ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));
            ReflectionTestUtils.setField(service, "reportService", mock(ReportService.class));
            SecurityPoolAdjustService bonds = mock(SecurityPoolAdjustService.class);
            when(bonds.applySelectedRatingCompany(any(), any(), any(), any(), anyBoolean()))
                    .thenReturn(new SecurityPoolAdjustService.SelectedRatingCompanyData());
            when(bonds.applySelectedRatingCompany(any(), any(), any(), any(), anyBoolean(), any()))
                    .thenReturn(new SecurityPoolAdjustService.SelectedRatingCompanyData());
            ReflectionTestUtils.setField(service, "securityPoolAdjustService", bonds);
            pool.setId(10L);
            pool.setPoolType("crmw");
            pool.setPoolName("CRMW库");
            pool.setStatus("enabled");
            when(pools.queryPoolList()).thenReturn(Collections.singletonList(pool));
            security.setWindCode("A.IB");
            security.setSecurityType("bond");
            when(mapper.querySecurityBoByCode("A.IB")).thenReturn(security);
            SecurityInfoBo certificate = new SecurityInfoBo();
            certificate.setWindCode("C.IB");
            certificate.setSecurityType("crmw");
            when(mapper.querySecurityBoByCode("C.IB")).thenReturn(certificate);
        }

        /** 构造单目标池的组合调入请求 */
        AdjustCheckReq request() {
            AdjustCheckReq req = new AdjustCheckReq();
            req.setSecurityCode("A.IB");
            req.setCrmwScode("C.IB");
            req.setCrmwStype("crmw");
            AdjustCheckReq.CheckItem item = new AdjustCheckReq.CheckItem();
            item.setTargetPoolId(10L);
            item.setPoolType("crmw");
            item.setAdjustMode("调入");
            req.setItems(Collections.singletonList(item));
            return req;
        }
    }
}
