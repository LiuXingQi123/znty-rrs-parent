package com.znty.rrs.service;

import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpGradeRuleAlertBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.securitypooladjust.RelatedRatingCompanyDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.SecurityInfoDetailDto;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.AbsIdentityMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** ABS 身份占用、五字段审批保存及主体联动决策测试 */
public class AbsIdentityServiceTest {
    private AbsIdentityService service;
    private AbsCompanySyncService syncService;
    private AbsIdentityMapper mapper;
    private InvestmentPoolMapper poolMapper;
    private SecurityPoolAdjustMapper securityMapper;
    private SecurityInfoBo master;

    @Before
    public void prepare() {
        service = new AbsIdentityService();
        mapper = mock(AbsIdentityMapper.class);
        poolMapper = mock(InvestmentPoolMapper.class);
        securityMapper = mock(SecurityPoolAdjustMapper.class);
        ReflectionTestUtils.setField(service, "absIdentityMapper", mapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityMapper);
        // 候选缓存与联动决策已拆为独立组件，测试按真实协作关系组装
        AbsRatingCompanyCache cache = new AbsRatingCompanyCache();
        ReflectionTestUtils.setField(cache, "securityPoolAdjustMapper", securityMapper);
        ReflectionTestUtils.setField(service, "absRatingCompanyCache", cache);
        syncService = new AbsCompanySyncService();
        ReflectionTestUtils.setField(syncService, "absIdentityMapper", mapper);
        ReflectionTestUtils.setField(syncService, "securityPoolAdjustMapper", securityMapper);
        ReflectionTestUtils.setField(syncService, "investmentPoolMapper", poolMapper);
        ReflectionTestUtils.setField(syncService, "absIdentityService", service);
        master = new SecurityInfoBo();
        master.setWindCode("ABS1");
        master.setSecurityType("abs");
        master.setIssuerCode("A");
        master.setIssuer("主体A");
        when(mapper.queryAbsSecurityList(anyList())).thenReturn(Collections.singletonList(master));
        when(securityMapper.querySecurityBoByCode("ABS1")).thenReturn(master);
        when(mapper.queryCompanyPoolContext("ABS1", 101L)).thenReturn(Arrays.asList("A:out:10", "B:in:20"));
        when(mapper.editApprovedIdentity(any())).thenReturn(1);
        when(securityMapper.addAdjustLog(any(IpAdjustLogBo.class))).thenReturn(1);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(101L);
        pool.setPoolName("任意业务池");
        pool.setPoolType("credit_bond");
        when(poolMapper.queryPoolById(101L)).thenReturn(pool);
    }

    /** 查询候选后复用完整数据，且不受调用方修改原对象影响 */
    @Test
    public void candidateShouldRetainAllFiveFields() {
        RelatedRatingCompanyDto row = new RelatedRatingCompanyDto();
        row.setCompanyCode("A");
        row.setCompanyName("主体A");
        row.setInnerRating("2");
        row.setRatingBondissuer("AA+");
        row.setRatingBondissuerAgency("机构代码");
        service.rememberRelated("ABS1", Collections.singletonList(row));
        row.setCompanyName("浏览器中的另一个值");
        RelatedRatingCompanyDto stored = service.findRelated("ABS1").get(0);
        assertThat(stored.getCompanyName()).isEqualTo("主体A");
        assertThat(stored.getRatingBondissuer()).isEqualTo("AA+");
        assertThat(stored.getRatingBondissuerAgency()).isEqualTo("机构代码");
        verifyNoInteractions(mapper);
    }

    /** 候选从写入时起缓存一分钟 */
    @Test
    public void relatedCandidateShouldExpireOneMinuteAfterCaching() {
        RelatedRatingCompanyDto row = new RelatedRatingCompanyDto();
        row.setCompanyCode("A");
        long before = System.currentTimeMillis();
        service.rememberRelated("ABS1", Collections.singletonList(row));
        long after = System.currentTimeMillis();

        Object cache = ReflectionTestUtils.getField(service, "absRatingCompanyCache");
        Map<?, ?> entries = (Map<?, ?>) ReflectionTestUtils.getField(cache, "relatedCandidates");
        Long expiresAt = (Long) ReflectionTestUtils.getField(entries.get("ABS1"), "expiresAt");
        assertThat(expiresAt).isBetween(before + 60_000L, after + 60_000L);
    }

