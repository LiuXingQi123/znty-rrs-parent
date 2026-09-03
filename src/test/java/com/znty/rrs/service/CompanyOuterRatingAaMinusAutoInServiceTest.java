package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
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

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 外评 AA- 及以下主体自动入池任务单元测试
 */
public class CompanyOuterRatingAaMinusAutoInServiceTest {

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
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);

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

        IpAdjustLogBo company = new IpAdjustLogBo();
        company.setSecurityCode("C90005");
        company.setSecurityShortName("某地产公司");
        company.setSecurityType("company");
        company.setOuterRating("AA-");
        when(autoAdjustMapper.queryCompanyInPoolNotInTarget(
                eq(AutoAdjustRestrictHelper.COMPANY_FORBIDDEN_POOL_ID), eq(17L)))
                .thenReturn(Collections.<IpAdjustLogBo>emptyList());
        when(autoAdjustMapper.queryCompanyInPoolNotInTarget(
                eq(AutoAdjustRestrictHelper.KEY_WATCH_POOL_ID), eq(17L)))
                .thenReturn(Collections.<IpAdjustLogBo>emptyList());
        when(autoAdjustMapper.queryCompanyByLowOuterRatingNotInPool(eq(17L)))
                .thenReturn(Collections.singletonList(company));
        when(securityPoolAdjustMapper.addAdjustLog(any(IpAdjustLogBo.class))).thenAnswer(invocation -> {
            IpAdjustLogBo log = (IpAdjustLogBo) invocation.getArguments()[0];
            log.setId(8001L);
            return 1;
        });
        when(securityPoolAdjustMapper.addPoolStatus(any(IpAdjustLogBo.class))).thenReturn(1);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        assertThat(service.getTaskCode()).isEqualTo("company_outer_rating_aa_minus_auto_in");

        ArgumentCaptor<IpAdjustLogBo> captor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(securityPoolAdjustMapper).addAdjustLog(captor.capture());
        verify(securityPoolAdjustMapper).addPoolStatus(any(IpAdjustLogBo.class));
        IpAdjustLogBo log = captor.getValue();
        assertThat(log.getSecurityCode()).isEqualTo("C90005");
        assertThat(log.getSecurityType()).isEqualTo("company");
        assertThat(log.getAdjustType()).isEqualTo("自动调整");
        assertThat(log.getAdjustMode()).isEqualTo(AdjustMode.IN.getCode());
        assertThat(log.getAuditStatus()).isEqualTo(AuditStatus.APPROVED.getCode());
        assertThat(log.getTargetPoolId()).isEqualTo(17L);
        assertThat(log.getAdjustReason()).isEqualTo("外评AA-及以下主体自动入池（近一年孰低外评：AA-）");
        assertThat(log.getAdjustAdvice()).isEqualTo(log.getAdjustReason());
    }

    @Test
    public void getParamHelp_ShouldDescribePledgeBlacklistInbound() {
        CompanyOuterRatingAaMinusAutoInService service = new CompanyOuterRatingAaMinusAutoInService();
        assertThat(service.getParamHelp()).contains("poolIds（主体入池目标池）：可选")
                .contains("17（黑名单质押库）")
                .contains("公司信用债禁止库 15")
                .contains("重点观察名单 23")
                .contains("2/3/4/5/6/7/13/14/19/20");
    }

    @Test
    public void buildAdjustReason_ShouldJoinHitClauses() {
        IpAdjustLogBo company = new IpAdjustLogBo();
        company.setInForbiddenPool(1);
        company.setInRestrictedPool(1);
        company.setInLowOuterRating(1);
        company.setOuterRating("A");
        assertThat(CompanyOuterRatingAaMinusAutoInService.buildAdjustReason(company))
                .isEqualTo("外评AA-及以下主体自动入池（公司信用债禁止库内主体；近一年孰低外评：A；重点观察名单内主体）");
    }

    @Test
    public void execute_ShouldMergeForbiddenAndLowRatingHits() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        CompanyOuterRatingAaMinusAutoInService service = new CompanyOuterRatingAaMinusAutoInService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);

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

        IpAdjustLogBo forbidden = new IpAdjustLogBo();
        forbidden.setSecurityCode("C90005");
        forbidden.setSecurityShortName("某地产公司");
        IpAdjustLogBo lowRating = new IpAdjustLogBo();
        lowRating.setSecurityCode("C90005");
        lowRating.setOuterRating("AA-");
        when(autoAdjustMapper.queryCompanyInPoolNotInTarget(
                eq(AutoAdjustRestrictHelper.COMPANY_FORBIDDEN_POOL_ID), eq(17L)))
                .thenReturn(Collections.singletonList(forbidden));
        when(autoAdjustMapper.queryCompanyInPoolNotInTarget(
                eq(AutoAdjustRestrictHelper.KEY_WATCH_POOL_ID), eq(17L)))
                .thenReturn(Collections.<IpAdjustLogBo>emptyList());
        when(autoAdjustMapper.queryCompanyByLowOuterRatingNotInPool(eq(17L)))
                .thenReturn(Collections.singletonList(lowRating));
        when(securityPoolAdjustMapper.addAdjustLog(any(IpAdjustLogBo.class))).thenAnswer(invocation -> {
            IpAdjustLogBo log = (IpAdjustLogBo) invocation.getArguments()[0];
            log.setId(8002L);
            return 1;
        });
        when(securityPoolAdjustMapper.addPoolStatus(any(IpAdjustLogBo.class))).thenReturn(1);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        ArgumentCaptor<IpAdjustLogBo> captor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(securityPoolAdjustMapper).addAdjustLog(captor.capture());
        assertThat(captor.getValue().getAdjustReason())
                .isEqualTo("外评AA-及以下主体自动入池（公司信用债禁止库内主体；近一年孰低外评：AA-）");
    }

    @Test
    public void execute_ShouldFailWhenParamMissing() {
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        CompanyOuterRatingAaMinusAutoInService service = new CompanyOuterRatingAaMinusAutoInService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mock(SecurityPoolAdjustMapper.class));
        ReflectionTestUtils.setField(service, "investmentPoolMapper", mock(InvestmentPoolMapper.class));
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);
        when(scheduledTaskMapper.queryTaskByCode(CompanyOuterRatingAaMinusAutoInService.TASK_CODE))
                .thenReturn(null);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("未配置扫描池");
    }

    @Test
    public void execute_ShouldSkipWhenNoCandidate() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        CompanyOuterRatingAaMinusAutoInService service = new CompanyOuterRatingAaMinusAutoInService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("外评AA-及以下主体自动入池");
        conf.setParamJson("{\"poolIds\":[17]}");
        when(scheduledTaskMapper.queryTaskByCode(CompanyOuterRatingAaMinusAutoInService.TASK_CODE))
                .thenReturn(conf);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(17L);
        pool.setPoolName("黑名单质押库");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool));
        when(autoAdjustMapper.queryCompanyInPoolNotInTarget(any(Long.class), eq(17L)))
                .thenReturn(Collections.<IpAdjustLogBo>emptyList());
        when(autoAdjustMapper.queryCompanyByLowOuterRatingNotInPool(eq(17L)))
                .thenReturn(Collections.<IpAdjustLogBo>emptyList());

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(0);
        verify(securityPoolAdjustMapper, never()).addAdjustLog(any(IpAdjustLogBo.class));
    }

    @Test
    public void parsePoolIds_ShouldAcceptJsonArray() {
        CompanyOuterRatingAaMinusAutoInService service = new CompanyOuterRatingAaMinusAutoInService();
        List<Long> ids = service.parsePoolIds("{\"poolIds\":[15,16]}");
        assertThat(ids).containsExactly(15L, 16L);
        assertThatThrownBy(() -> service.parsePoolIds("{}")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.parsePoolIds(null)).isInstanceOf(BizException.class);
    }
}
