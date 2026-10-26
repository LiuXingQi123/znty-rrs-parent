package com.znty.rrs.service;

import com.znty.rrs.entity.bo.CreditBondInnerRatingGradeBo;
import com.znty.rrs.entity.bo.CreditBondTermBucketBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpAdjustStepBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.securitypooladjust.RelatedRatingCompanyDto;
import com.znty.rrs.entity.securitypooladjust.RedirectPoolOptionDto;
import com.znty.rrs.entity.securitypooladjust.SecurityInfoDetailDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.CreditBondGradeRuleMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证审批改判按精确申请主体读取最新评分，并逐项校验和落地 */
public class SecurityPoolAdjustRedirectTest {

    /** 证券及申请快照查询组件 */
    private SecurityPoolAdjustMapper mapper;
    /** 内评矩阵查询组件 */
    private CreditBondGradeRuleMapper gradeMapper;
    /** 已有投资池查询组件 */
    private InvestmentPoolMapper poolMapper;
    /** 本次查询已加载的池数据 */
    private List<InvestmentPoolBo> pools;
    /** 实际执行评分解析的业务服务 */
    private SecurityPoolAdjustService service;
    /** 模拟包含多个评级主体的主档 */
    private SecurityInfoBo master;
    /** 当前申请记录 */
    private IpAdjustLogBo log;
    /** 当前申请选定的评级主体快照 */
    private SecurityInfoDetailDto snapshot;

