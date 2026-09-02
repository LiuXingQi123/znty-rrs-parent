package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 主体不在池债券自动出池单元测试
 */
public class CompanyNotInPoolBondAutoOutServiceTest {

    @Test
    public void execute_ShouldOutBondWhenCompanyNotInPool() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        CompanyNotInPoolBondAutoOutService service = new CompanyNotInPoolBondAutoOutService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("主体不在池债券自动出池");
        conf.setParamJson("{\"poolIds\":[15]}");
        when(scheduledTaskMapper.queryTaskByCode(CompanyNotInPoolBondAutoOutService.TASK_CODE)).thenReturn(conf);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(15L);
        pool.setPoolName("债券禁止库");
        pool.setPoolType("forbidden");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool));

        IpAdjustLogBo bond = new IpAdjustLogBo();
        bond.setSecurityCode("B001");
        bond.setSecurityShortName("某债");
        when(autoAdjustMapper.queryBondInPoolWhenCompanyNotIn(15L, 15L))
                .thenReturn(Collections.singletonList(bond));
        when(securityPoolAdjustMapper.deletePoolStatusSoft("B001", 15L)).thenReturn(1);
        when(securityPoolAdjustMapper.addAdjustLog(any(IpAdjustLogBo.class))).thenReturn(1);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        verify(securityPoolAdjustMapper).deletePoolStatusSoft(eq("B001"), eq(15L));
        assertThat(bond.getAdjustMode()).isEqualTo(AdjustMode.OUT.getCode());
        assertThat(bond.getAuditStatus()).isEqualTo(AuditStatus.APPROVED.getCode());
        assertThat(bond.getAdjustReason()).contains("主体不在池");
    }

    @Test
    public void parseParamMappings_ShouldAcceptPoolIdsAndMappings() {
        CompanyNotInPoolBondAutoOutService service = new CompanyNotInPoolBondAutoOutService();
        assertThat(service.parseParamMappings("{\"poolIds\":[15]}")).hasSize(1);
        assertThat(service.parseParamMappings(
                "{\"mappings\":[{\"bondPoolId\":15,\"companyPoolId\":16}]}").get(0))
                .containsExactly(15L, 16L);
        assertThat(service.parseParamMappings("{}")).isEmpty();
    }
}
