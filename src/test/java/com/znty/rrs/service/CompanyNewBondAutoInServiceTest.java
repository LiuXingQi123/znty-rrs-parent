package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.entity.bo.CompanyBondTypeScopeBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 在池主体旗下债券自动入池任务单元测试。
 */
public class CompanyNewBondAutoInServiceTest {

    /** 自动扫描主体同池映射，使用已读取的市场配置并跳过停用目标池 */
    @Test
    public void executeShouldUseLoadedPoolConfigForCandidateQueries() {
        AutoAdjustMapper mapper = mock(AutoAdjustMapper.class);
        InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        CompanyNewBondAutoInService service = AbsIdentityTestSupport.withIdentity(new CompanyNewBondAutoInService());
        ReflectionTestUtils.setField(service, "autoAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mock(SecurityPoolAdjustMapper.class));
        AutoAdjustTestSupport.bindPoolScope(service, mapper);
        InvestmentPoolBo observe = new InvestmentPoolBo();
        observe.setId(16L);
        observe.setStatus("enabled");
        observe.setMarketCodes("[\"NIB\"]");
        InvestmentPoolBo watch = new InvestmentPoolBo();
        watch.setId(23L);
        watch.setStatus("enabled");
        InvestmentPoolBo disabled = new InvestmentPoolBo();
        disabled.setId(17L);
        disabled.setStatus("disabled");
        SysScheduledTaskBo config = new SysScheduledTaskBo();
        config.setParamJson("{\"poolIds\":[17]}");
        when(scheduledTaskMapper.queryTaskByCode(CompanyNewBondAutoInService.TASK_CODE)).thenReturn(config);
        when(poolMapper.queryPoolList()).thenReturn(Arrays.asList(observe, watch, disabled));
        when(mapper.queryCompanyCurrentPoolIds()).thenReturn(Arrays.asList(16L, 23L));

        assertThat(service.execute().isSuccess()).isTrue();

        verify(mapper).queryCompanyNewBondForAutoIn(eq(16L), eq(16L), eq("[\"NIB\"]"), any(CompanyBondTypeScopeBo.class));
        verify(mapper).queryCompanyNewBondForAutoIn(eq(23L), eq(23L), isNull(), any(CompanyBondTypeScopeBo.class));
        verify(mapper, never()).queryCompanyNewBondForAutoIn(any(), eq(17L), any(), any());
    }

    @Test
    public void executeShouldInsertNewBonds() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        CompanyNewBondAutoInService service = AbsIdentityTestSupport.withIdentity(new CompanyNewBondAutoInService());
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("在池主体旗下债券自动入池（已排除ABS债）");
        conf.setParamJson("{\"poolIds\":[15]}");
        when(scheduledTaskMapper.queryTaskByCode(CompanyNewBondAutoInService.TASK_CODE)).thenReturn(conf);

        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(15L);
        pool.setPoolName("债券禁止库");
        pool.setPoolType("forbidden");
        pool.setStatus("enabled");
        InvestmentPoolBo relatedPool = new InvestmentPoolBo();
        relatedPool.setId(3L);
        relatedPool.setPoolName("二级库");
        relatedPool.setPoolType("credit_bond");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Arrays.asList(pool, relatedPool));
        when(securityPoolAdjustMapper.queryAllPoolRelationList()).thenReturn(Arrays.asList(
                buildRelation(15L, "in_mutex", 3L),
                buildRelation(3L, "in_restrict", 15L)));

        ScheduledAdjustCandidateDto bond = new ScheduledAdjustCandidateDto();
        bond.setSecurityCode("BOND001.IB");
        bond.setSecurityShortName("测试新债");
        bond.setSecurityType("mtn");
        bond.setIssuerCode("C001");
        bond.setIssuerName("测试集团");
        when(autoAdjustMapper.queryCompanyNewBondForAutoIn(eq(15L), eq(15L), isNull(),
                any(CompanyBondTypeScopeBo.class))).thenReturn(Arrays.asList(bond));
        when(securityPoolAdjustMapper.querySecurityCurrentPoolIdList("BOND001.IB"))
                .thenReturn(Arrays.asList(3L));
        when(securityPoolAdjustMapper.addAdjustLog(any(IpAdjustLogBo.class))).thenAnswer(invocation -> {
            IpAdjustLogBo log = (IpAdjustLogBo) invocation.getArguments()[0];
            log.setId(9001L);
            return 1;
        });
        when(securityPoolAdjustMapper.addPoolStatus(any(IpAdjustLogBo.class))).thenReturn(1);
        when(securityPoolAdjustMapper.deletePoolStatusSoft("BOND001.IB", 3L)).thenReturn(1);

        ScheduledTaskResult result = service.execute();

