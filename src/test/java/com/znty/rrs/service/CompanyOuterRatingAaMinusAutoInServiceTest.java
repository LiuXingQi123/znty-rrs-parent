package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 外评 AA- 及以下主体自动入池任务单元测试
 */
public class CompanyOuterRatingAaMinusAutoInServiceTest {

    /** 验证低外评主体自动调入并同步旗下债券。 */
    @Test
    public void execute_ShouldAutoInCompanyWithLowOuterRating() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        CompanyOuterRatingAaMinusAutoInService service = new CompanyOuterRatingAaMinusAutoInService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        PledgeBlacklistRuleService ruleService = mock(PledgeBlacklistRuleService.class);
        ReflectionTestUtils.setField(service, "pledgeBlacklistRuleService", ruleService);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);
        bindExternalRatingAgency(service);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("外评AA-及以下主体自动入池");
        conf.setParamJson("{\"poolIds\":[17]}");
        when(scheduledTaskMapper.queryTaskByCode(CompanyOuterRatingAaMinusAutoInService.TASK_CODE))
                .thenReturn(conf);

        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(17L);
        pool.setPoolName("黑名单质押库");
        pool.setPoolType("blacklist");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool));
        when(securityPoolAdjustMapper.queryAllPoolRelationList())
                .thenReturn(Collections.<com.znty.rrs.entity.bo.PoolRelationBo>emptyList());

        ScheduledAdjustCandidateDto company = new ScheduledAdjustCandidateDto();
        company.setSecurityCode("C90005");
        company.setSecurityShortName("某地产公司");
        company.setSecurityType("company");
        company.setOuterRating("AA-");
        when(autoAdjustMapper.queryCompanyInPoolNotInTarget(
                eq(AutoAdjustRestrictHelper.COMPANY_FORBIDDEN_POOL_ID), eq(17L)))
                .thenReturn(Collections.<ScheduledAdjustCandidateDto>emptyList());
        when(autoAdjustMapper.queryCompanyInPoolNotInTarget(
                eq(AutoAdjustRestrictHelper.KEY_WATCH_POOL_ID), eq(17L)))
                .thenReturn(Collections.<ScheduledAdjustCandidateDto>emptyList());
        when(autoAdjustMapper.queryCompanyByLowOuterRatingNotInPool(eq(17L), any(List.class)))
                .thenReturn(Collections.singletonList(company));
        when(ruleService.evaluate("C90005"))
                .thenReturn(new PledgeBlacklistRuleService.Decision(false, true, false));

        ScheduledAdjustCandidateDto bond1 = new ScheduledAdjustCandidateDto();
        bond1.setSecurityCode("BOND001.IB");
        bond1.setSecurityShortName("债1");
        bond1.setSecurityType("credit_bond");
        ScheduledAdjustCandidateDto bond2 = new ScheduledAdjustCandidateDto();
        bond2.setSecurityCode("BOND002.IB");
        bond2.setSecurityShortName("债2");
        bond2.setSecurityType("credit_bond");
        ScheduledAdjustCandidateDto bond3 = new ScheduledAdjustCandidateDto();
        bond3.setSecurityCode("BOND003.IB");
        bond3.setSecurityShortName("债3");
        bond3.setSecurityType("abs");
        when(autoAdjustMapper.queryCompanyBondNotInSamePoolForAutoIn(
                eq("C90005"), eq(17L), any(com.znty.rrs.entity.bo.CompanyBondTypeScopeBo.class)))
                .thenReturn(Arrays.asList(bond1, bond2, bond3));
        when(securityPoolAdjustMapper.querySecurityCurrentPoolIdList(any(String.class)))
                .thenReturn(Collections.<Long>emptyList());
        when(securityPoolAdjustMapper.addAdjustLog(any(IpAdjustLogBo.class))).thenAnswer(invocation -> {
            IpAdjustLogBo log = (IpAdjustLogBo) invocation.getArguments()[0];
            log.setId(8001L);
            return 1;
        });
        when(securityPoolAdjustMapper.addPoolStatus(any(IpAdjustLogBo.class))).thenReturn(1);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        assertThat(result.getMessage()).contains("本轮共自动入池 1 个主体、3 只债券")
                .contains("黑名单质押库(17)：1 个主体、3 只债券");
        assertThat(service.getTaskCode()).isEqualTo("company_outer_rating_aa_minus_auto_in");

        ArgumentCaptor<IpAdjustLogBo> captor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        // 1 主体 + 3 债
        verify(securityPoolAdjustMapper, times(4)).addAdjustLog(captor.capture());
        verify(securityPoolAdjustMapper, times(4)).addPoolStatus(any(IpAdjustLogBo.class));
        verify(autoAdjustMapper).queryCompanyBondNotInSamePoolForAutoIn(
                eq("C90005"), eq(17L), any(com.znty.rrs.entity.bo.CompanyBondTypeScopeBo.class));
        verify(ruleService).evaluate("C90005");
        IpAdjustLogBo log = captor.getAllValues().get(0);
        assertThat(log.getSecurityCode()).isEqualTo("C90005");
        assertThat(log.getSecurityType()).isEqualTo("company");
        assertThat(log.getAdjustType()).isEqualTo("自动调整");
        assertThat(log.getAdjustMode()).isEqualTo(AdjustMode.IN.getCode());
        assertThat(log.getAuditStatus()).isEqualTo(AuditStatus.APPROVED.getCode());
        assertThat(log.getTargetPoolId()).isEqualTo(17L);
        assertThat(log.getAdjustReason()).isEqualTo("外评AA-及以下主体自动入池（近一年孰低外评：AA-）");
        assertThat(log.getAdjustAdvice()).isNull();
        assertThat(log.getAdjustBatchNo()).matches("COMP\\d{17}1001");
        assertThat(result.getDetailLog()).doesNotContain("批次号");
    }

    /** 验证参数说明包含黑名单质押库入池口径。 */
    @Test
    public void getParamHelp_ShouldDescribePledgeBlacklistInbound() {
        CompanyOuterRatingAaMinusAutoInService service = new CompanyOuterRatingAaMinusAutoInService();
        assertThat(service.getParamHelp()).contains("poolIds（主体入池目标池）：可选")
                .contains("17（黑名单质押库）")
                .contains("公司信用债禁止库 15")
                .contains("重点观察名单 23")
                .contains("配置表中的有效机构")
                .contains("不复用人工 syncCompanyBonds");
    }

    /** 未配置有效外部评级机构时应阻断本轮自动入池。 */
    @Test
    public void execute_ShouldFailWhenNoAgencyConfigured() {
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        ExternalRatingAgencyService agencyService = mock(ExternalRatingAgencyService.class);
        CompanyOuterRatingAaMinusAutoInService service = new CompanyOuterRatingAaMinusAutoInService();
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        ReflectionTestUtils.setField(service, "externalRatingAgencyService", agencyService);
        when(agencyService.queryRequiredAgencyCodeList())
                .thenThrow(new BizException("未配置有效外部评级机构"));

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("未配置有效外部评级机构");
    }

    /** 验证调整原因合并展示所有命中条件。 */
    @Test
    public void buildAdjustReason_ShouldJoinHitClauses() {
        ScheduledAdjustCandidateDto company = new ScheduledAdjustCandidateDto();
        company.setInForbiddenPool(1);
        company.setInRestrictedPool(1);
        company.setInLowOuterRating(1);
        company.setOuterRating("AA-");
        assertThat(CompanyOuterRatingAaMinusAutoInService.buildAdjustReason(company))
                .contains("禁止库")
                .contains("重点观察")
                .contains("AA-");
    }

    private static void bindExternalRatingAgency(CompanyOuterRatingAaMinusAutoInService service) {
        ExternalRatingAgencyService agencyService = mock(ExternalRatingAgencyService.class);
        when(agencyService.queryRequiredAgencyCodeList())
                .thenReturn(Collections.singletonList("中诚信"));
        ReflectionTestUtils.setField(service, "externalRatingAgencyService", agencyService);
    }
}