    /** 在途或在库已经占用 A 时，其他库不能选择 B */
    @Test
    public void occupiedIdentityShouldRejectDifferentSubject() {
        when(mapper.queryOccupiedIdentityCodes("ABS1")).thenReturn(Collections.singletonList("A"));
        assertThatThrownBy(() -> service.validateIdentity("ABS1", "B"))
                .isInstanceOf(BizException.class).hasMessageContaining("主体[A]").hasMessageContaining("主体[B]");
    }

    /** 已确认身份只校验确认主体，其他权益人不再参与禁止类池判断 */
    @Test
    public void confirmedIdentityShouldOnlyUseConfirmedCompany() {
        when(mapper.queryConfirmedIdentityCode("ABS1")).thenReturn("A");
        assertThat(service.queryRiskCompanyCodes(master)).containsExactly("A");
        // 只读主档，不重复查询四类候选
        verify(securityMapper, never()).queryRelatedRatingCompanyList(anyString());
    }

    /** 未确认身份时汇总四类候选和本次已选主体，任一命中都要拦截 */
    @Test
    public void unconfirmedIdentityShouldUseAllRelatedCompanies() {
        when(mapper.queryConfirmedIdentityCode("ABS1")).thenReturn(null);
        RelatedRatingCompanyDto first = new RelatedRatingCompanyDto();
        first.setCompanyCode("A");
        RelatedRatingCompanyDto second = new RelatedRatingCompanyDto();
        second.setCompanyCode("B");
        when(securityMapper.queryRelatedRatingCompanyList("ABS1")).thenReturn(Arrays.asList(first, second));
        SecurityInfoBo selected = new SecurityInfoBo();
        selected.setWindCode("ABS1");
        selected.setIssuerCode("C");
        assertThat(service.queryRiskCompanyCodes(selected)).containsExactly("A", "B", "C");
    }

    /** 调用方已加载确认身份时不再查询主档 */
    @Test
    public void confirmedCodeParameterShouldReuseLoadedIdentity() {
        assertThat(service.queryRiskCompanyCodes(master, "A")).containsExactly("A");
        verify(mapper, never()).queryConfirmedIdentityCode(anyString());
        verify(securityMapper, never()).queryRelatedRatingCompanyList(anyString());
    }

    /** 选择和只读查询不提前报错，冲突留到下一步调整说明中 */
    @Test
    public void selectionShouldFillFieldsBeforeReportingOccupiedIdentity() {
        when(mapper.queryOccupiedIdentityCodes("ABS1")).thenReturn(Collections.singletonList("A"));
        service.applySelection(master, "B", "主体B", "2", "AA+", "R2");
        assertThat(master.getIssuerCode()).isEqualTo("B");
        verify(mapper, never()).queryOccupiedIdentityCodes(anyString());
        assertThat(service.queryIdentityFailure("ABS1", "B")).contains("主体[A]").contains("主体[B]");
        assertThatThrownBy(() -> service.validateIdentity("ABS1", "B")).isInstanceOf(BizException.class);
    }

    /** 关联市场冲突阻断同组，保留旧原因且不影响其他独立组 */
    @Test
    public void identityFailureShouldBeShownForWholeGroupWithoutAbortingOtherGroups() {
        when(mapper.queryOccupiedIdentityCodes("RELATED.SH")).thenReturn(Collections.singletonList("B"));
        AdjustCheckDto.CheckResultItem manual = checkItem("ABS1", "G1");
        manual.setFailReasons(Collections.singletonList("原业务原因"));
        AdjustCheckDto.CheckResultItem related = checkItem("RELATED.SH", "G1");
        AdjustCheckDto.CheckResultItem other = checkItem("ABS1", "G2");
        service.applyIdentityCheckFailures(master, Arrays.asList(manual, related, other));
        assertThat(manual.isCanAdjust()).isFalse();
        assertThat(related.isCanAdjust()).isFalse();
        assertThat(manual.getFailReasons()).anyMatch(reason -> reason.contains("RELATED.SH") && reason.contains("主体[B]"));
        assertThat(manual.getFailReasons()).contains("原业务原因");
        assertThat(other.isCanAdjust()).isTrue();
        assertThat(manual.getMaterialExemptionType()).isNull();
        assertThat(manual.getFlowOptions()).isEmpty();
    }

