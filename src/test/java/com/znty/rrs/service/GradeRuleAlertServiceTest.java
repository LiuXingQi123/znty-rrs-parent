package com.znty.rrs.service;

import com.znty.rrs.entity.bo.CreditBondInnerRatingGradeBo;
import com.znty.rrs.entity.bo.CreditBondTermBucketBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpGradeRuleAlertBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.graderulealert.GradeRuleAlertScanDto;
import com.znty.rrs.entity.graderulealert.GradeRuleAlertReq;
import com.znty.rrs.entity.securitypooladjust.RelatedRatingCompanyDto;
import com.znty.rrs.entity.securitypooladjust.SecurityInfoDetailDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.AbsIdentityMapper;
import com.znty.rrs.mapper.CreditBondGradeRuleMapper;
import com.znty.rrs.mapper.GradeRuleAlertMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证分级规则扫描沿用入库主体并读取最新评分，以及旧待办的处理边界 */
public class GradeRuleAlertServiceTest {

    /** 证券与快照查询组件 */
    private SecurityPoolAdjustMapper securityMapper;
    /** 矩阵查询组件 */
    private CreditBondGradeRuleMapper gradeMapper;
    /** 提醒读写组件 */
    private GradeRuleAlertMapper alertMapper;
    /** ABS 主体提醒与状态组件 */
    private AbsIdentityMapper absIdentityMapper;
    /** 投资池查询组件 */
    private InvestmentPoolMapper poolMapper;
    /** 复用的证券池校验服务 */
    private SecurityPoolAdjustService securityService;
    /** ABS 主体联动依据摘要 */
    private AbsCompanySyncService absCompanySyncService;
    /** 本次扫描任务 */
    private GradeRuleAlertService alertService;
    /** 模拟主档，查询时返回副本，避免一次校验修改后污染后续记录 */
    private SecurityInfoBo master;
    /** 当前入库记录实际采用的主体快照 */
    private SecurityInfoDetailDto snapshot;