    /** 构造包含旧评分、逗号串以及单一自选权益人的申请 */
    @Before
    public void setUp() {
        mapper = mock(SecurityPoolAdjustMapper.class);
        gradeMapper = mock(CreditBondGradeRuleMapper.class);
        poolMapper = mock(InvestmentPoolMapper.class);
        service = spy(new SecurityPoolAdjustService());
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "creditBondGradeRuleMapper", gradeMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        master = new SecurityInfoBo();
        master.setWindCode("ABS.SH");
        master.setAbsFlag(1);
        master.setInnerIssuerRating("3");
        master.setDateExistsStr("6个月");
        master.setGuarantorId("A,B");
        master.setGuarantor("权益人A,权益人B");
        master.setInnerGuarantorRating("1,2+");
        when(mapper.querySecurityBoByCode("ABS.SH")).thenAnswer(invocation -> {
            SecurityInfoBo copy = new SecurityInfoBo();
            BeanUtils.copyProperties(master, copy);
            return copy;
        });
        log = log(101L, "ABS.SH", "credit_bond", "调入", 2L);
        when(mapper.queryAdjustLogListForAudit(101L, null)).thenReturn(Collections.singletonList(log));
        snapshot = snapshot("SELF_SELECTED");
        snapshot.setGuarantor("本笔申请自选权益人");
        snapshot.setInnerGuarantorRating("4");
        when(mapper.querySecuritySnapshotDetailByAdjustLogId(101L, "ABS.SH")).thenReturn(snapshot);
        when(mapper.queryLatestRatingCompanyByCode("SELF_SELECTED")).thenReturn(rating("1"));
        pools = new ArrayList<>();
        List<CreditBondInnerRatingGradeBo> grades = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            InvestmentPoolBo pool = new InvestmentPoolBo();
            pool.setId((long) i + 1);
            pool.setPoolName(i + "级库");
            pool.setPoolType("credit_bond");
            pool.setPoolLevel(2);
            pool.setInnerSort(i);
            pool.setStatus("enabled");
            pool.setIsDeleted(0);
            pools.add(pool);
            CreditBondInnerRatingGradeBo grade = new CreditBondInnerRatingGradeBo();
            grade.setGradeCode(String.valueOf(i));
            grade.setSortNo(i);
            grades.add(grade);
            when(gradeMapper.queryAllowedPoolIdsByGradeAndBucket(String.valueOf(i), "LE_1"))
                    .thenReturn(Collections.singletonList((long) i + 1));
        }
        when(poolMapper.queryPoolList()).thenReturn(pools);
        when(gradeMapper.queryEnabledRatingGradeList()).thenReturn(grades);
        CreditBondTermBucketBo bucket = new CreditBondTermBucketBo();
        bucket.setBucketCode("LE_1");
        bucket.setMinTermYear(BigDecimal.ZERO);
        bucket.setMinInclusive(1);
        bucket.setMaxTermYear(BigDecimal.ONE);
        bucket.setMaxInclusive(1);
        when(gradeMapper.queryEnabledTermBucketList()).thenReturn(Collections.singletonList(bucket));
    }

    /** ABS 使用申请中的自选主体最新评分，忽略主档逗号串及快照旧评分 */
    @Test
    public void shouldUseLatestRatingOfApplicationSelectedCompany() {
        assertThat(service.queryRedirectPoolOptions("ABS.SH", 101L))
                .extracting(InvestmentPoolBo::getId).containsExactly(2L);
        verify(gradeMapper).queryAllowedPoolIdsByGradeAndBucket("1", "LE_1");
        verify(mapper, never()).queryRelatedRatingCompanyList(anyString());
        verify(mapper, never()).queryLatestSecuritySnapshotDetailByWindCode(anyString());
        assertThat(master.getInnerGuarantorRating()).isEqualTo("1,2+");
        assertThat(snapshot.getInnerGuarantorRating()).isEqualTo("4");
    }

    /** 完整路径复用当前已加载的池数据，保留同名候选和原名称，不增加查询 */
    @Test
    public void shouldBuildFullNamesFromAlreadyLoadedPools() {
        InvestmentPoolBo leaf = pools.get(0);
        leaf.setParentId(1L);
        leaf.setPoolName("一级库");
        InvestmentPoolBo root = new InvestmentPoolBo();
        root.setId(1L);
        root.setPoolName("信用债大库(new)");
        pools.add(root);
        InvestmentPoolBo oldRoot = new InvestmentPoolBo();
        oldRoot.setId(99L);
        oldRoot.setPoolName("信用债大库(old)");
        pools.add(oldRoot);
        InvestmentPoolBo oldLeaf = new InvestmentPoolBo();
        BeanUtils.copyProperties(leaf, oldLeaf);
        oldLeaf.setId(100L);
        oldLeaf.setParentId(99L);
        pools.add(oldLeaf);
        InvestmentPoolBo nestedRoot = new InvestmentPoolBo();
        nestedRoot.setId(200L);
        nestedRoot.setPoolName("中间层");
        nestedRoot.setParentId(99L);
        pools.add(nestedRoot);
        InvestmentPoolBo nestedLeaf = new InvestmentPoolBo();
        BeanUtils.copyProperties(leaf, nestedLeaf);
        nestedLeaf.setId(201L);
        nestedLeaf.setParentId(200L);
        pools.add(nestedLeaf);

        List<RedirectPoolOptionDto> options = service.queryRedirectPoolOptions("ABS.SH", 101L);

        assertThat(options).extracting(RedirectPoolOptionDto::getId).containsExactlyInAnyOrder(2L, 100L, 201L);
        assertThat(options).extracting(RedirectPoolOptionDto::getPoolFullName).containsExactlyInAnyOrder(
                "信用债大库(new)/一级库", "信用债大库(old)/一级库", "信用债大库(old)/中间层/一级库");
        assertThat(options).extracting(RedirectPoolOptionDto::getPoolName).containsOnly("一级库");
        assertThat(leaf.getPoolName()).isEqualTo("一级库");
        // 保持原有两次池数据查询，不为生成路径增加第三次数据库访问
        verify(poolMapper, times(2)).queryPoolList();
        verify(poolMapper, never()).queryPoolFullNameList();
    }

    /** 同券不同申请使用各自快照，不能拿最近一次申请或列表首个主体 */
    @Test
    public void shouldKeepRatingCompanyOfEachApplication() {
        IpAdjustLogBo other = log(102L, "ABS.SH", "credit_bond", "调入", 3L);
        when(mapper.queryAdjustLogListForAudit(102L, null)).thenReturn(Collections.singletonList(other));
        when(mapper.querySecuritySnapshotDetailByAdjustLogId(102L, "ABS.SH")).thenReturn(snapshot("A"));
        when(mapper.queryLatestRatingCompanyByCode("A")).thenReturn(rating("2"));
        assertThat(service.queryRedirectPoolOptions("ABS.SH", 101L))
                .extracting(InvestmentPoolBo::getId).containsExactly(2L);
        assertThat(service.queryRedirectPoolOptions("ABS.SH", 102L))
                .extracting(InvestmentPoolBo::getId).containsExactlyInAnyOrder(4L, 5L, 6L);
    }

    /** 担保债恢复所选担保人单一评分后，与发行主体内评取较优档 */
    @Test
    public void guaranteedBondShouldUseSelectedGuarantorAndBetterGrade() {
        master.setAbsFlag(0);
        master.setGuarantFlag(1);
        when(mapper.queryLatestRatingCompanyByCode("SELF_SELECTED")).thenReturn(rating("2"));
        assertThat(service.queryRedirectPoolOptions("ABS.SH", 101L))
                .extracting(InvestmentPoolBo::getId).containsExactlyInAnyOrder(3L, 4L, 5L, 6L);
        verify(gradeMapper).queryAllowedPoolIdsByGradeAndBucket("2", "LE_1");
    }

    /** 担保人暂无评分时沿用人工申请的发行主体评分规则，不能读回旧担保评分 */
    @Test
    public void guaranteedBondWithoutRatingShouldUseIssuerRule() {
        master.setAbsFlag(0);
        master.setGuarantFlag(1);
        when(mapper.queryLatestRatingCompanyByCode("SELF_SELECTED")).thenReturn(null);
        assertThat(service.queryRedirectPoolOptions("ABS.SH", 101L))
                .extracting(InvestmentPoolBo::getId).containsExactlyInAnyOrder(4L, 5L, 6L);
        verify(gradeMapper).queryAllowedPoolIdsByGradeAndBucket("3", "LE_1");
    }

    /** 强担保豁免重点观察限制时也必须使用实际所选担保人的最新评分 */
    @Test
    public void strongGuaranteeShouldUseLatestRatingForRestrictedExemption() {
        master.setAbsFlag(0);
        master.setGuarantFlag(1);
        when(mapper.querySecurityInRestrictedPool("ABS.SH")).thenReturn(true);
        assertThat(service.queryRedirectPoolOptions("ABS.SH", 101L)).hasSize(5);
        when(mapper.queryLatestRatingCompanyByCode("SELF_SELECTED")).thenReturn(rating("2"));
        assertThat(service.queryRedirectPoolOptions("ABS.SH", 101L)).isEmpty();
    }

    /** 普通债以及私募、永续、次级仍按发行主体评分，均不增加主体快照依赖 */
    @Test
    public void issuerBasedSpecialBondsShouldKeepTheirRules() {
        master.setAbsFlag(0);
        master.setGuarantFlag(0);
        master.setInnerIssuerRating("1");
        assertThat(service.queryRedirectPoolOptions("ABS.SH", 101L))
                .extracting(InvestmentPoolBo::getId).containsExactly(2L);
        master.setIssueType("私募");
        assertThat(service.queryRedirectPoolOptions("ABS.SH", 101L))
                .extracting(InvestmentPoolBo::getId).containsExactly(2L);
        master.setIssueType(null);
        master.setYxFlag(1);
        assertThat(service.queryRedirectPoolOptions("ABS.SH", 101L))
                .extracting(InvestmentPoolBo::getId).containsExactly(3L);
        master.setYxFlag(0);
        master.setCjFlag(1);
        assertThat(service.queryRedirectPoolOptions("ABS.SH", 101L))
                .extracting(InvestmentPoolBo::getId).containsExactly(2L);
        verify(mapper, never()).querySecuritySnapshotDetailByAdjustLogId(anyLong(), anyString());
    }

    /** 查询与最终校验之间真实评级变化时，应重新计算并拒绝原候选池 */
    @Test
    public void validationShouldRequeryLatestRating() {
        when(mapper.queryLatestRatingCompanyByCode("SELF_SELECTED")).thenReturn(rating("1"), rating("2"));
        assertThat(service.queryRedirectPoolOptions("ABS.SH", 101L)).hasSize(1);
        assertThatThrownBy(() -> service.validateRedirectPool("ABS.SH", 101L, 2L))
                .isInstanceOf(BizException.class).hasMessageContaining("改判池不在");
        service.validateRedirectPool("ABS.SH", 101L, 4L);
    }

    /** 缺少申请或传入其他证券申请时必须拒绝，不按主档猜选 */
    @Test
    public void shouldRejectMissingOrMismatchedApplication() {
        assertThatThrownBy(() -> service.queryRedirectPoolOptions("ABS.SH", null))
                .isInstanceOf(BizException.class).hasMessageContaining("调库记录 ID");
        assertThatThrownBy(() -> service.queryRedirectPoolOptions("ABS.SH", 999L))
                .isInstanceOf(BizException.class).hasMessageContaining("不匹配");
        assertThatThrownBy(() -> service.queryRedirectPoolOptions("OTHER.SH", 101L))
                .isInstanceOf(BizException.class).hasMessageContaining("不匹配");
    }

    /** 非信用债调入及已结束申请均不能调用改判入口 */
    @Test
    public void shouldRejectWrongDirectionPoolTypeOrFinishedApplication() {
        log.setAdjustMode("调出");
        assertThatThrownBy(() -> service.queryRedirectPoolOptions("ABS.SH", 101L))
                .isInstanceOf(BizException.class).hasMessageContaining("仅信用债");
        log.setAdjustMode("调入");
        log.setPoolType("observe");
        assertThatThrownBy(() -> service.queryRedirectPoolOptions("ABS.SH", 101L))
                .isInstanceOf(BizException.class).hasMessageContaining("仅信用债");
        log.setPoolType("credit_bond");
        log.setAuditStatus("20");
        assertThatThrownBy(() -> service.queryRedirectPoolOptions("ABS.SH", 101L))
                .isInstanceOf(BizException.class).hasMessageContaining("已结束");
        log.setAuditStatus("11");
        assertThat(service.queryRedirectPoolOptions("ABS.SH", 101L)).hasSize(1);
    }

    /** 历史申请缺少精确快照时不能读取其他批次的快照 */
    @Test
    public void shouldRejectMissingSnapshot() {
        when(mapper.querySecuritySnapshotDetailByAdjustLogId(101L, "ABS.SH")).thenReturn(null);
        assertThatThrownBy(() -> service.queryRedirectPoolOptions("ABS.SH", 101L))
                .isInstanceOf(BizException.class).hasMessageContaining("申请[101]")
                .hasMessageContaining("缺少入库证券快照");
    }

    /** 快照缺少主体或残留多个主体时必须明确拒绝 */
    @Test
    public void shouldRejectMissingOrMultipleSnapshotCompanies() {
        snapshot.setGuarantorId(" ");
        assertThatThrownBy(() -> service.queryRedirectPoolOptions("ABS.SH", 101L))
                .isInstanceOf(BizException.class).hasMessageContaining("缺少实际评级主体代码");
        snapshot.setGuarantorId("A,B");
        assertThatThrownBy(() -> service.queryRedirectPoolOptions("ABS.SH", 101L))
                .isInstanceOf(BizException.class).hasMessageContaining("不是单一代码");
    }

    /** ABS 无最新评分时明确指出主体，不能沿用快照或主档旧评分 */
    @Test
    public void absShouldRejectMissingLatestRating() {
        for (RelatedRatingCompanyDto rating : Arrays.asList(null, rating(" "))) {
            when(mapper.queryLatestRatingCompanyByCode("SELF_SELECTED")).thenReturn(rating);
            assertThatThrownBy(() -> service.queryRedirectPoolOptions("ABS.SH", 101L))
                    .isInstanceOf(BizException.class).hasMessageContaining("无最新内评分")
                    .hasMessageContaining("SELF_SELECTED");
        }
        verify(gradeMapper, never()).queryAllowedPoolIdsByGradeAndBucket(anyString(), anyString());
    }

    /** 最终改判逐条使用关联市场申请的快照，且不改配套调入及调出项 */
    @Test
    public void finalApprovalShouldValidateEachInboundApplicationAndKeepPairedItems() {
        IpAdjustLogBo out = log(100L, "ABS.SH", "credit_bond", "调出", 3L);
        IpAdjustLogBo linked = log(102L, "ABS.SH", "observe", "调入", 15L);
        IpAdjustLogBo related = log(103L, "ABS.IB", "credit_bond", "调入", 2L);
        when(mapper.queryAdjustLogListForAudit(103L, null)).thenReturn(Collections.singletonList(related));
        SecurityInfoBo relatedMaster = new SecurityInfoBo();
        BeanUtils.copyProperties(master, relatedMaster);
        relatedMaster.setWindCode("ABS.IB");
        when(mapper.querySecurityBoByCode("ABS.IB")).thenReturn(relatedMaster);
        when(mapper.querySecuritySnapshotDetailByAdjustLogId(103L, "ABS.IB"))
                .thenReturn(snapshot("RELATED_SELECTED"));
        when(mapper.queryLatestRatingCompanyByCode("RELATED_SELECTED")).thenReturn(rating("1"));
        List<IpAdjustLogBo> logs = Arrays.asList(out, log, linked, related);
        SecurityPoolAdjustFlowService flow = flow(logs);
        when(mapper.editAdjustLogTargetPool(101L, 2L, "1级库")).thenReturn(1);
        when(mapper.editAdjustLogTargetPool(103L, 2L, "1级库")).thenReturn(1);

        ReflectionTestUtils.invokeMethod(flow, "finishAdjustBatch", step(), 2L);

        verify(mapper).querySecuritySnapshotDetailByAdjustLogId(101L, "ABS.SH");
        verify(mapper).querySecuritySnapshotDetailByAdjustLogId(103L, "ABS.IB");
        verify(mapper).editAdjustLogTargetPool(101L, 2L, "1级库");
        verify(mapper).editAdjustLogTargetPool(103L, 2L, "1级库");
        verify(mapper, never()).editAdjustLogTargetPool(eq(100L), anyLong(), anyString());
        verify(mapper, never()).editAdjustLogTargetPool(eq(102L), anyLong(), anyString());
        assertThat(out.getTargetPoolId()).isEqualTo(3L);
        assertThat(linked.getTargetPoolId()).isEqualTo(15L);
        verify(service).applyPoolStatusChanges(logs);
    }

    /** 最终审批重新读取最新评分，评级变化后失败时不写目标池、审核状态或池状态 */
    @Test
    public void finalApprovalShouldRejectChangedRatingBeforeAnyPoolWrites() {
        assertThat(service.queryRedirectPoolOptions("ABS.SH", 101L)).hasSize(1);
        when(mapper.queryLatestRatingCompanyByCode("SELF_SELECTED")).thenReturn(rating("2"));
        SecurityPoolAdjustFlowService flow = flow(Collections.singletonList(log));
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(flow, "finishAdjustBatch", step(), 2L))
                .isInstanceOf(BizException.class).hasMessageContaining("申请[101]");
        verify(mapper, never()).editAdjustLogTargetPool(anyLong(), anyLong(), anyString());
        verify(mapper, never()).editActiveAdjustLogAuditStatus(anyLong(), anyString(), anyString());
        verify(service, never()).applyPoolStatusChanges(any());
    }

    /** 更新时申请已不再活动则中止，不继续更新审核状态或落池 */
    @Test
    public void finalApprovalShouldRejectLostActiveLog() {
        SecurityPoolAdjustFlowService flow = flow(Collections.singletonList(log));
        when(mapper.editAdjustLogTargetPool(101L, 2L, "1级库")).thenReturn(0);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(flow, "finishAdjustBatch", step(), 2L))
                .isInstanceOf(BizException.class).hasMessageContaining("状态已发生变化");
        verify(mapper, never()).editActiveAdjustLogAuditStatus(anyLong(), anyString(), anyString());
        verify(service, never()).applyPoolStatusChanges(any());
    }

    /** 构造最终审批服务，保留实际改判校验，仅隔离无关池状态及报告落地 */
    private SecurityPoolAdjustFlowService flow(List<IpAdjustLogBo> logs) {
        SecurityPoolAdjustFlowService flow = new SecurityPoolAdjustFlowService();
        ReflectionTestUtils.setField(flow, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(flow, "securityPoolAdjustService", service);
        ReflectionTestUtils.setField(flow, "reportService", mock(ReportService.class));
        when(mapper.queryAdjustLogListForAudit(101L, "BATCH")).thenReturn(logs);
        when(mapper.editActiveAdjustLogAuditStatus(101L, "BATCH", "20")).thenReturn(logs.size());
        doNothing().when(service).recheckBeforeFinalApproval(any());
        doNothing().when(service).applyPoolStatusChanges(any());
        return flow;
    }

    /** 构造实际审批步骤归属 */
    private IpAdjustStepBo step() {
        IpAdjustStepBo step = new IpAdjustStepBo();
        step.setAdjustLogId(101L);
        step.setAdjustBatchNo("BATCH");
        return step;
    }

    /** 构造调库申请 */
    private IpAdjustLogBo log(Long id, String code, String poolType, String mode, Long poolId) {
        IpAdjustLogBo item = new IpAdjustLogBo();
        item.setId(id);
        item.setSecurityCode(code);
        item.setPoolType(poolType);
        item.setAdjustMode(mode);
        item.setTargetPoolId(poolId);
        item.setAdjustBatchNo("BATCH");
        item.setAuditStatus("00");
        return item;
    }

    /** 构造单一主体快照 */
    private SecurityInfoDetailDto snapshot(String code) {
        SecurityInfoDetailDto item = new SecurityInfoDetailDto();
        item.setGuarantorId(code);
        return item;
    }

    /** 构造最新主体评分 */
    private RelatedRatingCompanyDto rating(String grade) {
        RelatedRatingCompanyDto item = new RelatedRatingCompanyDto();
        item.setInnerRating(grade);
        return item;
    }
}