    /** 同批多种主体以逐券原因返回，普通债不受该规则影响 */
    @Test
    public void mixedBatchIdentityShouldReturnFailureInsteadOfThrowing() {
        Map<String, Set<String>> choices = new HashMap<>();
        choices.put("ABS1", new LinkedHashSet<>(Arrays.asList("A", "B")));
        choices.put("NORMAL", new LinkedHashSet<>(Arrays.asList("A", "B")));
        Map<String, String> failures = service.queryBatchIdentityFailures(choices);
        assertThat(failures).containsOnlyKeys("ABS1");
        assertThat(failures.get("ABS1")).contains("同批申请中选择了不同主体");
        AdjustCheckDto.CheckResultItem item = checkItem("ABS1", "G1");
        service.applyCheckFailure(item, failures.get("ABS1"));
        assertThat(item.isCanAdjust()).isFalse();
        assertThat(item.getFailReasons()).contains(failures.get("ABS1"));
    }

    private AdjustCheckDto.CheckResultItem checkItem(String code, String group) {
        AdjustCheckDto.CheckResultItem item = new AdjustCheckDto.CheckResultItem();
        item.setSecurityCode(code);
        item.setAdjustGroupKey(group);
        item.setCanAdjust(true);
        item.setMaterialExemptionType("creditBondSimple");
        return item;
    }

    /** 成功入库留下的 A 永久固定，其他主体在调整说明中展示不可调整原因 */
    @Test
    public void historicalIdentityShouldRemainBoundAfterAllPoolsAndFlowsEnd() {
        when(mapper.queryOccupiedIdentityCodes("ABS1")).thenReturn(Collections.singletonList("A"));
        service.validateIdentity("ABS1", "A");
        assertThat(service.queryIdentityFailure("ABS1", "B")).contains("主体[A]", "不能更换主体");
        assertThatThrownBy(() -> service.validateIdentity("ABS1", "B")).isInstanceOf(BizException.class);
        verify(mapper, never()).editApprovedIdentity(any());
    }

    /** 自动联动入池暂未确认主体时，不把空身份当成冲突 */
    @Test
    public void passiveInboundWithoutIdentityShouldAllowSelection() {
        when(mapper.queryOccupiedIdentityCodes("ABS1")).thenReturn(Collections.singletonList(null));
        service.validateIdentity("ABS1", "A");
        assertThat(service.queryIdentityFailure("ABS1", "A")).isNull();
    }

    /** 审核通过只保存本笔快照的主体五字段 */
    @Test
    public void approvedInboundShouldPersistExactSnapshotIdentity() {
        SecurityInfoDetailDto snapshot = snapshot("A");
        when(securityMapper.querySecuritySnapshotDetailByAdjustLogId(10L, "ABS1")).thenReturn(snapshot);
        service.approveIdentities(Collections.singletonList(log(10L, "调入")));
        ArgumentCaptor<SecurityInfoBo> value = ArgumentCaptor.forClass(SecurityInfoBo.class);
        verify(mapper).editApprovedIdentity(value.capture());
        assertThat(value.getValue().getIssuer()).isEqualTo("主体A");
        assertThat(value.getValue().getIssuerCode()).isEqualTo("A");
        assertThat(value.getValue().getInnerIssuerRating()).isEqualTo("2");
        assertThat(value.getValue().getRatingBondissuer()).isEqualTo("AA+");
        assertThat(value.getValue().getRatingBondissuerAgency()).isEqualTo("机构A");
    }

    /** 预校验或调出审批都不回写五字段 */
    @Test
    public void precheckAndOutboundShouldNotRewriteMasterIdentity() {
        when(securityMapper.querySecuritySnapshotDetailByAdjustLogId(10L, "ABS1")).thenReturn(snapshot("A"));
        service.validateApprovalIdentities(Collections.singletonList(log(10L, "调入")));
        service.approveIdentities(Collections.singletonList(log(11L, "调出")));
        verify(mapper, never()).editApprovedIdentity(any());
    }

