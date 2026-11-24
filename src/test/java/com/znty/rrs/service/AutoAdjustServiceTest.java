package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.RelationType;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 到期证券自动出池任务单元测试
 */
public class AutoAdjustServiceTest {

    /** 每轮读取保存的天数，候选查询、过程日志和业务调库原因使用同一个值 */
    @Test
    public void autoOutExpiredShouldReloadConfiguredMaturityOffsetDays() {
        AutoAdjustMapper mapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
        AutoAdjustService service = new AutoAdjustService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", adjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", taskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, mapper);
        AutoAdjustTestSupport.bindExcelExclusion(service);
        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("到期证券自动出池");
        when(taskMapper.queryTaskByCode(AutoAdjustService.TASK_CODE)).thenReturn(conf);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(15L);
        pool.setPoolName("债券禁止库");
        when(poolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool));
        when(mapper.queryBoundPoolIds(AutoAdjustService.TASK_CODE, "auto_out"))
                .thenReturn(Collections.singletonList(15L));
        when(adjustMapper.deleteUnprotectedPoolStatusSoft("S001", 15L)).thenReturn(1);

        for (int days : new int[]{3, 0, -1}) {
            conf.setParamJson("{\"maturityOffsetDays\":" + days + "}");
            ScheduledAdjustCandidateDto item = new ScheduledAdjustCandidateDto();
            item.setSecurityCode("S001");
            when(mapper.queryPoolSecurityByExpired(15L, days, Collections.emptyList())).thenReturn(Collections.singletonList(item));

            ScheduledTaskResult result = service.execute();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getAffectedCount()).isEqualTo(1);
            verify(mapper).queryPoolSecurityByExpired(15L, days, Collections.emptyList());
            assertThat(result.getDetailLog()).contains("maturityOffsetDays=" + days);
            assertThat(item.getAdjustReason()).contains("出池口径：摘牌日早于当前日期减去 " + days + " 天");
        }
    }

    /** 日期偏移参数写错时，不应扫描候选或修改池状态 */
    @Test
    public void autoOutExpiredShouldFailBeforeScanWhenMaturityOffsetInvalid() {
        AutoAdjustMapper mapper = mock(AutoAdjustMapper.class);
        ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
        AutoAdjustService service = new AutoAdjustService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", taskMapper);
        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setParamJson("{\"poolIds\":[15],\"maturityOffsetDays\":1.5}");
        when(taskMapper.queryTaskByCode(AutoAdjustService.TASK_CODE)).thenReturn(conf);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("maturityOffsetDays");
        verify(mapper, never()).queryPoolSecurityByExpired(any(Long.class), any(Integer.class), any());
    }

    @Test
    public void autoOutExpiredShouldDeleteExpiredCrmwInOrdinaryPool() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        AutoAdjustService service = new AutoAdjustService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);
        AutoAdjustTestSupport.bindExcelExclusion(service);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("到期证券自动出池");
        conf.setParamJson("{\"poolIds\":[10]}");
        when(scheduledTaskMapper.queryTaskByCode(AutoAdjustService.TASK_CODE)).thenReturn(conf);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(10L);
        pool.setPoolName("信用债大库");
        pool.setPoolType("credit_bond");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Arrays.asList(pool));
        ScheduledAdjustCandidateDto expired = new ScheduledAdjustCandidateDto();
        expired.setSecurityCode("S001");
        expired.setSecurityShortName("测试凭证");
        expired.setSecurityType("crmw");
        expired.setDelistDate("20260901");
        when(autoAdjustMapper.queryPoolSecurityByExpired(10L, 1, Collections.emptyList())).thenReturn(Arrays.asList(expired));
        when(securityPoolAdjustMapper.queryAllPoolRelationList()).thenReturn(Collections.<PoolRelationBo>emptyList());
        when(securityPoolAdjustMapper.deleteUnprotectedPoolStatusSoft("S001", 10L)).thenReturn(1);

        ScheduledTaskResult result = service.execute();

        ArgumentCaptor<IpAdjustLogBo> logCaptor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(securityPoolAdjustMapper).addAdjustLog(logCaptor.capture());
        verify(securityPoolAdjustMapper).deleteUnprotectedPoolStatusSoft("S001", 10L);
        IpAdjustLogBo log = logCaptor.getValue();
        assertThat(log.getSecurityType()).isEqualTo("crmw");
        assertThat(log.getAdjustType()).isEqualTo("自动调整");
        assertThat(log.getAdjustMode()).isEqualTo(AdjustMode.OUT.getCode());
        assertThat(log.getAuditStatus()).isEqualTo(AuditStatus.APPROVED.getCode());
        assertThat(log.getAdjustReason())
                .isEqualTo("【任务名称：到期证券自动出池】（摘牌日：2026-09-01；出池口径：摘牌日早于当前日期减去 1 天）");
        assertThat(log.getAdjustAdvice()).isNull();
        assertThat(log.getAdjustBatchNo()).matches("BOND\\d{17}2001");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        assertThat(result.getMessage()).contains("任务完成：扫描候选 1 条，实际出池 1 条，未处理 0 条")
                .contains("信用债大库（10）：扫描 1 条，出池 1 条，未处理 0 条");
        assertThat(result.getDetailLog()).contains("任务开始：到期证券自动出池")
                .contains("扫描条件：扫描 ip_pool_status 中已生效且未删除的债券（含 ABS、CRMW）")
                .contains("最终出池范围：先按在池记录 ID 剔除 Excel 保护项")
                .contains("扫描结果：池【信用债大库】到期命中 1 条，Excel 保护跳过 0 条")
                .contains("处理结果：池【信用债大库】实际出池 1 条，未处理 0 条")
                .contains("任务结束：任务完成：扫描候选 1 条");
        assertThat(result.getDetailLog()).doesNotContain("批次号");
        assertThat(result.getTaskName()).isEqualTo("到期证券自动出池");
        assertThat(service.getTaskCode()).isEqualTo(AutoAdjustService.TASK_CODE);
        assertThat(service.getTaskCode()).isEqualTo("security_expired_auto_out");
    }

    /** 同一任务内不同证券应分别生成 BOND 批次。 */
    @Test
    public void autoOutExpiredShouldSeparateBatchBySecurityCode() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        AutoAdjustService service = new AutoAdjustService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);
        AutoAdjustTestSupport.bindExcelExclusion(service);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("到期证券自动出池");
        conf.setParamJson("{\"poolIds\":[10]}");
        when(scheduledTaskMapper.queryTaskByCode(AutoAdjustService.TASK_CODE)).thenReturn(conf);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(10L);
        pool.setPoolName("信用债大库");
        pool.setPoolType("credit_bond");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool));
        ScheduledAdjustCandidateDto first = new ScheduledAdjustCandidateDto();
        first.setSecurityCode("S001");
        ScheduledAdjustCandidateDto second = new ScheduledAdjustCandidateDto();
        second.setSecurityCode("S002");
        when(autoAdjustMapper.queryPoolSecurityByExpired(10L, 1, Collections.emptyList())).thenReturn(Arrays.asList(first, second));
        when(securityPoolAdjustMapper.queryAllPoolRelationList())
                .thenReturn(Collections.<PoolRelationBo>emptyList());
        when(securityPoolAdjustMapper.deleteUnprotectedPoolStatusSoft(any(String.class), eq(10L))).thenReturn(1);

        ScheduledTaskResult result = service.execute();

        ArgumentCaptor<IpAdjustLogBo> captor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(securityPoolAdjustMapper, times(2)).addAdjustLog(captor.capture());
        List<IpAdjustLogBo> logs = captor.getAllValues();
        assertThat(logs.get(0).getAdjustBatchNo()).matches("BOND\\d{17}2001");
        assertThat(logs.get(1).getAdjustBatchNo()).matches("BOND\\d{17}2002");
        assertThat(logs.get(0).getAdjustBatchNo()).isNotEqualTo(logs.get(1).getAdjustBatchNo());
        assertThat(result.getAffectedCount()).isEqualTo(2);
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
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);
        AutoAdjustTestSupport.bindExcelExclusion(service);

        when(scheduledTaskMapper.queryTaskByCode(AutoAdjustService.TASK_CODE)).thenReturn(null);

        ScheduledTaskResult result = service.execute();

        verify(securityPoolAdjustMapper, never()).addAdjustLog(any(IpAdjustLogBo.class));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("未配置扫描池");
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
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);
        AutoAdjustTestSupport.bindExcelExclusion(service);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("到期证券自动出池");
        conf.setParamJson("{\"poolIds\":'123'}");
        when(scheduledTaskMapper.queryTaskByCode(AutoAdjustService.TASK_CODE)).thenReturn(conf);

        ScheduledTaskResult result = service.execute();

        verify(securityPoolAdjustMapper, never()).addAdjustLog(any(IpAdjustLogBo.class));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("JSON 解析失败");
    }

    @Test
    public void autoOutExpiredShouldSkipWhenSoftDeleteMisses() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        AutoAdjustService service = new AutoAdjustService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);
        AutoAdjustTestSupport.bindExcelExclusion(service);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("到期证券自动出池");
        conf.setParamJson("{\"poolIds\":[10]}");
        when(scheduledTaskMapper.queryTaskByCode(AutoAdjustService.TASK_CODE)).thenReturn(conf);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(10L);
        pool.setPoolName("信用债大库");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Arrays.asList(pool));
        ScheduledAdjustCandidateDto expired = new ScheduledAdjustCandidateDto();
        expired.setSecurityCode("S001");
        when(autoAdjustMapper.queryPoolSecurityByExpired(10L, 1, Collections.emptyList())).thenReturn(Arrays.asList(expired));
        when(securityPoolAdjustMapper.queryAllPoolRelationList()).thenReturn(Collections.<PoolRelationBo>emptyList());
        when(securityPoolAdjustMapper.deleteUnprotectedPoolStatusSoft("S001", 10L)).thenReturn(0);

        ScheduledTaskResult result = service.execute();

        verify(securityPoolAdjustMapper, never()).addAdjustLog(any(IpAdjustLogBo.class));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(0);
        assertThat(result.getMessage()).contains("扫描候选 1 条，实际出池 0 条，未处理 1 条");
        assertThat(result.getDetailLog()).contains("未处理：证券【S001】：当前已不在目标池，本轮不处理");
    }

    /** 候选查询已标记调出限制时直接跳过，不逐只查所在池 */
    @Test
    public void autoOutExpiredShouldSkipWhenInOutRestrictPool() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        AutoAdjustService service = new AutoAdjustService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);
        AutoAdjustTestSupport.bindExcelExclusion(service);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("到期证券自动出池");
        conf.setParamJson("{\"poolIds\":[10]}");
        when(scheduledTaskMapper.queryTaskByCode(AutoAdjustService.TASK_CODE)).thenReturn(conf);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(10L);
        pool.setPoolName("信用债大库");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Arrays.asList(pool));
        ScheduledAdjustCandidateDto expired = new ScheduledAdjustCandidateDto();
        expired.setSecurityCode("S001");
        expired.setOutRestricted(true);
        when(autoAdjustMapper.queryPoolSecurityByExpired(10L, 1, Collections.singletonList(15L)))
                .thenReturn(Collections.singletonList(expired));
        PoolRelationBo restrict = new PoolRelationBo();
        restrict.setPoolId(10L);
        restrict.setRelationType(RelationType.OUT_RESTRICT.getCode());
        restrict.setRelationPoolId(15L);
        when(securityPoolAdjustMapper.queryAllPoolRelationList()).thenReturn(Arrays.asList(restrict));

        ScheduledTaskResult result = service.execute();

        verify(securityPoolAdjustMapper, never()).deleteUnprotectedPoolStatusSoft(any(String.class), any(Long.class));
        verify(securityPoolAdjustMapper, never()).addAdjustLog(any(IpAdjustLogBo.class));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(0);
        assertThat(result.getMessage()).contains("扫描候选 1 条，实际出池 0 条，未处理 1 条");
        assertThat(result.getDetailLog()).contains("未处理：证券【S001】：当前位于调出限制池，本轮不处理");
        verify(securityPoolAdjustMapper, never()).querySecurityCurrentPoolIdList(any(String.class));
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
        assertThat(service.getParamHelp()).contains("poolIds", "扫描 ip_pool_status", "含 ABS、CRMW", "剔除 Excel 保护项");
    }

    @Test
    public void autoOutExpiredShouldScanBoundPoolWhenParamEmpty() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        AutoAdjustService service = new AutoAdjustService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);
        AutoAdjustTestSupport.bindExcelExclusion(service);

        when(scheduledTaskMapper.queryTaskByCode(AutoAdjustService.TASK_CODE)).thenReturn(null);
        when(autoAdjustMapper.queryBoundPoolIds(AutoAdjustService.TASK_CODE, "auto_out"))
                .thenReturn(Arrays.asList(10L));
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(10L);
        pool.setPoolName("信用债大库");
        pool.setPoolType("credit_bond");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Arrays.asList(pool));
        ScheduledAdjustCandidateDto expired = new ScheduledAdjustCandidateDto();
        expired.setSecurityCode("S001");
        when(autoAdjustMapper.queryPoolSecurityByExpired(10L, 1, Collections.emptyList())).thenReturn(Arrays.asList(expired));
        when(securityPoolAdjustMapper.queryAllPoolRelationList()).thenReturn(Collections.<PoolRelationBo>emptyList());
        when(securityPoolAdjustMapper.deleteUnprotectedPoolStatusSoft("S001", 10L)).thenReturn(1);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        verify(autoAdjustMapper).queryPoolSecurityByExpired(10L, 1, Collections.emptyList());
    }

    @Test
    public void autoOutExpiredShouldUnionParamAndBoundPools() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        AutoAdjustService service = new AutoAdjustService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);
        ExcelPoolExclusionMapper exclusions = AutoAdjustTestSupport.bindExcelExclusion(service);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("到期证券自动出池");
        conf.setParamJson("{\"poolIds\":[10]}");
        when(scheduledTaskMapper.queryTaskByCode(AutoAdjustService.TASK_CODE)).thenReturn(conf);
        when(autoAdjustMapper.queryBoundPoolIds(AutoAdjustService.TASK_CODE, "auto_out"))
                .thenReturn(Arrays.asList(10L, 16L));
        InvestmentPoolBo pool10 = new InvestmentPoolBo();
        pool10.setId(10L);
        pool10.setPoolName("信用债大库");
        InvestmentPoolBo pool16 = new InvestmentPoolBo();
        pool16.setId(16L);
        pool16.setPoolName("观察池");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Arrays.asList(pool10, pool16));
        ScheduledAdjustCandidateDto protectedStatus = new ScheduledAdjustCandidateDto();
        protectedStatus.setSecurityCode("B001");
        protectedStatus.setPoolStatusId(100L);
        ScheduledAdjustCandidateDto ordinaryStatus = new ScheduledAdjustCandidateDto();
        ordinaryStatus.setSecurityCode("B001");
        ordinaryStatus.setPoolStatusId(101L);
        when(autoAdjustMapper.queryPoolSecurityByExpired(10L, 1, Collections.emptyList()))
                .thenReturn(Collections.singletonList(protectedStatus));
        when(autoAdjustMapper.queryPoolSecurityByExpired(16L, 1, Collections.emptyList()))
                .thenReturn(Collections.singletonList(ordinaryStatus));
        when(exclusions.queryExcelProtectedPoolStatusIds(Arrays.asList(10L, 16L), true))
                .thenReturn(Collections.singleton(100L));
        when(securityPoolAdjustMapper.deleteUnprotectedPoolStatusSoft("B001", 16L)).thenReturn(1);
        when(securityPoolAdjustMapper.queryAllPoolRelationList()).thenReturn(Collections.<PoolRelationBo>emptyList());

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        verify(exclusions).queryExcelProtectedPoolStatusIds(Arrays.asList(10L, 16L), true);
        verify(securityPoolAdjustMapper, never()).deleteUnprotectedPoolStatusSoft("B001", 10L);
        verify(securityPoolAdjustMapper).deleteUnprotectedPoolStatusSoft("B001", 16L);
        verify(autoAdjustMapper).queryPoolSecurityByExpired(10L, 1, Collections.emptyList());
        verify(autoAdjustMapper).queryPoolSecurityByExpired(16L, 1, Collections.emptyList());
    }
}
