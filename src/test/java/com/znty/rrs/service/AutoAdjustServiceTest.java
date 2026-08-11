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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 到期出池任务单元测试。
 */
public class AutoAdjustServiceTest {

    @Test
    public void autoOutExpiredShouldDeleteExpiredSecurities() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        AutoAdjustService service = new AutoAdjustService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("到期出池");
        conf.setParamJson("{\"poolIds\":[10]}");
        when(scheduledTaskMapper.queryTaskByCode(AutoAdjustService.TASK_CODE)).thenReturn(conf);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(10L);
        pool.setPoolName("信用债大库");
        pool.setPoolType("credit_bond");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Arrays.asList(pool));
        IpAdjustLogBo expired = new IpAdjustLogBo();
        expired.setSecurityCode("S001");
        expired.setSecurityShortName("测试债");
        expired.setSecurityType("corporate_bond");
        when(autoAdjustMapper.queryPoolSecurityByExpired(10L)).thenReturn(Arrays.asList(expired));

        ScheduledTaskResult result = service.execute();

        ArgumentCaptor<IpAdjustLogBo> logCaptor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(securityPoolAdjustMapper).addAdjustLog(logCaptor.capture());
        verify(securityPoolAdjustMapper).deletePoolStatusSoft("S001", 10L);
        IpAdjustLogBo log = logCaptor.getValue();
        assertThat(log.getAdjustType()).isEqualTo("自动调整");
        assertThat(log.getAdjustMode()).isEqualTo(AdjustMode.OUT.getCode());
        assertThat(log.getAuditStatus()).isEqualTo(AuditStatus.APPROVED.getCode());
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        assertThat(result.getTaskName()).isEqualTo("到期出池");
        assertThat(service.getTaskCode()).isEqualTo(AutoAdjustService.TASK_CODE);
        verify(autoAdjustMapper, never()).queryAutoOutPoolIds();
    }

    @Test
    public void autoOutExpiredShouldFailWhenParamJsonEmpty() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        AutoAdjustService service = new AutoAdjustService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);

        when(scheduledTaskMapper.queryTaskByCode(AutoAdjustService.TASK_CODE)).thenReturn(null);

        ScheduledTaskResult result = service.execute();

        verify(securityPoolAdjustMapper, never()).addAdjustLog(any(IpAdjustLogBo.class));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("扩展参数未配置");
    }

    @Test
    public void autoOutExpiredShouldFailWhenJsonInvalid() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        AutoAdjustService service = new AutoAdjustService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("到期出池");
        conf.setParamJson("{\"poolIds\":'123'}");
        when(scheduledTaskMapper.queryTaskByCode(AutoAdjustService.TASK_CODE)).thenReturn(conf);

        ScheduledTaskResult result = service.execute();

        verify(securityPoolAdjustMapper, never()).addAdjustLog(any(IpAdjustLogBo.class));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("JSON 解析失败");
    }

    @Test
    public void parsePoolIdsShouldOnlyAcceptJson() {
        AutoAdjustService service = new AutoAdjustService();
        List<Long> ids = service.parsePoolIds("{\"poolIds\":[10,15,20]}");
        assertThat(ids).containsExactly(10L, 15L, 20L);

        assertThatThrownBy(() -> service.parsePoolIds("15-15")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.parsePoolIds("{\"poolIds\":[]}")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.parsePoolIds(null)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.parsePoolIds("{\"poolIds\":'123'}")).isInstanceOf(BizException.class);
        assertThat(service.getParamHelp()).contains("poolIds");
    }
}