    /** 同批不同目标池选择不同主体时整批失败 */
    @Test
    public void mixedIdentitiesInSameBatchShouldFailBeforeWritingMaster() {
        when(securityMapper.querySecuritySnapshotDetailByAdjustLogId(10L, "ABS1")).thenReturn(snapshot("A"));
        when(securityMapper.querySecuritySnapshotDetailByAdjustLogId(11L, "ABS1")).thenReturn(snapshot("B"));
        assertThatThrownBy(() -> service.approveIdentities(Arrays.asList(log(10L, "调入"), log(11L, "调入"))))
                .isInstanceOf(BizException.class).hasMessageContaining("同批申请中选择了不同主体");
        verify(mapper, never()).editApprovedIdentity(any());
    }

    /** 已确认身份后，其他权益人进出不再影响该 ABS，也不切换主体 */
    @Test
    public void anotherCompanyShouldNotAffectConfirmedIdentity() {
        assertThat(syncService.allowCompanySync(master, "B", 101L, "任意业务池", true)).isFalse();
        assertThat(master.getIssuerCode()).isEqualTo("A");
        verify(mapper, never()).addSyncAlert(any());
        verify(mapper, never()).editApprovedIdentity(any());
    }

    /** 主档尚未确认主体时，人工联动和定时任务写同样的提醒和待确认日志，都不提前落池 */
    @Test
    public void manualAndScheduledInboundShouldUseSamePendingRule() {
        SecurityInfoBo unconfirmed = new SecurityInfoBo();
        unconfirmed.setWindCode("ABS1");
        unconfirmed.setSecurityType("abs");
        unconfirmed.setIssuerCode(null);
        unconfirmed.setIssuer(null);
        when(securityMapper.querySecurityBoByCode("ABS1")).thenReturn(unconfirmed);
        assertThat(syncService.allowCompanySync(unconfirmed, "B", 101L, "任意业务池", true)).isFalse();

        ScheduledAdjustCandidateDto candidate = new ScheduledAdjustCandidateDto();
        candidate.setSecurityCode("ABS1");
        candidate.setSecurityShortName("测试ABS");
        candidate.setSecurityType("abs");
        candidate.setIssuerCode(null);
        candidate.setIssuerName(null);
        candidate.setAbsFlag(1);
        assertThat(syncService.allowScheduledSync(candidate, "B", 101L, "任意业务池", true)).isFalse();
        // 人工落地和定时扫描都当场生成同一条主体联动提醒，研究员在规则提醒里能立刻看到
        ArgumentCaptor<IpGradeRuleAlertBo> alertCaptor = ArgumentCaptor.forClass(IpGradeRuleAlertBo.class);
        verify(mapper, times(2)).addSyncAlert(alertCaptor.capture());
        for (IpGradeRuleAlertBo alert : alertCaptor.getAllValues()) {
            assertThat(alert.getSecurityCode()).isEqualTo("ABS1");
            assertThat(alert.getCurrentPoolId()).isEqualTo(101L);
            assertThat(alert.getFailReason())
                    .isEqualTo("主体[B]入池触发 ABS 联动，ABS 主体身份尚未确认，请研究员选择权益人");
        }
        ArgumentCaptor<IpAdjustLogBo> captor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        // 人工联动和定时扫描都只生成待确认日志，不提前落池
        verify(securityMapper, times(2)).addAdjustLog(captor.capture());
        IpAdjustLogBo pending = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(pending.getSecurityCode()).isEqualTo("ABS1");
        assertThat(pending.getSecurityShortName()).isEqualTo("测试ABS");
        assertThat(pending.getSecurityType()).isEqualTo("abs");
        assertThat(pending.getAdjustType()).isEqualTo("自动调整");
        assertThat(pending.getAdjustMode()).isEqualTo("调入");
        assertThat(pending.getTargetPoolId()).isEqualTo(101L);
        assertThat(pending.getTargetPoolName()).isEqualTo("任意业务池");
        assertThat(pending.getPoolType()).isEqualTo("credit_bond");
        assertThat(pending.getAuditStatus()).isEqualTo("00");
        assertThat(pending.getFlowType()).isEqualTo(AbsCompanySyncService.ABS_PENDING_FLOW_TYPE);
        assertThat(pending.getAdjusterId()).isEqualTo("0");
        assertThat(pending.getAdjusterName()).isEqualTo("系统");
        assertThat(pending.getAdjustBatchNo()).startsWith("BOND");
        assertThat(pending.getAdjustReason()).contains("触发主体：B");
        assertThat(pending.getSubmitTime()).isNotNull();
        verify(mapper, never()).editApprovedIdentity(any());
        // 待确认期间不能写入池状态
        verify(securityMapper, never()).addPoolStatus(any());
    }

