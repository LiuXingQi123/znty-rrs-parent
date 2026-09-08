package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.entity.bo.CompanyBondTypeScopeBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 主体下债券自动入库（同池 / IP_RULE）单元测试
 */
public class CompanySamePoolBondAutoInServiceTest {

    /** 验证主体在同池时自动调入债券并处理互斥池。 */
    @Test
    public void execute_ShouldAutoInBondSamePool() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        CompanySamePoolBondAutoInService service = new CompanySamePoolBondAutoInService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("主体下债券自动入库");
        conf.setParamJson("{\"poolIds\":[15]}");
        when(scheduledTaskMapper.queryTaskByCode(CompanySamePoolBondAutoInService.TASK_CODE)).thenReturn(conf);

        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(15L);
        pool.setPoolName("债券禁止库");
        pool.setPoolType("forbidden");
        InvestmentPoolBo relatedPool = new InvestmentPoolBo();
        relatedPool.setId(3L);
        relatedPool.setPoolName("二级库");
        relatedPool.setPoolType("credit_bond");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Arrays.asList(pool, relatedPool));
        // 构建目标池的互斥及反向限制关系
        when(securityPoolAdjustMapper.queryAllPoolRelationList()).thenReturn(Arrays.asList(
                buildRelation(15L, "in_mutex", 3L),
                buildRelation(3L, "in_restrict", 15L)));

        ScheduledAdjustCandidateDto bond = new ScheduledAdjustCandidateDto();
        bond.setSecurityCode("112008001.IB");
        bond.setSecurityShortName("测试债");
        bond.setSecurityType("mtn");
        bond.setIssuerCode("C001");
        bond.setIssuerName("测试集团");
        when(autoAdjustMapper.queryCompanyBondSamePoolForAutoIn(eq(15L),
                any(CompanyBondTypeScopeBo.class)))
                .thenReturn(Collections.singletonList(bond));
        when(securityPoolAdjustMapper.querySecurityCurrentPoolIdList("112008001.IB"))
                .thenReturn(Collections.singletonList(3L));
        when(securityPoolAdjustMapper.addAdjustLog(any(IpAdjustLogBo.class))).thenAnswer(invocation -> {
            IpAdjustLogBo log = (IpAdjustLogBo) invocation.getArguments()[0];
            log.setId(9101L);
            return 1;
        });
        when(securityPoolAdjustMapper.addPoolStatus(any(IpAdjustLogBo.class))).thenReturn(1);
        when(securityPoolAdjustMapper.deletePoolStatusSoft("112008001.IB", 3L)).thenReturn(1);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        assertThat(service.getTaskCode()).isEqualTo("company_same_pool_bond_auto_in");
        verify(autoAdjustMapper).queryCompanyBondSamePoolForAutoIn(
                eq(15L), any(CompanyBondTypeScopeBo.class));

        ArgumentCaptor<IpAdjustLogBo> captor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(securityPoolAdjustMapper, times(2)).addAdjustLog(captor.capture());
        IpAdjustLogBo inboundLog = captor.getAllValues().get(0);
        IpAdjustLogBo outboundLog = captor.getAllValues().get(1);
        assertThat(inboundLog.getSecurityCode()).isEqualTo("112008001.IB");
        assertThat(inboundLog.getAdjustType()).isEqualTo("自动调整");
        assertThat(inboundLog.getAdjustMode()).isEqualTo(AdjustMode.IN.getCode());
        assertThat(inboundLog.getAuditStatus()).isEqualTo(AuditStatus.APPROVED.getCode());
        assertThat(inboundLog.getTargetPoolId()).isEqualTo(15L);
        assertThat(inboundLog.getAdjustReason())
                .isEqualTo("主体下债券自动入库（发行主体：测试集团/C001；主体所在池：债券禁止库）");
        assertThat(inboundLog.getAdjustAdvice()).isEqualTo(inboundLog.getAdjustReason());
        assertThat(outboundLog.getAdjustType()).isEqualTo("互斥调整");
        assertThat(outboundLog.getAdjustMode()).isEqualTo(AdjustMode.OUT.getCode());
        assertThat(outboundLog.getTargetPoolId()).isEqualTo(3L);
        assertThat(outboundLog.getTargetPoolName()).isEqualTo("二级库");
        assertThat(outboundLog.getAdjustBatchNo()).isEqualTo(inboundLog.getAdjustBatchNo());
        assertThat(outboundLog.getAdjustReason()).contains("池关系触发：调入债券禁止库后自动调出二级库");
        assertThat(outboundLog.getAdjustAdvice()).isEqualTo(outboundLog.getAdjustReason());
        verify(securityPoolAdjustMapper).deletePoolStatusSoft("112008001.IB", 3L);
    }

    /** 验证扫描17时只补充发行主体命中三个条件的债券。 */
    @Test
    public void execute_ShouldGatePool17ByIssuerConditions() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        PledgeBlacklistRuleService ruleService = mock(PledgeBlacklistRuleService.class);
        CompanySamePoolBondAutoInService service = new CompanySamePoolBondAutoInService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        ReflectionTestUtils.setField(service, "pledgeBlacklistRuleService", ruleService);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("主体下债券自动入库");
        conf.setParamJson("{\"poolIds\":[17]}");
        when(scheduledTaskMapper.queryTaskByCode(CompanySamePoolBondAutoInService.TASK_CODE)).thenReturn(conf);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(PledgeBlacklistRuleService.BLACKLIST_POOL_ID);
        pool.setPoolName("黑名单质押库");
        pool.setPoolType("blacklist");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool));
        when(securityPoolAdjustMapper.queryAllPoolRelationList()).thenReturn(Collections.<PoolRelationBo>emptyList());

        ScheduledAdjustCandidateDto matchedBond = new ScheduledAdjustCandidateDto();
        matchedBond.setSecurityCode("B001");
        matchedBond.setSecurityType("corporate_bond");
        ScheduledAdjustCandidateDto unmatchedBond = new ScheduledAdjustCandidateDto();
        unmatchedBond.setSecurityCode("B002");
        unmatchedBond.setSecurityType("corporate_bond");
        when(autoAdjustMapper.queryCompanyBondSamePoolForAutoIn(
                eq(PledgeBlacklistRuleService.BLACKLIST_POOL_ID), any(CompanyBondTypeScopeBo.class)))
                .thenReturn(Arrays.asList(matchedBond, unmatchedBond));
        SecurityInfoBo matchedSecurity = new SecurityInfoBo();
        matchedSecurity.setIssuerCode("C001");
        SecurityInfoBo unmatchedSecurity = new SecurityInfoBo();
        unmatchedSecurity.setIssuerCode("C002");
        when(securityPoolAdjustMapper.querySecurityBoByCode("B001")).thenReturn(matchedSecurity);
        when(securityPoolAdjustMapper.querySecurityBoByCode("B002")).thenReturn(unmatchedSecurity);
        when(ruleService.evaluate("C001"))
                .thenReturn(new PledgeBlacklistRuleService.Decision(true, false, false));
        when(ruleService.evaluate("C002"))
                .thenReturn(new PledgeBlacklistRuleService.Decision(false, false, false));
        when(securityPoolAdjustMapper.querySecurityCurrentPoolIdList("B001"))
                .thenReturn(Collections.<Long>emptyList());
        when(securityPoolAdjustMapper.addAdjustLog(any(IpAdjustLogBo.class))).thenReturn(1);
        when(securityPoolAdjustMapper.addPoolStatus(any(IpAdjustLogBo.class))).thenReturn(1);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        ArgumentCaptor<IpAdjustLogBo> captor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(securityPoolAdjustMapper).addAdjustLog(captor.capture());
        assertThat(captor.getValue().getSecurityCode()).isEqualTo("B001");
        verify(securityPoolAdjustMapper, never()).querySecurityCurrentPoolIdList("B002");
    }

    /** 验证缺少扫描池参数时任务失败。 */
    @Test
    public void execute_ShouldFailWhenParamMissing() {
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        CompanySamePoolBondAutoInService service = new CompanySamePoolBondAutoInService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mock(SecurityPoolAdjustMapper.class));
        ReflectionTestUtils.setField(service, "investmentPoolMapper", mock(InvestmentPoolMapper.class));
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);
        when(scheduledTaskMapper.queryTaskByCode(CompanySamePoolBondAutoInService.TASK_CODE)).thenReturn(null);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("未配置扫描池");
    }

    /** 验证没有待补债券时任务成功且不写数据。 */
    @Test
    public void execute_ShouldSkipWhenNoCandidate() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        CompanySamePoolBondAutoInService service = new CompanySamePoolBondAutoInService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("主体下债券自动入库");
        conf.setParamJson("{\"poolIds\":[15]}");
        when(scheduledTaskMapper.queryTaskByCode(CompanySamePoolBondAutoInService.TASK_CODE)).thenReturn(conf);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(15L);
        pool.setPoolName("债券禁止库");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool));
        when(autoAdjustMapper.queryCompanyBondSamePoolForAutoIn(eq(15L),
                any(CompanyBondTypeScopeBo.class)))
                .thenReturn(Collections.<ScheduledAdjustCandidateDto>emptyList());

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(0);
        verify(securityPoolAdjustMapper, never()).addAdjustLog(any(IpAdjustLogBo.class));
    }

    /** 验证任务参数可解析池 ID 数组。 */
    @Test
    public void parsePoolIds_ShouldAcceptJsonArray() {
        CompanySamePoolBondAutoInService service = new CompanySamePoolBondAutoInService();
        List<Long> ids = service.parsePoolIds("{\"poolIds\":[15,16]}");
        assertThat(ids).containsExactly(15L, 16L);
        assertThatThrownBy(() -> service.parsePoolIds("{}")).isInstanceOf(BizException.class);
    }

    /**
     * 构建测试用投资池关系。
     *
     * @param poolId        来源池 ID
     * @param relationType  关系类型
     * @param relationPoolId 关系池 ID
     * @return 投资池关系
     */
    private PoolRelationBo buildRelation(Long poolId, String relationType, Long relationPoolId) {
        PoolRelationBo relation = new PoolRelationBo();
        relation.setPoolId(poolId);
        relation.setRelationType(relationType);
        relation.setRelationPoolId(relationPoolId);
        return relation;
    }
}
