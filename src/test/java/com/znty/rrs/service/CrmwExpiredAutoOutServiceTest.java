package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
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

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CRMW 到期自动出池单元测试
 */
public class CrmwExpiredAutoOutServiceTest {

    /** CRMW 每轮读取独立配置，凭证到期查询和调库原因按实际天数记录 */
    @Test
    public void execute_ShouldReloadConfiguredMaturityOffsetDays() {
        AutoAdjustMapper mapper = mock(AutoAdjustMapper.class);
        CrmwPoolAdjustMapper adjustMapper = mock(CrmwPoolAdjustMapper.class);
        InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
        CrmwExpiredAutoOutService service = new CrmwExpiredAutoOutService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "crmwPoolAdjustMapper", adjustMapper);
        ReflectionTestUtils.setField(service, "excelPoolExclusionService", mock(ExcelPoolExclusionService.class));
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", taskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, mapper);
        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("CRMW到期自动出池");
        when(taskMapper.queryTaskByCode(CrmwExpiredAutoOutService.TASK_CODE)).thenReturn(conf);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(18L);
        pool.setPoolName("CRMW库");
        pool.setPoolType("crmw");
        when(poolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool));
        when(mapper.deleteExpiredCrmwStatus(91L)).thenReturn(1);

        for (int days : new int[]{3, 0, -1}) {
            conf.setParamJson("{\"poolIds\":[18],\"maturityOffsetDays\":" + days + "}");
            ScheduledAdjustCandidateDto item = new ScheduledAdjustCandidateDto();
            item.setPoolStatusId(91L);
            item.setSecurityCode("B001");
            item.setCrmwScode("CRMW001");
            item.setCrmwStype("crmw");
            when(mapper.queryCrmwPoolByExpired(18L, days, Collections.emptyList())).thenReturn(Collections.singletonList(item));

            ScheduledTaskResult result = service.execute();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getAffectedCount()).isEqualTo(1);
            verify(mapper).queryCrmwPoolByExpired(18L, days, Collections.emptyList());
            assertThat(result.getDetailLog()).contains("maturityOffsetDays=" + days);
            assertThat(item.getAdjustReason()).contains("出池口径：凭证摘牌日早于当前日期减去 " + days + " 天");
        }
    }

    /** CRMW 日期偏移参数写错时，扫描前直接返回业务错误 */
    @Test
    public void execute_ShouldFailBeforeScanWhenMaturityOffsetInvalid() {
        AutoAdjustMapper mapper = mock(AutoAdjustMapper.class);
        ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
        CrmwExpiredAutoOutService service = new CrmwExpiredAutoOutService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", taskMapper);
        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setParamJson("{\"poolIds\":[18],\"maturityOffsetDays\":\"3\"}");
        when(taskMapper.queryTaskByCode(CrmwExpiredAutoOutService.TASK_CODE)).thenReturn(conf);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("maturityOffsetDays");
        verify(mapper, never()).queryCrmwPoolByExpired(any(Long.class), any(Integer.class), any());
    }

    @Test
    public void execute_ShouldOutExpiredCrmw() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        CrmwPoolAdjustMapper crmwPoolAdjustMapper = mock(CrmwPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        CrmwExpiredAutoOutService service = new CrmwExpiredAutoOutService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "crmwPoolAdjustMapper", crmwPoolAdjustMapper);
        ExcelPoolExclusionService exclusions = mock(ExcelPoolExclusionService.class);
        ReflectionTestUtils.setField(service, "excelPoolExclusionService", exclusions);
        when(exclusions.loadProtectedCrmwStatusIds(Collections.singletonList(18L)))
                .thenReturn(Collections.singleton(82L));
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);

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

        ScheduledAdjustCandidateDto item = new ScheduledAdjustCandidateDto();
        item.setPoolStatusId(81L);
        item.setSecurityCode("B001");
        item.setCrmwScode("CRMW001");
        item.setCrmwStype("crmw");
        item.setSecurityStatus("D");
        item.setDelistDate("20991231");
        ScheduledAdjustCandidateDto protectedItem = new ScheduledAdjustCandidateDto();
        protectedItem.setPoolStatusId(82L);
        protectedItem.setSecurityCode("B001");
        protectedItem.setCrmwScode("CRMW002");
        protectedItem.setCrmwStype("crmw");
        when(autoAdjustMapper.queryCrmwPoolByExpired(18L, 1, Collections.emptyList()))
                .thenReturn(Arrays.asList(item, protectedItem));
        when(autoAdjustMapper.deleteExpiredCrmwStatus(81L)).thenReturn(1);
        when(crmwPoolAdjustMapper.addAdjustLog(any(IpAdjustLogBo.class))).thenReturn(1);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        assertThat(result.getMessage()).contains("本轮共自动出池 1 条到期 CRMW 组合")
                .contains("目标池明细：CRMW库(18)：1 条 CRMW 组合");
        verify(autoAdjustMapper).deleteExpiredCrmwStatus(81L);
        verify(autoAdjustMapper, never()).deleteExpiredCrmwStatus(82L);
        verify(crmwPoolAdjustMapper).addAdjustLog(any(IpAdjustLogBo.class));
        assertThat(item.getAdjustMode()).isEqualTo(AdjustMode.OUT.getCode());
        assertThat(item.getAuditStatus()).isEqualTo(AuditStatus.APPROVED.getCode());
        assertThat(item.getAdjustReason())
                .isEqualTo("【任务名称：CRMW到期自动出池】（凭证摘牌日：2099-12-31；出池口径：凭证状态为 D（退市））");
        assertThat(item.getAdjustAdvice()).isNull();
        assertThat(item.getAdjustBatchNo()).matches("CRMW\\d{17}2001");
        assertThat(result.getDetailLog()).doesNotContain("批次号");
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
        ReflectionTestUtils.setField(service, "excelPoolExclusionService", mock(ExcelPoolExclusionService.class));
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
        AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);

        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskName("CRMW到期自动出池");
        conf.setParamJson("{\"poolIds\":[18]}");
        when(scheduledTaskMapper.queryTaskByCode(CrmwExpiredAutoOutService.TASK_CODE)).thenReturn(conf);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(18L);
        pool.setPoolName("CRMW库");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool));
        when(crmwPoolAdjustMapper.queryAllPoolRelationList()).thenReturn(Collections.<PoolRelationBo>emptyList());
        ScheduledAdjustCandidateDto item = new ScheduledAdjustCandidateDto();
        item.setPoolStatusId(91L);
        item.setSecurityCode("B001");
        item.setCrmwScode("CRMW001");
        item.setCrmwStype("crmw");
        when(autoAdjustMapper.queryCrmwPoolByExpired(18L, 1, Collections.emptyList())).thenReturn(Collections.singletonList(item));
        when(autoAdjustMapper.deleteExpiredCrmwStatus(91L)).thenReturn(0);

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