    /** 主体退出时，未确认主体的 ABS 只生成提醒，不自动出库 */
    @Test
    public void unconfirmedAbsShouldWarnOnOutbound() {
        SecurityInfoBo unconfirmed = new SecurityInfoBo();
        unconfirmed.setWindCode("ABS1");
        unconfirmed.setSecurityType("abs");
        unconfirmed.setIssuerCode(null);
        unconfirmed.setIssuer(null);
        when(securityMapper.querySecurityBoByCode("ABS1")).thenReturn(unconfirmed);
        when(mapper.queryOtherCompanyInPool("ABS1", "B", 101L)).thenReturn(Collections.singletonList("C"));
        assertThat(syncService.allowCompanySync(unconfirmed, "B", 101L, "任意业务池", false)).isFalse();
        ArgumentCaptor<IpGradeRuleAlertBo> alert = ArgumentCaptor.forClass(IpGradeRuleAlertBo.class);
        verify(mapper).addSyncAlert(alert.capture());
        assertThat(alert.getValue().getFailReason()).contains("C");
    }

    /** 自动联动不能使用历史 A 绕过最新在途 B 的身份占用 */
    @Test
    public void automaticInboundShouldRespectNewPendingIdentityOnCheck() {
        when(mapper.queryOccupiedIdentityCodes("ABS1")).thenReturn(Collections.singletonList("B"));
        assertThat(syncService.allowCompanySync(master, "A", 101L, "任意业务池", true)).isFalse();
        ArgumentCaptor<IpGradeRuleAlertBo> alert = ArgumentCaptor.forClass(IpGradeRuleAlertBo.class);
        verify(mapper).addSyncAlert(alert.capture());
        assertThat(alert.getValue().getFailReason()).contains("主体[B]");
    }

    /** 已确认身份后，其他权益人仍在池不构成保留依据，直接自动出库 */
    @Test
    public void otherCompanyInPoolShouldNotBlockConfirmedOutbound() {
        when(mapper.queryOtherCompanyInPool("ABS1", "A", 101L)).thenReturn(Collections.singletonList("B"));
        assertThat(syncService.allowCompanySync(master, "A", 101L, "任意业务池", false)).isTrue();
        verify(mapper, never()).addSyncAlert(any());
    }

    /** 未确认身份时跨池映射检查来源主体池，提醒仍指向实际债券池 */
    @Test
    public void crossPoolMappingShouldCheckSourceCompanyPool() {
        SecurityInfoBo unconfirmed = new SecurityInfoBo();
        unconfirmed.setWindCode("ABS1");
        unconfirmed.setSecurityType("abs");
        when(securityMapper.querySecurityBoByCode("ABS1")).thenReturn(unconfirmed);
        when(mapper.queryOtherCompanyInPool("ABS1", "A", 202L)).thenReturn(Collections.singletonList("B"));
        assertThat(syncService.allowCompanySync(unconfirmed, "A", 101L, "债券池", false, 202L)).isFalse();
        ArgumentCaptor<IpGradeRuleAlertBo> alert = ArgumentCaptor.forClass(IpGradeRuleAlertBo.class);
        verify(mapper).addSyncAlert(alert.capture());
        assertThat(alert.getValue().getCurrentPoolId()).isEqualTo(101L);
        assertThat(alert.getValue().getFailReason()).contains("B");
    }

    /** 债级独立申请不能被主体退出自动解除 */
    @Test
    public void independentInboundShouldRequireResearcherDecision() {
        when(mapper.queryIndependentInbound("ABS1", 101L)).thenReturn(true);
        assertThat(syncService.allowCompanySync(master, "A", 101L, "任意业务池", false)).isFalse();
        verify(mapper).addSyncAlert(any());
    }

    /** 唯一主体依据消失且没有独立来源时允许自动出池 */
    @Test
    public void unambiguousOutboundShouldContinue() {
        assertThat(syncService.allowCompanySync(master, "A", 101L, "任意业务池", false)).isTrue();
        verify(mapper, never()).addSyncAlert(any());
    }

