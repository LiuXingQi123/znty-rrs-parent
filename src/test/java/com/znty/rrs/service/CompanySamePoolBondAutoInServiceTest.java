package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
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
        when(securityPoolAdjustMapper.queryAllPoolRelationList()).thenReturn(Arrays.asList(
                buildRelation(15L, "in_mutex", 3L),
                buildRelation(3L, "in_restrict", 15L)));

        IpAdjustLogBo bond = new IpAdjustLogBo();
        bond.setSecurityCode("112008001.IB");
        bond.setSecurityShortName("测试债");
        bond.setSecurityType("mtn");
        when(autoAdjustMapper.queryCompanyBondSamePoolForAutoIn(15L))
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
        verify(autoAdjustMapper).queryCompanyBondSamePoolForAutoIn(eq(15L));

        ArgumentCaptor<IpAdjustLogBo> captor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(securityPoolAdjustMapper, times(2)).addAdjustLog(captor.capture());
        IpAdjustLogBo inboundLog = captor.getAllValues().get(0);
        IpAdjustLogBo outboundLog = captor.getAllValues().get(1);
        assertThat(inboundLog.getSecurityCode()).isEqualTo("112008001.IB");
        assertThat(inboundLog.getAdjustType()).isEqualTo("自动调整");
        assertThat(inboundLog.getAdjustMode()).isEqualTo(AdjustMode.IN.getCode());
        assertThat(inboundLog.getAuditStatus()).isEqualTo(AuditStatus.APPROVED.getCode());
        assertThat(inboundLog.getTargetPoolId()).isEqualTo(15L);
        assertThat(inboundLog.getAdjustReason()).contains("主体下债券自动入库");
        assertThat(outboundLog.getAdjustType()).isEqualTo("互斥调整");
        assertThat(outboundLog.getAdjustMode()).isEqualTo(AdjustMode.OUT.getCode());
        assertThat(outboundLog.getTargetPoolId()).isEqualTo(3L);
        assertThat(outboundLog.getTargetPoolName()).isEqualTo("二级库");
        assertThat(outboundLog.getAdjustBatchNo()).isEqualTo(inboundLog.getAdjustBatchNo());
        verify(securityPoolAdjustMapper).deletePoolStatusSoft("112008001.IB", 3L);
    }

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
        when(autoAdjustMapper.queryCompanyBondSamePoolForAutoIn(15L))
                .thenReturn(Collections.<IpAdjustLogBo>emptyList());

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(0);
        verify(securityPoolAdjustMapper, never()).addAdjustLog(any(IpAdjustLogBo.class));
    }

    @Test
    public void parsePoolIds_ShouldAcceptJsonArray() {
        CompanySamePoolBondAutoInService service = new CompanySamePoolBondAutoInService();
        List<Long> ids = service.parsePoolIds("{\"poolIds\":[15,16]}");
        assertThat(ids).containsExactly(15L, 16L);
        assertThatThrownBy(() -> service.parsePoolIds("{}")).isInstanceOf(BizException.class);
    }

    private PoolRelationBo buildRelation(Long poolId, String relationType, Long relationPoolId) {
        PoolRelationBo relation = new PoolRelationBo();
        relation.setPoolId(poolId);
        relation.setRelationType(relationType);
        relation.setRelationPoolId(relationPoolId);
        return relation;
    }
}
