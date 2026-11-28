package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.entity.bo.AdjustSecuritySnapshotBo;
import com.znty.rrs.entity.bo.CompanyBondTypeScopeBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.ExcelPoolExclusionMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 在池主体旗下债券自动入池任务单元测试
 */
public class CompanyNewBondAutoInServiceTest {

    /** 自动扫描主体同池映射并跳过停用目标池 */
    @Test
    public void executeShouldUseLoadedPoolConfigForCandidateQueries() {
        AutoAdjustMapper mapper = mock(AutoAdjustMapper.class);
        InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        CompanyNewBondAutoInService service = AbsIdentityTestSupport.withIdentity(new CompanyNewBondAutoInService());
        ReflectionTestUtils.setField(service, "autoAdjustMapper", mapper);
        AutoAdjustTestSupport.bindExcelExclusion(service);
        when(mapper.queryDatabaseDate()).thenReturn(LocalDate.of(2026, 11, 18));
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mock(SecurityPoolAdjustMapper.class));
        AutoAdjustTestSupport.bindPoolScope(service, mapper);
        InvestmentPoolBo observe = new InvestmentPoolBo();
        observe.setId(16L);
        observe.setStatus("enabled");
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

        verify(mapper).queryCompanyNewBondForAutoIn(eq(16L), eq(16L),
                any(CompanyBondTypeScopeBo.class));
        verify(mapper).queryCompanyNewBondForAutoIn(eq(23L), eq(23L),
                any(CompanyBondTypeScopeBo.class));
        verify(mapper, never()).queryCompanyNewBondForAutoIn(any(), eq(17L), any());
    }

    /** 验证自动补入担保债时保存担保人和内评，并调出互斥池 */
    @Test
    public void executeShouldInsertNewBonds() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        CompanyNewBondAutoInService service = AbsIdentityTestSupport.withIdentity(new CompanyNewBondAutoInService());
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        AutoAdjustTestSupport.bindExcelExclusion(service);
        when(autoAdjustMapper.queryDatabaseDate()).thenReturn(LocalDate.of(2026, 11, 18));
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("在池主体旗下债券自动入池（已排除ABS债）");
        conf.setParamJson("{\"poolIds\":[15],\"minDaysToMaturity\":1}");
        when(scheduledTaskMapper.queryTaskByCode(CompanyNewBondAutoInService.TASK_CODE)).thenReturn(conf);

        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(15L);
        pool.setPoolName("债券禁止库");
        pool.setPoolType("forbidden");
        pool.setStatus("enabled");
        pool.setMaxCapacity(1L);
        InvestmentPoolBo relatedPool = new InvestmentPoolBo();
        relatedPool.setId(3L);
        relatedPool.setPoolName("二级库");
        relatedPool.setPoolType("credit_bond");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Arrays.asList(pool, relatedPool));

        ScheduledAdjustCandidateDto bond = new ScheduledAdjustCandidateDto();
        bond.setSecurityCode("BOND001.IB");
        bond.setSecurityShortName("测试新债");
        bond.setSecurityType("mtn");
        bond.setIssuerCode("C001");
        bond.setIssuerName("测试集团");
        ScheduledAdjustCandidateDto alreadyInPoolBond = new ScheduledAdjustCandidateDto();
        alreadyInPoolBond.setSecurityCode("BOND002.IB");
        alreadyInPoolBond.setIssuerCode("C001");
        when(autoAdjustMapper.queryCompanyNewBondForAutoIn(eq(15L), eq(15L),
                any(CompanyBondTypeScopeBo.class))).thenReturn(Arrays.asList(bond, alreadyInPoolBond));
        BatchSecurityCheckService checks = mock(BatchSecurityCheckService.class);
        ReflectionTestUtils.setField(service, "batchSecurityCheckService", checks);
        AdjustCheckDto.CheckResultItem inbound = checkedItem("BOND001.IB", 15L, "债券禁止库", "调入");
        AdjustCheckDto.CheckResultItem mutex = checkedItem("BOND001.IB", 3L, "二级库", "调出");
        AdjustCheckDto.CheckResultItem existing = checkedItem("BOND002.IB", 15L, "债券禁止库", "调入");
        existing.setCanAdjust(false);
        existing.setFailReasons(Collections.singletonList("证券已在目标投资池中"));
        AdjustCheckDto inboundResult = checkResult(inbound, mutex);
        SecurityInfoBo checkedSecurity = new SecurityInfoBo();
        checkedSecurity.setGuarantFlag(1);
        checkedSecurity.setGuarantorId("C10008");
        checkedSecurity.setGuarantor("某资产管理股份有限公司");
        checkedSecurity.setInnerGuarantorRating("2+");
        inboundResult.setCheckedSecurityInfo(checkedSecurity);
        when(checks.checkCompanyFollowBatch(anyList(), eq("20261119"), any()))
                .thenReturn(Arrays.asList(inboundResult, checkResult(existing)));
        when(securityPoolAdjustMapper.addAdjustLog(any(IpAdjustLogBo.class))).thenAnswer(invocation -> {
            IpAdjustLogBo log = (IpAdjustLogBo) invocation.getArguments()[0];
            log.setId(9001L);
            return 1;
        });
        when(securityPoolAdjustMapper.addPoolStatus(any(IpAdjustLogBo.class))).thenReturn(1);
        when(securityPoolAdjustMapper.deleteUnprotectedPoolStatusSoft("BOND001.IB", 3L)).thenReturn(1);

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
        ArgumentCaptor<AdjustSecuritySnapshotBo> snapshot = ArgumentCaptor.forClass(AdjustSecuritySnapshotBo.class);
        verify(securityPoolAdjustMapper).addAdjustSecuritySnapshot(snapshot.capture());
        assertThat(snapshot.getValue().getAdjustLogId()).isEqualTo(adjustLog.getAdjustLogId());
        assertThat(snapshot.getValue().getWindCode()).isEqualTo(adjustLog.getSecurityCode());
        assertThat(snapshot.getValue().getGuarantorId()).isEqualTo("C10008");
        assertThat(snapshot.getValue().getInnerGuarantorRating()).isEqualTo("2+");
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
        assertThat(result.getMessage()).contains("任务完成：扫描候选债券 2 条，实际入池 1 条，未处理 1 条")
                .contains("主体池债券禁止库（15）→债券池债券禁止库（15）：扫描 2 条，入池 1 条，未处理 1 条");
        assertThat(result.getDetailLog()).doesNotContain("批次号");
        ArgumentCaptor<CompanyBondTypeScopeBo> scopeCaptor = ArgumentCaptor.forClass(CompanyBondTypeScopeBo.class);
        verify(autoAdjustMapper).queryCompanyNewBondForAutoIn(eq(15L), eq(15L), scopeCaptor.capture());
        assertThat(scopeCaptor.getValue().isExcludeAbs()).isTrue();
        assertThat(scopeCaptor.getValue().getExcludedSecurityTypes()).isEmpty();
        verify(securityPoolAdjustMapper).deleteUnprotectedPoolStatusSoft("BOND001.IB", 3L);
        verify(checks).checkCompanyFollowBatch(anyList(), eq("20261119"), any());
        verify(securityPoolAdjustMapper, never()).queryPoolCurrentCount(15L);
        verify(securityPoolAdjustMapper, never()).querySecurityCurrentPoolIdList(any());
        verify(scheduledTaskMapper).queryTaskByCode(CompanyNewBondAutoInService.TASK_CODE);
    }

    /** 同池和跨池候选共用一次校验，业务失败跳过并记录原因 */
    @Test
    public void executeShouldValidateAllMappingsAndLogBlockedCandidates() {
        AutoAdjustMapper mapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
        CompanyNewBondAutoInService service = new CompanyNewBondAutoInService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", mapper);
        AutoAdjustTestSupport.bindExcelExclusion(service);
        when(mapper.queryDatabaseDate()).thenReturn(LocalDate.of(2026, 11, 18));
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", adjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", taskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, mapper);
        SysScheduledTaskBo config = new SysScheduledTaskBo();
        config.setParamJson("{\"poolIds\":[17],\"mappings\":[{\"companyInPoolId\":15,\"bondTargetPoolId\":17}]}");
        when(taskMapper.queryTaskByCode(CompanyNewBondAutoInService.TASK_CODE)).thenReturn(config);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(17L);
        pool.setStatus("enabled");
        pool.setMaxCapacity(1L);
        when(poolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool));

        ScheduledAdjustCandidateDto samePoolBond = new ScheduledAdjustCandidateDto();
        samePoolBond.setSecurityCode("BOND001.IB");
        samePoolBond.setIssuerCode("C001");
        ScheduledAdjustCandidateDto crossPoolBond = new ScheduledAdjustCandidateDto();
        crossPoolBond.setSecurityCode("BOND002.IB");
        crossPoolBond.setIssuerCode("C002");
        BatchSecurityCheckService checks = mock(BatchSecurityCheckService.class);
        ReflectionTestUtils.setField(service, "batchSecurityCheckService", checks);
        AdjustCheckDto.CheckResultItem inbound = checkedItem("BOND001.IB", 17L, "黑名单质押库", "调入");
        AdjustCheckDto.CheckResultItem blocked = checkedItem("BOND002.IB", 17L, "黑名单质押库", "调入");
        blocked.setCanAdjust(false);
        blocked.setFailReasons(Collections.singletonList("证券当前在调入限制池中"));
        when(checks.checkCompanyFollowBatch(anyList(), eq("20261118"), any()))
                .thenReturn(Arrays.asList(checkResult(inbound), checkResult(blocked)));
        when(mapper.queryCompanyNewBondForAutoIn(eq(17L), eq(17L), any(CompanyBondTypeScopeBo.class)))
                .thenReturn(Collections.singletonList(samePoolBond));
        when(mapper.queryCompanyNewBondForAutoIn(eq(15L), eq(17L), any(CompanyBondTypeScopeBo.class)))
                .thenReturn(Collections.singletonList(crossPoolBond));
        when(adjustMapper.addAdjustLog(any())).thenReturn(1);
        when(adjustMapper.addPoolStatus(any())).thenReturn(1);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        assertThat(result.getDetailLog()).contains("BOND002.IB", "证券当前在调入限制池中");
        assertThat(result.getMessage()).contains("证券当前在调入限制池中");
        verify(adjustMapper).addPoolStatus(any(IpAdjustLogBo.class));
        verify(checks).checkCompanyFollowBatch(anyList(), eq("20261118"), any());
    }

    /** 同券关联已入池的空结果不计未处理，只在对应目标池实际写入后才豁免 */
    @Test
    public void executeShouldExcludeAlreadyAppliedRelatedCandidatesFromSkippedCount() {
        AutoAdjustMapper mapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
        BatchSecurityCheckService checks = mock(BatchSecurityCheckService.class);
        CompanyNewBondAutoInService service = new CompanyNewBondAutoInService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", mapper);
        ExcelPoolExclusionMapper exclusions = AutoAdjustTestSupport.bindExcelExclusion(service);
        when(mapper.queryDatabaseDate()).thenReturn(LocalDate.of(2026, 11, 18));
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", adjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "batchSecurityCheckService", checks);
        AutoAdjustTestSupport.bindPoolScope(service, mapper);

        SysScheduledTaskBo config = new SysScheduledTaskBo();
        config.setParamJson("{\"poolIds\":[17,23,16]}");
        when(taskMapper.queryTaskByCode(CompanyNewBondAutoInService.TASK_CODE)).thenReturn(config);
        InvestmentPoolBo pledge = new InvestmentPoolBo();
        pledge.setId(17L);
        pledge.setPoolName("黑名单质押库");
        pledge.setStatus("enabled");
        InvestmentPoolBo restricted = new InvestmentPoolBo();
        restricted.setId(23L);
        restricted.setPoolName("重点观察名单");
        restricted.setStatus("enabled");
        InvestmentPoolBo observe = new InvestmentPoolBo();
        observe.setId(16L);
        observe.setPoolName("观察池");
        observe.setStatus("enabled");
        when(poolMapper.queryPoolList()).thenReturn(Arrays.asList(pledge, restricted, observe));

        ScheduledAdjustCandidateDto main = new ScheduledAdjustCandidateDto();
        main.setSecurityCode("128103.SZ");
        main.setIssuerCode("C10003");
        ScheduledAdjustCandidateDto related = new ScheduledAdjustCandidateDto();
        related.setSecurityCode("198103.SH");
        related.setIssuerCode("C10003");
        List<IpAdjustLogBo> actions = new ArrayList<>();
        for (long poolId : new long[]{16L, 17L, 23L}) {
            IpAdjustLogBo action = new IpAdjustLogBo();
            action.setId(poolId);
            action.setSecurityCode("C10003");
            action.setTargetPoolId(poolId);
            action.setAdjustMode("调入");
            actions.add(action);
        }
        when(exclusions.queryExcelCompanyActions(any(), eq(null))).thenReturn(actions);
        for (Long poolId : Arrays.asList(17L, 23L)) {
            when(mapper.queryCompanyNewBondForAutoIn(eq(poolId), eq(poolId), any(CompanyBondTypeScopeBo.class)))
                    .thenReturn(Arrays.asList(main, related));
        }
        when(mapper.queryCompanyNewBondForAutoIn(eq(16L), eq(16L), any(CompanyBondTypeScopeBo.class)))
                .thenReturn(Collections.singletonList(related));
        when(checks.checkCompanyFollowBatch(anyList(), eq("20261118"), any())).thenReturn(Arrays.asList(
                checkResult(checkedItem("128103.SZ", 17L, "黑名单质押库", "调入"),
                        checkedItem("198103.SH", 17L, "黑名单质押库", "调入")),
                checkResult(),
                checkResult(checkedItem("128103.SZ", 23L, "重点观察名单", "调入"),
                        checkedItem("198103.SH", 23L, "重点观察名单", "调入")),
                checkResult(), checkResult()));
        when(adjustMapper.addAdjustLog(any())).thenReturn(1);
        when(adjustMapper.addPoolStatus(any())).thenReturn(1);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(4);
        assertThat(result.getMessage()).contains("扫描候选债券 5 条，实际入池 4 条，未处理 1 条",
                "债券池黑名单质押库（17）：扫描 2 条，入池 2 条，未处理 0 条",
                "债券池重点观察名单（23）：扫描 2 条，入池 2 条，未处理 0 条");
        assertThat(result.getDetailLog()).contains("证券【198103.SH】池【观察池】：无可执行的调入项，本轮未写入目标池");
        verify(adjustMapper, times(4)).addPoolStatus(any(IpAdjustLogBo.class));
    }

    /** 拒绝负数和小数到期门槛 */
    @Test
    public void executeShouldRejectInvalidMinimumDaysToMaturity() {
        CompanyNewBondAutoInService service = new CompanyNewBondAutoInService();
        ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", taskMapper);
        SysScheduledTaskBo config = new SysScheduledTaskBo();
        when(taskMapper.queryTaskByCode(CompanyNewBondAutoInService.TASK_CODE)).thenReturn(config);

        for (String invalid : Arrays.asList("-1", "0.5")) {
            config.setParamJson("{\"minDaysToMaturity\":" + invalid + "}");
            ScheduledTaskResult result = service.execute();
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("minDaysToMaturity 须为非负整数");
        }
    }

    @Test
    public void executeShouldDoNothingWhenNoMappingsExist() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        CompanyNewBondAutoInService service = AbsIdentityTestSupport.withIdentity(new CompanyNewBondAutoInService());
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        AutoAdjustTestSupport.bindExcelExclusion(service);
        when(autoAdjustMapper.queryDatabaseDate()).thenReturn(LocalDate.of(2026, 11, 18));
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);

        when(scheduledTaskMapper.queryTaskByCode(any(String.class))).thenReturn(null);

        ScheduledTaskResult result = service.execute();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isZero();
        verify(autoAdjustMapper, never()).queryCompanyNewBondForAutoIn(
                any(Long.class), any(Long.class), any(CompanyBondTypeScopeBo.class));
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
        assertThat(service.getParamHelp()).contains("JSON 对象", "minDaysToMaturity");
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
        AutoAdjustTestSupport.bindExcelExclusion(service);
        when(autoAdjustMapper.queryDatabaseDate()).thenReturn(LocalDate.of(2026, 11, 18));
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
        when(autoAdjustMapper.queryCompanyNewBondForAutoIn(eq(15L), eq(15L),
                any(CompanyBondTypeScopeBo.class))).thenReturn(Arrays.asList(bond1, bond2));
        when(securityPoolAdjustMapper.addAdjustLog(any(IpAdjustLogBo.class))).thenAnswer(invocation -> {
            IpAdjustLogBo log = (IpAdjustLogBo) invocation.getArguments()[0];
            log.setId(1L);
            return 1;
        });
        BatchSecurityCheckService checks = mock(BatchSecurityCheckService.class);
        ReflectionTestUtils.setField(service, "batchSecurityCheckService", checks);
        when(checks.checkCompanyFollowBatch(anyList(), eq("20261118"), any())).thenReturn(Arrays.asList(
                checkResult(checkedItem("B1", 15L, "债券禁止库", "调入")),
                checkResult(checkedItem("B2", 15L, "债券禁止库", "调入"))));
        when(securityPoolAdjustMapper.addPoolStatus(any(IpAdjustLogBo.class))).thenReturn(0).thenReturn(1);

        ScheduledTaskResult result = service.execute();
        verify(securityPoolAdjustMapper, times(1)).addAdjustLog(any(IpAdjustLogBo.class));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAffectedCount()).isZero();
    }

    /** 构建共用批量校验返回的一条调库项 */
    private AdjustCheckDto.CheckResultItem checkedItem(String code, Long poolId, String poolName, String mode) {
        AdjustCheckDto.CheckResultItem item = new AdjustCheckDto.CheckResultItem();
        item.setSecurityCode(code);
        item.setSecurityShortName("测试新债");
        item.setSecurityType("mtn");
        item.setPoolName(poolName);
        item.setPoolType("forbidden");
        item.setTargetPoolId(poolId);
        item.setAdjustMode(mode);
        item.setCanAdjust(true);
        return item;
    }

    /** 将调库项封装为同一主券的批量校验结果 */
    private AdjustCheckDto checkResult(AdjustCheckDto.CheckResultItem... items) {
        AdjustCheckDto result = new AdjustCheckDto();
        result.setItems(Arrays.asList(items));
        return result;
    }
}