    /** 相同依据已由研究员处理后，任务不能立即反向调库 */
    @Test
    public void processedContextShouldPreventAutomaticReentry() {
        SecurityInfoBo unconfirmed = new SecurityInfoBo();
        unconfirmed.setWindCode("ABS1");
        unconfirmed.setSecurityType("abs");
        when(securityMapper.querySecurityBoByCode("ABS1")).thenReturn(unconfirmed);
        when(mapper.queryOtherCompanyInPool("ABS1", "A", 101L)).thenReturn(Collections.singletonList("B"));
        syncService.allowCompanySync(unconfirmed, "A", 101L, "任意业务池", false);
        ArgumentCaptor<IpGradeRuleAlertBo> captured = ArgumentCaptor.forClass(IpGradeRuleAlertBo.class);
        verify(mapper).addSyncAlert(captured.capture());
        IpGradeRuleAlertBo previous = captured.getValue();
        previous.setAlertStatus("20");
        when(mapper.queryLatestSyncAlert("ABS1", 101L)).thenReturn(previous);
        assertThat(syncService.allowCompanySync(unconfirmed, "B", 101L, "任意业务池", true)).isFalse();
        verify(mapper, times(1)).addSyncAlert(any());
        // 依据版本变化后不复用旧的人工结论，重新生成待处理提醒
        when(mapper.queryCompanyPoolContext("ABS1", 101L)).thenReturn(Arrays.asList("A:out:10", "B:in:21"));
        assertThat(syncService.allowCompanySync(unconfirmed, "B", 101L, "任意业务池", true)).isFalse();
        verify(mapper, times(2)).addSyncAlert(any());
    }

    /** 申请快照与日志的实际代码必须一致 */
    @Test
    public void missingApprovalSnapshotShouldFail() {
        assertThatThrownBy(() -> service.approveIdentities(Collections.singletonList(log(10L, "调入"))))
                .isInstanceOf(BizException.class).hasMessageContaining("缺少主体快照");
    }

    /** 普通和自选角色使用同一代码时身份相同，不同代码必须在分组前拒绝 */
    @Test
    public void batchGroupingShouldCompareFinalCompanyCode() {
        Map<String, String> choices = new HashMap<>();
        service.validateBatchSelection("ABS1", "A", null, choices);
        service.validateBatchSelection("ABS1", "B", "A", choices);
        assertThatThrownBy(() -> service.validateBatchSelection("ABS1", "B", null, choices))
                .isInstanceOf(BizException.class).hasMessageContaining("同批申请中选择了不同主体");
    }

    /** ABS 的黑名单硬限制不能因当前身份 A 未命中就忽略关联 B */
    @Test
    public void blacklistShouldAggregateAllRelatedSubjectsAndKeepHardOutboundRule() {
        PledgeBlacklistRuleService rules = spy(new PledgeBlacklistRuleService());
        // 按批次预期池状态的入口实际调用三参判定，需与该签名保持一致
        doReturn(new PledgeBlacklistRuleService.Decision(false, false, false))
                .when(rules).evaluate(eq("A"), anySet(), anySet());
        doReturn(new PledgeBlacklistRuleService.Decision(true, false, false))
                .when(rules).evaluate(eq("B"), anySet(), anySet());
        assertThat(rules.validateCompanies(Arrays.asList("A", "B"), "调入")).isNull();
        assertThat(rules.validateCompanies(Arrays.asList("A", "B"), "调出")).contains("主体[B]").contains("不能调出17");
    }

    private IpAdjustLogBo log(Long id, String mode) {
        IpAdjustLogBo log = new IpAdjustLogBo();
        log.setId(id);
        log.setSecurityCode("ABS1");
        log.setSecurityType("abs");
        log.setAdjustMode(mode);
        log.setTargetPoolId(101L);
        return log;
    }

    private SecurityInfoDetailDto snapshot(String code) {
        SecurityInfoDetailDto value = new SecurityInfoDetailDto();
        value.setWindCode("ABS1");
        value.setIssuerCode(code);
        value.setIssuer("主体" + code);
        value.setInnerIssuerRating("2");
        value.setRatingBondissuer("AA+");
        value.setRatingBondissuerAgency("机构" + code);
        return value;
    }
}
