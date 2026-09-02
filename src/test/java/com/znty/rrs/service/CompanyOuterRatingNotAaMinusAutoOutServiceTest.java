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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 外评非 AA- 及以下主体自动出池任务单元测试
 */
public class CompanyOuterRatingNotAaMinusAutoOutServiceTest {

    @Test
    public void getParamHelp_ShouldDescribeDefaultPoolsAndParameters() {
        CompanyOuterRatingNotAaMinusAutoOutService service = new CompanyOuterRatingNotAaMinusAutoOutService();

        assertThat(service.getParamHelp()).contains("poolIds（主体出池目标池）：可选")
                .contains("16（观察池）")
                .contains("主体出池目标池")
                .contains("禁止出池拦截池")
                .contains("不从扫描目标池自动出库")
                .contains("同时在 15（债券禁止库）的主体被排除")
                .contains("15（债券禁止库）");
    }

    @Test
    public void execute_ShouldAutoOutCompanyWithHighOuterRating() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        CompanyOuterRatingNotAaMinusAutoOutService service = new CompanyOuterRatingNotAaMinusAutoOutService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("外评非AA-及以下主体自动出池");
        conf.setParamJson("{\"poolIds\":[16],\"limitPoolIds\":[15]}");
        when(scheduledTaskMapper.queryTaskByCode(CompanyOuterRatingNotAaMinusAutoOutService.TASK_CODE))
                .thenReturn(conf);

        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(16L);
        pool.setPoolName("观察池");
        pool.setPoolType("observe");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool));

        IpAdjustLogBo company = new IpAdjustLogBo();
        company.setSecurityCode("C90001");
        company.setSecurityShortName("某高评级公司");
        company.setSecurityType("company");
        when(autoAdjustMapper.queryCompanyByNotLowOuterRatingInPool(eq(16L), eq(Collections.singletonList(15L))))
                .thenReturn(Collections.singletonList(company));
        when(autoAdjustMapper.queryCompanyBondInSamePoolForAutoOut("C90001", 16L))
                .thenReturn(Collections.<IpAdjustLogBo>emptyList());
        when(securityPoolAdjustMapper.addAdjustLog(any(IpAdjustLogBo.class))).thenAnswer(invocation -> {
            IpAdjustLogBo log = (IpAdjustLogBo) invocation.getArguments()[0];
            log.setId(9001L);
            return 1;
        });
        when(securityPoolAdjustMapper.deletePoolStatusSoft("C90001", 16L)).thenReturn(1);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        assertThat(service.getTaskCode()).isEqualTo("company_outer_rating_not_aa_minus_auto_out");

        ArgumentCaptor<IpAdjustLogBo> captor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(securityPoolAdjustMapper).addAdjustLog(captor.capture());
        verify(securityPoolAdjustMapper).deletePoolStatusSoft(eq("C90001"), eq(16L));
        IpAdjustLogBo log = captor.getValue();
        assertThat(log.getSecurityCode()).isEqualTo("C90001");
        assertThat(log.getSecurityType()).isEqualTo("company");
        assertThat(log.getAdjustType()).isEqualTo("自动调整");
        assertThat(log.getAdjustMode()).isEqualTo(AdjustMode.OUT.getCode());
        assertThat(log.getAuditStatus()).isEqualTo(AuditStatus.APPROVED.getCode());
        assertThat(log.getTargetPoolId()).isEqualTo(16L);
        assertThat(log.getAdjustReason()).contains("外评非AA-及以下");
    }

    @Test
    public void execute_ShouldFailWhenParamMissing() {
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        CompanyOuterRatingNotAaMinusAutoOutService service = new CompanyOuterRatingNotAaMinusAutoOutService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mock(SecurityPoolAdjustMapper.class));
        ReflectionTestUtils.setField(service, "investmentPoolMapper", mock(InvestmentPoolMapper.class));
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);
        when(scheduledTaskMapper.queryTaskByCode(CompanyOuterRatingNotAaMinusAutoOutService.TASK_CODE))
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
        CompanyOuterRatingNotAaMinusAutoOutService service = new CompanyOuterRatingNotAaMinusAutoOutService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("外评非AA-及以下主体自动出池");
        conf.setParamJson("{\"poolIds\":[15]}");
        when(scheduledTaskMapper.queryTaskByCode(CompanyOuterRatingNotAaMinusAutoOutService.TASK_CODE))
                .thenReturn(conf);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(15L);
        pool.setPoolName("债券禁止库");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool));
        when(autoAdjustMapper.queryCompanyByNotLowOuterRatingInPool(eq(15L), any(List.class)))
                .thenReturn(Collections.<IpAdjustLogBo>emptyList());

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(0);
        verify(securityPoolAdjustMapper, never()).addAdjustLog(any(IpAdjustLogBo.class));
        verify(securityPoolAdjustMapper, never()).deletePoolStatusSoft(any(String.class), any(Long.class));
    }

    @Test
    public void execute_ShouldOutSamePoolBondsAfterCompanyOut() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        CompanyOuterRatingNotAaMinusAutoOutService service = new CompanyOuterRatingNotAaMinusAutoOutService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("外评非AA-及以下主体自动出池");
        conf.setParamJson("{\"poolIds\":[16],\"limitPoolIds\":[15]}");
        when(scheduledTaskMapper.queryTaskByCode(CompanyOuterRatingNotAaMinusAutoOutService.TASK_CODE))
                .thenReturn(conf);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(16L);
        pool.setPoolName("观察池");
        pool.setPoolType("observe");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool));

        IpAdjustLogBo company = new IpAdjustLogBo();
        company.setSecurityCode("C90001");
        company.setSecurityType("company");
        IpAdjustLogBo bond = new IpAdjustLogBo();
        bond.setSecurityCode("B001");
        bond.setSecurityShortName("某债");
        bond.setSecurityType("corporate_bond");
        when(autoAdjustMapper.queryCompanyByNotLowOuterRatingInPool(eq(16L), eq(Collections.singletonList(15L))))
                .thenReturn(Collections.singletonList(company));
        when(autoAdjustMapper.queryCompanyBondInSamePoolForAutoOut("C90001", 16L))
                .thenReturn(Collections.singletonList(bond));
        when(securityPoolAdjustMapper.addAdjustLog(any(IpAdjustLogBo.class))).thenReturn(1);
        when(securityPoolAdjustMapper.deletePoolStatusSoft("C90001", 16L)).thenReturn(1);
        when(securityPoolAdjustMapper.deletePoolStatusSoft("B001", 16L)).thenReturn(1);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(2);
        verify(securityPoolAdjustMapper).deletePoolStatusSoft(eq("C90001"), eq(16L));
        verify(securityPoolAdjustMapper).deletePoolStatusSoft(eq("B001"), eq(16L));
    }

    @Test
    public void resolveLimitPoolIds_ShouldDefaultToForbiddenPools() {
        CompanyOuterRatingNotAaMinusAutoOutService service = new CompanyOuterRatingNotAaMinusAutoOutService();
        InvestmentPoolBo forbidden = new InvestmentPoolBo();
        forbidden.setId(15L);
        forbidden.setPoolType("forbidden");
        InvestmentPoolBo observe = new InvestmentPoolBo();
        observe.setId(16L);
        observe.setPoolType("observe");
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        poolMap.put(15L, forbidden);
        poolMap.put(16L, observe);

        assertThat(service.resolveLimitPoolIds("{\"poolIds\":[16]}", poolMap)).containsExactly(15L);
        assertThat(service.resolveLimitPoolIds("{\"poolIds\":[16],\"limitPoolIds\":[]}", poolMap)).isEmpty();
        assertThat(service.resolveLimitPoolIds("{\"poolIds\":[16],\"limitPoolIds\":[15,23]}", poolMap))
                .containsExactly(15L, 23L);
    }

    @Test
    public void parsePoolIds_ShouldAcceptJsonArray() {
        CompanyOuterRatingNotAaMinusAutoOutService service = new CompanyOuterRatingNotAaMinusAutoOutService();
        List<Long> ids = service.parsePoolIds("{\"poolIds\":[15,16]}");
        assertThat(ids).containsExactly(15L, 16L);
        assertThatThrownBy(() -> service.parsePoolIds("{}")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.parsePoolIds(null)).isInstanceOf(BizException.class);
    }
}