    /** 准备真实业务服务和隔离的主档、快照、评分及矩阵查询 */
    @Before
    public void setUp() {
        securityMapper = mock(SecurityPoolAdjustMapper.class);
        gradeMapper = mock(CreditBondGradeRuleMapper.class);
        alertMapper = mock(GradeRuleAlertMapper.class);
        absIdentityMapper = mock(AbsIdentityMapper.class);
        poolMapper = mock(InvestmentPoolMapper.class);
        absCompanySyncService = mock(AbsCompanySyncService.class);
        securityService = AbsIdentityTestSupport.withIdentity(new SecurityPoolAdjustService());
        ReflectionTestUtils.setField(securityService, "securityPoolAdjustMapper", securityMapper);
        ReflectionTestUtils.setField(securityService, "creditBondGradeRuleMapper", gradeMapper);
        ReflectionTestUtils.setField(securityService, "investmentPoolMapper", poolMapper);
        alertService = new GradeRuleAlertService();
        ReflectionTestUtils.setField(alertService, "gradeRuleAlertMapper", alertMapper);
        ReflectionTestUtils.setField(alertService, "securityPoolAdjustService", securityService);
        ReflectionTestUtils.setField(alertService, "securityPoolAdjustMapper", securityMapper);
        ReflectionTestUtils.setField(alertService, "absIdentityMapper", absIdentityMapper);
        ReflectionTestUtils.setField(alertService, "absCompanySyncService", absCompanySyncService);
        ReflectionTestUtils.setField(alertService, "investmentPoolMapper", poolMapper);

        master = new SecurityInfoBo();
        master.setWindCode("TEST_ABS.SH");
        master.setAbsFlag(1);
        master.setInnerIssuerRating("3");
        master.setDateExistsStr("6个月");
        when(securityMapper.querySecurityBoByCode("TEST_ABS.SH")).thenAnswer(invocation -> {
            SecurityInfoBo copy = new SecurityInfoBo();
            BeanUtils.copyProperties(master, copy);
            return copy;
        });
        when(securityMapper.querySecurityCurrentPoolIdList("TEST_ABS.SH"))
                .thenReturn(Arrays.asList(2L, 3L));
        snapshot = new SecurityInfoDetailDto();
        snapshot.setGuarantorId("SELECTED_A");
        snapshot.setGuarantor("入库自选权益人");
        snapshot.setInnerGuarantorRating("4");
        when(securityMapper.querySecuritySnapshotDetailByAdjustLogId(101L, "TEST_ABS.SH"))
                .thenReturn(snapshot);
        // 入库实际主体的最新评分为 1，主档及快照评分均不参与本次矩阵取值
        when(securityMapper.queryLatestRatingCompanyByCode("SELECTED_A")).thenReturn(rating("1"));
        when(poolMapper.queryPoolList()).thenReturn(Arrays.asList(pool(2L, 1), pool(3L, 2)));
        CreditBondTermBucketBo bucket = new CreditBondTermBucketBo();
        bucket.setBucketCode("LE_1");
        bucket.setMinTermYear(BigDecimal.ZERO);
        bucket.setMinInclusive(1);
        bucket.setMaxTermYear(BigDecimal.ONE);
        bucket.setMaxInclusive(1);
        when(gradeMapper.queryEnabledTermBucketList()).thenReturn(Collections.singletonList(bucket));
        for (String code : Arrays.asList("1", "2", "3")) {
            when(gradeMapper.queryAllowedPoolIdsByGradeAndBucket(code, "LE_1"))
                    .thenReturn(Collections.singletonList(2L));
        }
        List<CreditBondInnerRatingGradeBo> grades = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            CreditBondInnerRatingGradeBo grade = new CreditBondInnerRatingGradeBo();
            grade.setGradeCode(String.valueOf(i));
            grade.setSortNo(i);
            grades.add(grade);
        }
        when(gradeMapper.queryEnabledRatingGradeList()).thenReturn(grades);
    }

    /** 主档评分为空时，按入库实际主体最新评分复核，不误报无内评 */
    @Test
    public void shouldUseSelectedCompanyWhenMasterRatingMissing() {
        assertThat(securityService.evaluateGradedInboundForPool("TEST_ABS.SH", 2L, 101L)).isNull();
        verify(gradeMapper).queryAllowedPoolIdsByGradeAndBucket("1", "LE_1");
        verify(securityMapper, never()).queryRelatedRatingCompanyList(anyString());
        verify(securityMapper, never()).querySelfSelectedRightsHolderByCode(anyString());
        assertThat(master.getInnerGuarantorRating()).isNull();
        assertThat(snapshot.getInnerGuarantorRating()).isEqualTo("4");
    }

    /** 主档残留旧评分时，不再因旧评分对应矩阵为空而误报 */
    @Test
    public void shouldIgnoreStaleMasterAndSnapshotRatings() {
        master.setInnerGuarantorRating("4");
        assertThat(securityService.evaluateGradedInboundForPool("TEST_ABS.SH", 2L, 101L)).isNull();
        verify(gradeMapper, never()).queryAllowedPoolIdsByGradeAndBucket("4", "LE_1");
    }

    /** 入库后的真实评级下调仍需提醒，不能一直使用快照旧评分 */
    @Test
    public void shouldAlertWhenSelectedCompanyRatingDowngrades() {
        snapshot.setInnerGuarantorRating("1");
        when(securityMapper.queryLatestRatingCompanyByCode("SELECTED_A")).thenReturn(rating("2"));
        assertThat(securityService.evaluateGradedInboundForPool("TEST_ABS.SH", 2L, 101L))
                .contains("特殊债调整后的允许范围", "仅 2 级及更差");
    }

    /** 同券不同池沿用各自入库记录的主体，不取全券最近一笔快照 */
    @Test
    public void shouldUseSnapshotOfEachPoolEntry() {
        SecurityInfoDetailDto other = new SecurityInfoDetailDto();
        other.setGuarantorId("SELECTED_B");
        when(securityMapper.querySecuritySnapshotDetailByAdjustLogId(102L, "TEST_ABS.SH")).thenReturn(other);
        when(securityMapper.queryLatestRatingCompanyByCode("SELECTED_B")).thenReturn(rating("2"));
        assertThat(securityService.evaluateGradedInboundForPool("TEST_ABS.SH", 2L, 101L)).isNull();
        assertThat(securityService.evaluateGradedInboundForPool("TEST_ABS.SH", 3L, 102L)).isNull();
        verify(securityMapper).queryLatestRatingCompanyByCode("SELECTED_A");
        verify(securityMapper).queryLatestRatingCompanyByCode("SELECTED_B");
    }

    /** 最新评分确实缺失时保留提醒并给出主体代码，不回退快照或发行主体评分 */
    @Test
    public void shouldAlertWhenLatestRatingMissing() {
        snapshot.setInnerGuarantorRating("1");
        when(securityMapper.queryLatestRatingCompanyByCode("SELECTED_A")).thenReturn(null);
        assertThat(securityService.evaluateGradedInboundForPool("TEST_ABS.SH", 2L, 101L))
                .contains("所选权益人无内评分", "SELECTED_A");
        verify(gradeMapper, never()).queryAllowedPoolIdsByGradeAndBucket(anyString(), anyString());
    }

    /** 最新评分记录存在但为空白时，同样不能沿用旧评分 */
    @Test
    public void shouldNotFallbackWhenLatestRatingBlank() {
        when(securityMapper.queryLatestRatingCompanyByCode("SELECTED_A")).thenReturn(rating(" "));
        assertThat(securityService.evaluateGradedInboundForPool("TEST_ABS.SH", 2L, 101L))
                .contains("所选权益人无内评分", "SELECTED_A");
    }

    /** 缺少来源日志时明确报出证券和池，不猜选关系主体 */
    @Test
    public void shouldRejectMissingEntryLog() {
        assertThatThrownBy(() -> securityService.evaluateGradedInboundForPool("TEST_ABS.SH", 2L, null))
                .isInstanceOf(BizException.class).hasMessageContaining("TEST_ABS.SH")
                .hasMessageContaining("当前池[2]").hasMessageContaining("缺少来源入库日志");
    }

    /** 缺少精确入库快照时不读取其他调库记录代替 */
    @Test
    public void shouldRejectMissingExactEntrySnapshot() {
        assertThatThrownBy(() -> securityService.evaluateGradedInboundForPool("TEST_ABS.SH", 2L, 999L))
                .isInstanceOf(BizException.class).hasMessageContaining("入库日志[999]")
                .hasMessageContaining("缺少入库证券快照");
        verify(securityMapper, never()).queryLatestSecuritySnapshotDetailByWindCode(anyString());
    }

    /** 快照缺少实际评级主体代码时不能按名称推断 */
    @Test
    public void shouldRejectMissingSelectedCompanyCode() {
        snapshot.setGuarantorId(" ");
        assertThatThrownBy(() -> securityService.evaluateGradedInboundForPool("TEST_ABS.SH", 2L, 101L))
                .isInstanceOf(BizException.class).hasMessageContaining("入库日志[101]")
                .hasMessageContaining("缺少实际评级主体代码");
    }

    /** 非 ABS 担保债仍取发行主体与实际担保人最新内评的较优值 */
    @Test
    public void guaranteedBondShouldUseLatestSelectedGuarantor() {
        master.setAbsFlag(0);
        master.setGuarantFlag(1);
        master.setInnerGuarantorRating("1");
        when(securityMapper.queryLatestRatingCompanyByCode("SELECTED_A")).thenReturn(rating("2"));
        assertThat(securityService.evaluateGradedInboundForPool("TEST_ABS.SH", 2L, 101L)).isNull();
        verify(gradeMapper).queryAllowedPoolIdsByGradeAndBucket("2", "LE_1");
    }

    /** 担保人暂未评级时仍遵循人工规则，使用发行主体评分查矩阵 */
    @Test
    public void guaranteedBondWithoutGuarantorRatingShouldKeepIssuerRule() {
        master.setAbsFlag(0);
        master.setGuarantFlag(1);
        when(securityMapper.queryLatestRatingCompanyByCode("SELECTED_A")).thenReturn(null);
        assertThat(securityService.evaluateGradedInboundForPool("TEST_ABS.SH", 2L, 101L)).isNull();
        verify(gradeMapper).queryAllowedPoolIdsByGradeAndBucket("3", "LE_1");
    }

    /** 普通非担保债不增加快照依赖，保持原主体评分口径 */
    @Test
    public void ordinaryBondShouldKeepOriginalRatingSource() {
        master.setAbsFlag(0);
        master.setGuarantFlag(0);
        assertThat(securityService.evaluateGradedInboundForPool("TEST_ABS.SH", 2L, null)).isNull();
        verify(securityMapper, never()).querySecuritySnapshotDetailByAdjustLogId(anyLong(), anyString());
        verify(securityMapper, never()).queryLatestRatingCompanyByCode(anyString());
        verify(gradeMapper).queryAllowedPoolIdsByGradeAndBucket("3", "LE_1");
    }

    /** 不适用信用债分级库的品种保留原失败原因，不先因缺少快照而阻断 */
    @Test
    public void excludedBondShouldKeepOriginalFailure() {
        master.setAbsFlag(0);
        master.setGuarantFlag(1);
        master.setSecurityType("convertible_bond");
        assertThat(securityService.evaluateGradedInboundForPool("TEST_ABS.SH", 2L, null))
                .contains("不适用信用债分级库");
    }

    /** 原误报已合规时不再刷新待办，由原有本轮失效逻辑关闭旧提醒 */
    @Test
    public void taskShouldExpireOldFalseAlertWhenLatestRatingAllows() {
        when(alertMapper.queryGradedBondInPoolList()).thenReturn(Collections.singletonList(scan(2L, 101L)));
        when(alertMapper.editStaleOpenAlertInvalid(anyString())).thenReturn(1);
        ScheduledTaskResult result = alertService.execute();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("本轮命中 0 条", "本轮失效 1 条");
        verify(securityMapper).querySecuritySnapshotDetailByAdjustLogId(101L, "TEST_ABS.SH");
        verify(alertMapper, never()).addAlert(any(IpGradeRuleAlertBo.class));
        verify(alertMapper).editStaleOpenAlertInvalid(anyString());
    }

    /** 最新内评确实缺失时生成可定位主体的待办，不改变池状态 */
    @Test
    public void taskShouldCreateAlertForActualMissingRating() {
        when(alertMapper.queryGradedBondInPoolList()).thenReturn(Collections.singletonList(scan(2L, 101L)));
        when(securityMapper.queryLatestRatingCompanyByCode("SELECTED_A")).thenReturn(null);
        ScheduledTaskResult result = alertService.execute();
        assertThat(result.getMessage()).contains("本轮命中 1 条");
        ArgumentCaptor<IpGradeRuleAlertBo> captor = ArgumentCaptor.forClass(IpGradeRuleAlertBo.class);
        verify(alertMapper).addAlert(captor.capture());
        assertThat(captor.getValue().getFailReason()).contains("SELECTED_A");
        assertThat(captor.getValue().getAlertStatus()).isEqualTo("00");
    }

    /** 快照缺失中止本轮，异常传播给事务及任务执行器，不能继续关闭旧提醒 */
    @Test
    public void taskShouldNotExpireAlertsWhenEntryIdentityMissing() {
        when(alertMapper.queryGradedBondInPoolList()).thenReturn(Collections.singletonList(scan(2L, 999L)));
        assertThatThrownBy(() -> alertService.execute()).isInstanceOf(BizException.class);
        verify(alertMapper, never()).editStaleOpenAlertInvalid(anyString());
        verify(alertMapper, never()).addAlert(any(IpGradeRuleAlertBo.class));
    }

    /** 同一依据已标记已处理时，下一轮扫描不再新增待办 */
    @Test
    public void taskShouldSkipAlertWhenSameBasisAlreadyProcessed() {
        when(alertMapper.queryGradedBondInPoolList()).thenReturn(Collections.singletonList(scan(2L, 101L)));
        when(securityMapper.queryLatestRatingCompanyByCode("SELECTED_A")).thenReturn(null);
        alertService.execute();
        ArgumentCaptor<IpGradeRuleAlertBo> captor = ArgumentCaptor.forClass(IpGradeRuleAlertBo.class);
        verify(alertMapper).addAlert(captor.capture());
        assertThat(captor.getValue().getContextKey()).isNotBlank();
        IpGradeRuleAlertBo processed = new IpGradeRuleAlertBo();
        processed.setAlertStatus("20");
        processed.setContextKey(captor.getValue().getContextKey());
        when(alertMapper.queryLatestGradeAlert("TEST_ABS.SH", 2L)).thenReturn(processed);

        ScheduledTaskResult result = alertService.execute();

        assertThat(result.getMessage()).contains("沿用已处理 1 条");
        verify(alertMapper, times(1)).addAlert(any(IpGradeRuleAlertBo.class));
    }

    /** 依据变化后仍要提醒，不能因为同券同池处理过就永久屏蔽 */
    @Test
    public void taskShouldAlertAgainWhenBasisChanged() {
        IpGradeRuleAlertBo processed = new IpGradeRuleAlertBo();
        processed.setAlertStatus("20");
        processed.setContextKey(GradeRuleAlertService.buildGradeContextKey("TEST_ABS.SH", 2L, 1, "旧原因"));
        when(alertMapper.queryLatestGradeAlert("TEST_ABS.SH", 2L)).thenReturn(processed);
        when(alertMapper.queryGradedBondInPoolList()).thenReturn(Collections.singletonList(scan(2L, 101L)));
        when(securityMapper.queryLatestRatingCompanyByCode("SELECTED_A")).thenReturn(null);

        ScheduledTaskResult result = alertService.execute();

        assertThat(result.getMessage()).contains("本轮命中 1 条", "沿用已处理 0 条");
        verify(alertMapper).addAlert(any(IpGradeRuleAlertBo.class));
    }

    /** 标记已处理时把当前依据摘要写回待办，供下一轮扫描沿用 */
    @Test
    public void processedAlertShouldBindCurrentBasis() {
        IpGradeRuleAlertBo alert = new IpGradeRuleAlertBo();
        alert.setId(5L);
        alert.setSecurityCode("BOND1.SH");
        alert.setCurrentPoolId(2L);
        alert.setCurrentSort(3);
        alert.setFailReason("内评不足");
        alert.setAlertStatus("00");
        when(alertMapper.queryAlertById(5L)).thenReturn(alert);
        when(alertMapper.editAlertProcessed(any(IpGradeRuleAlertBo.class))).thenReturn(1);

        GradeRuleAlertReq req = new GradeRuleAlertReq();
        req.setId(5L);
        req.setCurrentUserId("user");
        req.setCurrentUserName("研究员");
        alertService.editAlertProcessed(req);

        ArgumentCaptor<IpGradeRuleAlertBo> captor = ArgumentCaptor.forClass(IpGradeRuleAlertBo.class);
        verify(alertMapper).editAlertProcessed(captor.capture());
        assertThat(captor.getValue().getContextKey()).isEqualTo(
                GradeRuleAlertService.buildGradeContextKey("BOND1.SH", 2L, 3, "内评不足"));
    }

    /** ABS 主体联动提醒不能走普通标记已处理，避免覆盖 ABS 人工结论 */
    @Test
    public void shouldRejectProcessingAbsAlertAsGrade() {
        IpGradeRuleAlertBo alert = new IpGradeRuleAlertBo();
        alert.setId(6L);
        alert.setRuleType("absCompanySync");
        alert.setAlertStatus("00");
        when(alertMapper.queryAlertById(6L)).thenReturn(alert);

        GradeRuleAlertReq req = new GradeRuleAlertReq();
        req.setId(6L);

        assertThatThrownBy(() -> alertService.editAlertProcessed(req))
                .isInstanceOf(BizException.class).hasMessageContaining("确认主体");
        verify(alertMapper, never()).editAlertProcessed(any(IpGradeRuleAlertBo.class));
    }

    /** ABS 忽略入池同步删除自动调库日志和在池状态，不走普通调库删除入口 */    @Test
    public void absIgnoreShouldDeletePassiveInboundRecords() {
        IpGradeRuleAlertBo alert = new IpGradeRuleAlertBo();
        alert.setId(1L);
        alert.setSecurityCode("TEST_ABS.SH");
        alert.setCurrentPoolId(2L);
        alert.setRuleType("absCompanySync");
        alert.setAlertStatus("00");
        when(alertMapper.queryAlertById(1L)).thenReturn(alert);
        when(absIdentityMapper.editAbsAlertDecision(any(IpGradeRuleAlertBo.class))).thenReturn(1);

        GradeRuleAlertReq req = new GradeRuleAlertReq();
        req.setId(1L);
        req.setDecision("noNeed");
        req.setCurrentUserId("user");
        req.setCurrentUserName("研究员");
        alertService.processAbsAlert(req);

        verify(absIdentityMapper).deletePassiveInboundLog("TEST_ABS.SH", 2L);
        verify(absIdentityMapper).deletePassiveInboundStatus("TEST_ABS.SH", 2L);
        verify(securityMapper, never()).deletePoolStatusSoft(anyString(), anyLong());
    }

    /** 确认主体时才把待确认日志转为生效并写入池状态 */
    @Test
    public void absConfirmShouldPromotePendingInboundToPoolStatus() {
        IpGradeRuleAlertBo alert = new IpGradeRuleAlertBo();
        alert.setId(1L);
        alert.setSecurityCode("TEST_ABS.SH");
        alert.setCurrentPoolId(2L);
        alert.setCurrentPoolName("目标池");
        alert.setRuleType("absCompanySync");
        alert.setAlertStatus("00");
        IpAdjustLogBo pending = new IpAdjustLogBo();
        pending.setId(10L);
        pending.setSecurityCode("TEST_ABS.SH");
        pending.setSecurityShortName("测试ABS");
        pending.setSecurityType("abs");
        pending.setAdjustType("自动调整");
        pending.setAdjustMode("调入");
        pending.setAdjustBatchNo("BOND20260101000000001");
        pending.setTargetPoolId(2L);
        pending.setTargetPoolName("目标池");
        pending.setPoolType("forbidden");
        pending.setFlowType(AbsCompanySyncService.ABS_PENDING_FLOW_TYPE);
        pending.setAuditStatus("00");
        pending.setAdjusterId("0");
        pending.setAdjusterName("系统");
        pending.setAdjustReason("ABS主体自动联动入池（待确认）");
        pending.setSubmitTime(new Date());
        when(alertMapper.queryAlertById(1L)).thenReturn(alert);
        when(absIdentityMapper.queryPendingScheduledInboundLog("TEST_ABS.SH", 2L)).thenReturn(pending);
        when(absIdentityMapper.editPendingScheduledInboundApproved(10L,
                "ABS主体自动联动入池（研究员确认主体：C10001，已完成入池）")).thenReturn(1);
        when(absIdentityMapper.editApprovedIdentity(any(SecurityInfoBo.class))).thenReturn(1);
        when(absIdentityMapper.editAbsAlertDecision(any(IpGradeRuleAlertBo.class))).thenReturn(1);
        when(securityMapper.querySecurityCurrentPoolIdList("TEST_ABS.SH"))
                .thenReturn(Collections.singletonList(3L));
        when(securityMapper.addPoolStatus(any(IpAdjustLogBo.class))).thenReturn(1);
        when(securityMapper.queryAllPoolRelationList()).thenReturn(Collections.<com.znty.rrs.entity.bo.PoolRelationBo>emptyList());
        InvestmentPoolBo targetPool = new InvestmentPoolBo();
        targetPool.setId(2L);
        targetPool.setPoolName("目标池");
        targetPool.setPoolType("forbidden");
        InvestmentPoolBo oldPool = new InvestmentPoolBo();
        oldPool.setId(3L);
        oldPool.setPoolName("原池");
        when(poolMapper.queryPoolList()).thenReturn(Arrays.asList(targetPool, oldPool));

        AbsIdentityService identity = mock(AbsIdentityService.class);
        doAnswer(invocation -> {
            SecurityInfoBo selected = invocation.getArgument(0);
            selected.setIssuerCode(invocation.getArgument(1));
            selected.setIssuer(invocation.getArgument(2));
            selected.setInnerIssuerRating(invocation.getArgument(3));
            selected.setRatingBondissuer(invocation.getArgument(4));
            selected.setRatingBondissuerAgency(invocation.getArgument(5));
            return null;
        }).when(identity).applySelection(any(SecurityInfoBo.class), anyString(), anyString(),
                anyString(), anyString(), anyString());
        ReflectionTestUtils.setField(alertService, "absIdentityService", identity);
        when(securityMapper.querySecurityBoByCode("TEST_ABS.SH")).thenReturn(master);

        GradeRuleAlertReq req = new GradeRuleAlertReq();
        req.setId(1L);
        req.setDecision("confirm");
        req.setIssuerCode("C10001");
        req.setIssuerName("确认主体");
        req.setInnerIssuerRating("2");
        req.setRatingBondissuer("AA+");
        req.setRatingBondissuerAgency("R1");
        req.setCurrentUserId("user");
        req.setCurrentUserName("研究员");

        alertService.processAbsAlert(req);

        ArgumentCaptor<IpAdjustLogBo> statusCaptor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(securityMapper).addPoolStatus(statusCaptor.capture());
        assertThat(statusCaptor.getValue().getAdjustLogId()).isEqualTo(10L);
        assertThat(statusCaptor.getValue().getSecurityShortName()).isEqualTo("测试ABS");
        assertThat(statusCaptor.getValue().getSecurityType()).isEqualTo("abs");
        assertThat(statusCaptor.getValue().getAdjustType()).isEqualTo("自动调整");
        assertThat(statusCaptor.getValue().getAdjustMode()).isEqualTo("调入");
        assertThat(statusCaptor.getValue().getTargetPoolId()).isEqualTo(2L);
        assertThat(statusCaptor.getValue().getTargetPoolName()).isEqualTo("目标池");
        assertThat(statusCaptor.getValue().getPoolType()).isEqualTo("forbidden");
        assertThat(statusCaptor.getValue().getFlowType())
                .isEqualTo(AbsCompanySyncService.ABS_PENDING_FLOW_TYPE);
        assertThat(statusCaptor.getValue().getAuditStatus()).isEqualTo("20");
        assertThat(statusCaptor.getValue().getAdjusterId()).isEqualTo("0");
        assertThat(statusCaptor.getValue().getAdjusterName()).isEqualTo("系统");
        assertThat(statusCaptor.getValue().getSubmitTime()).isNotNull();
        assertThat(statusCaptor.getValue().getAdjustReason())
                .isEqualTo("ABS主体自动联动入池（研究员确认主体：C10001，已完成入池）");
        verify(absIdentityMapper).editApprovedIdentity(any(SecurityInfoBo.class));
        verify(absIdentityMapper).editPendingScheduledInboundApproved(10L,
                "ABS主体自动联动入池（研究员确认主体：C10001，已完成入池）");
        // 确认主体后按新身份重算依据摘要，避免后续同主体同依据重复提醒
        verify(absCompanySyncService).resolveContextKey("TEST_ABS.SH", "C10001", 2L);
    }

    /** 创建本次扫描对应的在池记录 */
    private GradeRuleAlertScanDto scan(Long poolId, Long logId) {
        GradeRuleAlertScanDto row = new GradeRuleAlertScanDto();
        row.setSecurityCode("TEST_ABS.SH");
        row.setCurrentPoolId(poolId);
        row.setAdjustLogId(logId);
        return row;
    }

    /** 创建用于校验分级库范围的投资池 */
    private InvestmentPoolBo pool(Long id, int sort) {
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(id);
        pool.setPoolName(sort + "级库");
        pool.setPoolType("credit_bond");
        pool.setPoolLevel(2);
        pool.setInnerSort(sort);
        return pool;
    }

    /** 创建模拟的主体最新评分 */
    private RelatedRatingCompanyDto rating(String grade) {
        RelatedRatingCompanyDto rating = new RelatedRatingCompanyDto();
        rating.setInnerRating(grade);
        return rating;
    }
}
