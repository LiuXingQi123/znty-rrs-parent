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
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 在池主体旗下债券自动入池任务单元测试。
 */
public class CompanyNewBondAutoInServiceTest {

    @Test
    public void executeShouldInsertNewBonds() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        CompanyNewBondAutoInService service = new CompanyNewBondAutoInService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("在池主体旗下债券自动入池");
        conf.setParamJson("{\"poolIds\":[15]}");
        when(scheduledTaskMapper.queryTaskByCode(CompanyNewBondAutoInService.TASK_CODE)).thenReturn(conf);

        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(15L);
        pool.setPoolName("债券禁止库");
        pool.setPoolType("forbidden");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Arrays.asList(pool));

        IpAdjustLogBo bond = new IpAdjustLogBo();
        bond.setSecurityCode("BOND001.IB");
        bond.setSecurityShortName("测试新债");
        bond.setSecurityType("mtn");
        when(autoAdjustMapper.queryCompanyNewBondForAutoIn(15L, 15L)).thenReturn(Arrays.asList(bond));
        when(securityPoolAdjustMapper.addAdjustLog(any(IpAdjustLogBo.class))).thenAnswer(invocation -> {
            IpAdjustLogBo log = (IpAdjustLogBo) invocation.getArguments()[0];
            log.setId(9001L);
            return 1;
        });
        when(securityPoolAdjustMapper.addPoolStatus(any(IpAdjustLogBo.class))).thenReturn(1);

        ScheduledTaskResult result = service.execute();

        ArgumentCaptor<IpAdjustLogBo> logCaptor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(securityPoolAdjustMapper).addAdjustLog(logCaptor.capture());
        verify(securityPoolAdjustMapper).addPoolStatus(logCaptor.capture());
        IpAdjustLogBo adjustLog = logCaptor.getAllValues().get(0);
        assertThat(adjustLog.getAdjustType()).isEqualTo("自动调整");
        assertThat(adjustLog.getAdjustMode()).isEqualTo(AdjustMode.IN.getCode());
        assertThat(adjustLog.getAuditStatus()).isEqualTo(AuditStatus.APPROVED.getCode());
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        verify(autoAdjustMapper).queryCompanyNewBondForAutoIn(eq(15L), eq(15L));
    }

    @Test
    public void executeShouldFailWhenParamJsonEmpty() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        CompanyNewBondAutoInService service = new CompanyNewBondAutoInService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);

        when(scheduledTaskMapper.queryTaskByCode(any(String.class))).thenReturn(null);

        ScheduledTaskResult result = service.execute();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("未配置扫描池");
        verify(autoAdjustMapper, never()).queryCompanyNewBondForAutoIn(any(Long.class), any(Long.class));
    }

    @Test
    public void parseParamMappingsShouldOnlyAcceptJson() {
        CompanyNewBondAutoInService service = new CompanyNewBondAutoInService();
        List<long[]> samePool = service.parseParamMappings("{\"poolIds\":[15]}");
        assertThat(samePool).hasSize(1);
        assertThat(samePool.get(0)[0]).isEqualTo(15L);
        assertThat(samePool.get(0)[1]).isEqualTo(15L);

        List<long[]> multiPool = service.parseParamMappings("{\"poolIds\":[15,16]}");
        assertThat(multiPool).hasSize(2);
        assertThat(multiPool.get(1)[0]).isEqualTo(16L);
        assertThat(multiPool.get(1)[1]).isEqualTo(16L);

        List<long[]> multi = service.parseParamMappings(
                "{\"mappings\":[{\"companyInPoolId\":15,\"bondTargetPoolId\":15},"
                        + "{\"companyInPoolId\":16,\"bondTargetPoolId\":100}]}");
        assertThat(multi).hasSize(2);
        assertThat(multi.get(1)[0]).isEqualTo(16L);
        assertThat(multi.get(1)[1]).isEqualTo(100L);

        // 旧式文本 / 非法 JSON → 业务异常（执行结果记失败）
        try {
            service.parseParamMappings("15-15,16-100");
            org.junit.Assert.fail("expected BizException");
        } catch (com.znty.rrs.exception.BizException expected) {
            assertThat(expected.getMessage()).contains("JSON");
        }
    }

    @Test
    public void getParamHelpShouldDescribeJsonContract() {
        CompanyNewBondAutoInService service = new CompanyNewBondAutoInService();
        assertThat(service.getParamHelp()).contains("poolIds");
        assertThat(service.getParamHelp()).contains("mappings");
        assertThat(service.getParamHelp()).contains("须填写 JSON");
        assertThat(service.getParamHelp()).doesNotContain("1)");
    }

    @Test
    public void executeShouldContinueWhenPoolStatusInsertFails() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        CompanyNewBondAutoInService service = new CompanyNewBondAutoInService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("在池主体旗下债券自动入池");
        conf.setParamJson("{\"poolIds\":[15]}");
        when(scheduledTaskMapper.queryTaskByCode(CompanyNewBondAutoInService.TASK_CODE)).thenReturn(conf);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(15L);
        pool.setPoolName("债券禁止库");
        pool.setPoolType("forbidden");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Arrays.asList(pool));

        IpAdjustLogBo bond1 = new IpAdjustLogBo();
        bond1.setSecurityCode("B1");
        bond1.setSecurityShortName("债1");
        bond1.setSecurityType("mtn");
        IpAdjustLogBo bond2 = new IpAdjustLogBo();
        bond2.setSecurityCode("B2");
        bond2.setSecurityShortName("债2");
        bond2.setSecurityType("mtn");
        when(autoAdjustMapper.queryCompanyNewBondForAutoIn(15L, 15L)).thenReturn(Arrays.asList(bond1, bond2));
        when(securityPoolAdjustMapper.addAdjustLog(any(IpAdjustLogBo.class))).thenAnswer(invocation -> {
            IpAdjustLogBo log = (IpAdjustLogBo) invocation.getArguments()[0];
            log.setId(1L);
            return 1;
        });
        when(securityPoolAdjustMapper.addPoolStatus(any(IpAdjustLogBo.class))).thenReturn(0).thenReturn(1);

        ScheduledTaskResult result = service.execute();
        verify(securityPoolAdjustMapper, times(2)).addAdjustLog(any(IpAdjustLogBo.class));
        assertThat(result.getAffectedCount()).isEqualTo(1);
    }
}
