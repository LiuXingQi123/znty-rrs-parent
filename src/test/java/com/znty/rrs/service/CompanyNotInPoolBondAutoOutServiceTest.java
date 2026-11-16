package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.entity.bo.CompanyBondTypeScopeBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 主体不在池债券自动出池单元测试
 */
@RunWith(Parameterized.class)
public class CompanyNotInPoolBondAutoOutServiceTest {

    @Parameterized.Parameters
    public static Object[] independentStatusCases() {
        return new Object[]{false, true};
    }

    @Parameterized.Parameter
    public boolean independentStatusRemains;

    @Test
    public void execute_ShouldOutBondWhenCompanyNotInPool() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        CompanyNotInPoolBondAutoOutService service = AbsIdentityTestSupport.withIdentity(new CompanyNotInPoolBondAutoOutService());
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);
        CompanyNewBondAutoInService inbound = mock(CompanyNewBondAutoInService.class);
        ReflectionTestUtils.setField(service, "companyNewBondAutoInService", inbound);
        when(inbound.resolveInboundMappings(any())).thenReturn(Collections.singletonList(new long[]{16L, 15L}));

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("主体不在池债券自动出池");
        conf.setParamJson("{\"poolIds\":[15]}");
        when(scheduledTaskMapper.queryTaskByCode(CompanyNotInPoolBondAutoOutService.TASK_CODE)).thenReturn(conf);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(15L);
        pool.setPoolName("债券禁止库");
        pool.setPoolType("forbidden");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool));

        ScheduledAdjustCandidateDto bond = new ScheduledAdjustCandidateDto();
        bond.setSecurityCode("B001");
        bond.setPoolStatusId(123L);
        bond.setSecurityShortName("某债");
        bond.setIssuerCode("C001");
        bond.setIssuerName("测试集团");
        when(autoAdjustMapper.queryCompanyFollowedBondsForAutoOut(eq(15L),
                eq(new java.util.LinkedHashSet<>(java.util.Arrays.asList(15L, 16L)))))
                .thenReturn(Collections.singletonList(bond));
        when(autoAdjustMapper.deleteCompanyFollowedBondStatus(123L)).thenReturn(1);
        when(securityPoolAdjustMapper.querySecurityCurrentPoolIdList("B001"))
                .thenReturn(Collections.singletonList(15L))
                .thenReturn(independentStatusRemains ? Collections.singletonList(15L) : Collections.emptyList());
        when(securityPoolAdjustMapper.addAdjustLog(any(IpAdjustLogBo.class))).thenReturn(1);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        verify(autoAdjustMapper).deleteCompanyFollowedBondStatus(123L);
        if (independentStatusRemains) {
            assertThat(result.getAffectedCount()).isZero();
            verify(securityPoolAdjustMapper, never()).addAdjustLog(any());
            return;
        }
        assertThat(result.getAffectedCount()).isEqualTo(1);
        assertThat(result.getMessage()).contains("实际出池 1 条", "[15, 16]");
        assertThat(bond.getAdjustMode()).isEqualTo(AdjustMode.OUT.getCode());
        assertThat(bond.getAuditStatus()).isEqualTo(AuditStatus.APPROVED.getCode());
        assertThat(bond.getAdjustReason())
                .isEqualTo("【任务名称：主体不在池债券自动出池（仅债券）】（发行主体：测试集团/C001；主体未在任一依据池：[15, 16]）");
        assertThat(bond.getAdjustAdvice()).isNull();
        assertThat(bond.getAdjustBatchNo()).matches("BOND\\d{17}2001");
        assertThat(result.getDetailLog()).doesNotContain("批次号");
    }

    @Test
    public void parseParamMappings_ShouldAcceptPoolIdsAndMappings() {
        CompanyNotInPoolBondAutoOutService service = AbsIdentityTestSupport.withIdentity(new CompanyNotInPoolBondAutoOutService());
        assertThat(service.parseParamMappings("{\"poolIds\":[15]}")).hasSize(1);
        assertThat(service.parseParamMappings(
                "{\"mappings\":[{\"bondPoolId\":15,\"companyPoolId\":16}]}").get(0))
                .containsExactly(15L, 16L);
        assertThat(service.parseParamMappings("{}")).isEmpty();
    }
}