        ArgumentCaptor<IpAdjustLogBo> logCaptor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(securityPoolAdjustMapper, times(2)).addAdjustLog(logCaptor.capture());
        verify(securityPoolAdjustMapper).addPoolStatus(any(IpAdjustLogBo.class));
        IpAdjustLogBo adjustLog = logCaptor.getAllValues().get(0);
        IpAdjustLogBo autoOutLog = logCaptor.getAllValues().get(1);
        assertThat(adjustLog.getAdjustType()).isEqualTo("自动调整");
        assertThat(adjustLog.getAdjustMode()).isEqualTo(AdjustMode.IN.getCode());
        assertThat(adjustLog.getAuditStatus()).isEqualTo(AuditStatus.APPROVED.getCode());
        assertThat(adjustLog.getAdjustReason())
                .isEqualTo("【任务名称：在池主体旗下债券自动入池（已排除ABS债）（仅债券）】【主体跟随】（发行主体：测试集团/C001；依据池ID：15；主体所在池：债券禁止库）");
        assertThat(adjustLog.getAdjustAdvice()).isNull();
        assertThat(adjustLog.getAdjustBatchNo()).matches("BOND\\d{17}1001");
        assertThat(autoOutLog.getAdjustType()).isEqualTo("互斥调整");
        assertThat(autoOutLog.getAdjustMode()).isEqualTo(AdjustMode.OUT.getCode());
        assertThat(autoOutLog.getTargetPoolId()).isEqualTo(3L);
        assertThat(autoOutLog.getTargetPoolName()).isEqualTo("二级库");
        assertThat(autoOutLog.getAdjustBatchNo()).isEqualTo(adjustLog.getAdjustBatchNo());
        assertThat(autoOutLog.getAdjustReason()).contains("池关系触发：调入债券禁止库后自动调出二级库");
        assertThat(autoOutLog.getAdjustAdvice()).isNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        assertThat(result.getTaskName()).isEqualTo("在池主体旗下债券自动入池（已排除ABS债）（仅债券）");
        assertThat(result.getMessage()).contains("任务完成：扫描候选债券 1 条，实际入池 1 条，未处理 0 条")
                .contains("主体池债券禁止库（15）→债券池债券禁止库（15）：扫描 1 条，入池 1 条，未处理 0 条");
        assertThat(result.getDetailLog()).doesNotContain("批次号");
        ArgumentCaptor<CompanyBondTypeScopeBo> scopeCaptor = ArgumentCaptor.forClass(CompanyBondTypeScopeBo.class);
        verify(autoAdjustMapper).queryCompanyNewBondForAutoIn(eq(15L), eq(15L), isNull(), scopeCaptor.capture());
        assertThat(scopeCaptor.getValue().isExcludeAbs()).isTrue();
        assertThat(scopeCaptor.getValue().getExcludedSecurityTypes()).isEmpty();
        verify(securityPoolAdjustMapper).deletePoolStatusSoft("BOND001.IB", 3L);
    }

    @Test
    public void executeShouldDoNothingWhenNoMappingsExist() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        CompanyNewBondAutoInService service = AbsIdentityTestSupport.withIdentity(new CompanyNewBondAutoInService());
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);

        when(scheduledTaskMapper.queryTaskByCode(any(String.class))).thenReturn(null);

        ScheduledTaskResult result = service.execute();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isZero();
        verify(autoAdjustMapper, never()).queryCompanyNewBondForAutoIn(
                any(Long.class), any(Long.class), any(), any(CompanyBondTypeScopeBo.class));
    }

    @Test
    public void parseParamMappingsShouldOnlyAcceptJson() {
        CompanyNewBondAutoInService service = AbsIdentityTestSupport.withIdentity(new CompanyNewBondAutoInService());
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
        } catch (BizException expected) {
            assertThat(expected.getMessage()).contains("JSON");
        }
    }

    @Test
    public void getParamHelpShouldDescribeJsonContract() {
        CompanyNewBondAutoInService service = AbsIdentityTestSupport.withIdentity(new CompanyNewBondAutoInService());
        assertThat(service.getParamHelp()).contains("poolIds");
        assertThat(service.getParamHelp()).contains("mappings");
        assertThat(service.getParamHelp()).contains("须填写 JSON");
        assertThat(service.getParamHelp()).doesNotContain("1)");
    }

    @Test
    public void executeShouldFailWhenPoolStatusInsertFails() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        CompanyNewBondAutoInService service = AbsIdentityTestSupport.withIdentity(new CompanyNewBondAutoInService());
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("在池主体旗下债券自动入池（已排除ABS债）");
        conf.setParamJson("{\"poolIds\":[15]}");
        when(scheduledTaskMapper.queryTaskByCode(CompanyNewBondAutoInService.TASK_CODE)).thenReturn(conf);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(15L);
        pool.setPoolName("债券禁止库");
        pool.setPoolType("forbidden");
        pool.setStatus("enabled");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Arrays.asList(pool));

        ScheduledAdjustCandidateDto bond1 = new ScheduledAdjustCandidateDto();
        bond1.setSecurityCode("B1");
        bond1.setSecurityShortName("债1");
        bond1.setSecurityType("mtn");
        ScheduledAdjustCandidateDto bond2 = new ScheduledAdjustCandidateDto();
        bond2.setSecurityCode("B2");
        bond2.setSecurityShortName("债2");
        bond2.setSecurityType("mtn");
        when(autoAdjustMapper.queryCompanyNewBondForAutoIn(eq(15L), eq(15L), isNull(),
                any(CompanyBondTypeScopeBo.class))).thenReturn(Arrays.asList(bond1, bond2));
        when(securityPoolAdjustMapper.addAdjustLog(any(IpAdjustLogBo.class))).thenAnswer(invocation -> {
            IpAdjustLogBo log = (IpAdjustLogBo) invocation.getArguments()[0];
            log.setId(1L);
            return 1;
        });
        when(securityPoolAdjustMapper.addPoolStatus(any(IpAdjustLogBo.class))).thenReturn(0).thenReturn(1);

        ScheduledTaskResult result = service.execute();
        verify(securityPoolAdjustMapper, times(1)).addAdjustLog(any(IpAdjustLogBo.class));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAffectedCount()).isZero();
    }

    /** 构建测试用投资池关系。 */
    private PoolRelationBo buildRelation(Long poolId, String relationType, Long relationPoolId) {
        PoolRelationBo relation = new PoolRelationBo();
        relation.setPoolId(poolId);
        relation.setRelationType(relationType);
        relation.setRelationPoolId(relationPoolId);
        return relation;
    }
}
