package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.CrmwPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CRMW 到期自动出池单元测试
 */
public class CrmwExpiredAutoOutServiceTest {

    @Test
    public void execute_ShouldOutExpiredCrmw() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        CrmwPoolAdjustMapper crmwPoolAdjustMapper = mock(CrmwPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        CrmwExpiredAutoOutService service = new CrmwExpiredAutoOutService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "crmwPoolAdjustMapper", crmwPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("CRMW到期自动出池");
        conf.setParamJson("{\"poolIds\":[18]}");
        when(scheduledTaskMapper.queryTaskByCode(CrmwExpiredAutoOutService.TASK_CODE)).thenReturn(conf);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(18L);
        pool.setPoolName("CRMW库");
        pool.setPoolType("crmw");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool));
        when(crmwPoolAdjustMapper.queryAllPoolRelationList()).thenReturn(Collections.<PoolRelationBo>emptyList());

        IpAdjustLogBo item = new IpAdjustLogBo();
        item.setSecurityCode("B001");
        item.setCrmwScode("CRMW001");
        item.setCrmwStype("crmw");
        when(autoAdjustMapper.queryCrmwPoolByExpired(18L)).thenReturn(Collections.singletonList(item));
        when(crmwPoolAdjustMapper.deletePoolStatusSoft("B001", "CRMW001", "crmw", 18L)).thenReturn(1);
        when(crmwPoolAdjustMapper.addAdjustLog(any(IpAdjustLogBo.class))).thenReturn(1);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        verify(crmwPoolAdjustMapper).deletePoolStatusSoft(eq("B001"), eq("CRMW001"), eq("crmw"), eq(18L));
        verify(crmwPoolAdjustMapper).addAdjustLog(any(IpAdjustLogBo.class));
        assertThat(item.getAdjustMode()).isEqualTo(AdjustMode.OUT.getCode());
        assertThat(item.getAuditStatus()).isEqualTo(AuditStatus.APPROVED.getCode());
    }

    @Test
    public void execute_ShouldSkipWhenSoftDeleteMisses() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        CrmwPoolAdjustMapper crmwPoolAdjustMapper = mock(CrmwPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        CrmwExpiredAutoOutService service = new CrmwExpiredAutoOutService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "crmwPoolAdjustMapper", crmwPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("CRMW到期自动出池");
        conf.setParamJson("{\"poolIds\":[18]}");
        when(scheduledTaskMapper.queryTaskByCode(CrmwExpiredAutoOutService.TASK_CODE)).thenReturn(conf);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(18L);
        pool.setPoolName("CRMW库");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool));
        when(crmwPoolAdjustMapper.queryAllPoolRelationList()).thenReturn(Collections.<PoolRelationBo>emptyList());
        IpAdjustLogBo item = new IpAdjustLogBo();
        item.setSecurityCode("B001");
        item.setCrmwScode("CRMW001");
        item.setCrmwStype("crmw");
        when(autoAdjustMapper.queryCrmwPoolByExpired(18L)).thenReturn(Collections.singletonList(item));
        when(crmwPoolAdjustMapper.deletePoolStatusSoft("B001", "CRMW001", "crmw", 18L)).thenReturn(0);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(0);
        verify(crmwPoolAdjustMapper, never()).addAdjustLog(any(IpAdjustLogBo.class));
    }

    @Test
    public void parsePoolIds_ShouldAcceptJson() {
        CrmwExpiredAutoOutService service = new CrmwExpiredAutoOutService();
        assertThat(service.parsePoolIds("{\"poolIds\":[18]}")).containsExactly(18L);
        assertThatThrownBy(() -> service.parsePoolIds("{}")).isInstanceOf(BizException.class);
    }
}
