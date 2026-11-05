package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.schedule.AbsBondCandidateDto;
import com.znty.rrs.entity.schedule.AbsCompanyRelationDto;
import com.znty.rrs.entity.schedule.PoolSecurityRowDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.AbsPoolReconcileMapper;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.time.format.DateTimeFormatter.BASIC_ISO_DATE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 主体下 ABS 债券自动入库（区分已确认发行人和未确认发行人）单元测试
 */
public class CompanyAbsBondAutoInServiceTest {

    /** 已确认发行人的 ABS：发行主体在池即自动入池 */
    @Test
    public void execute_ShouldAutoInConfirmedAbs() {
        Fixture fixture = new Fixture();
        fixture.companyInPool(15L, "C001");
        fixture.absMaster("ABS001", "C001", "测试集团");
        fixture.conf("{\"poolIds\":[15]}");
        fixture.allowSync(true);
        fixture.stubWrite();

        ScheduledTaskResult result = fixture.service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        assertThat(result.getMessage()).contains("自动入池 1 条 ABS");
        ArgumentCaptor<IpAdjustLogBo> captor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(fixture.securityPoolAdjustMapper).addAdjustLog(captor.capture());
        IpAdjustLogBo log = captor.getValue();
        assertThat(log.getSecurityCode()).isEqualTo("ABS001");
        assertThat(log.getAdjustMode()).isEqualTo(AdjustMode.IN.getCode());
        assertThat(log.getTargetPoolId()).isEqualTo(15L);
        assertThat(log.getAdjustReason()).contains("主体下ABS债券自动入库").contains("联动触发主体：C001");
        verify(fixture.securityPoolAdjustMapper).addPoolStatus(any(IpAdjustLogBo.class));
    }

    /** 未确认发行人的 ABS：只生成提醒，不写池状态 */
    @Test
    public void execute_ShouldOnlyRemindUnconfirmedAbs() {
        Fixture fixture = new Fixture();
        fixture.companyInPool(15L, "C001");
        fixture.absMaster("ABS002", null, null);
        fixture.relation("ABS002", "C001");
        fixture.conf("{\"poolIds\":[15]}");
        fixture.allowSync(false);

        ScheduledTaskResult result = fixture.service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(0);
        assertThat(result.getMessage()).contains("规则提醒 1 条");
        verify(fixture.securityPoolAdjustMapper, never()).addPoolStatus(any(IpAdjustLogBo.class));
        verify(fixture.securityPoolAdjustMapper, never()).addAdjustLog(any(IpAdjustLogBo.class));
    }

    /** 已确认发行人已离开依据池：ABS 自动出池 */
    @Test
    public void execute_ShouldAutoOutAbsWhenIssuerLeftPool() {
        Fixture fixture = new Fixture();
        fixture.companyInPool(15L, "C999");
        fixture.absMaster("ABS003", "C001", "已离池集团");
        fixture.absInPool(15L, "ABS003");
        fixture.conf("{\"poolIds\":[15]}");
        fixture.allowSync(true);
        when(fixture.securityPoolAdjustMapper.deletePoolStatusSoft("ABS003", 15L)).thenReturn(1);
        when(fixture.securityPoolAdjustMapper.addAdjustLog(any(IpAdjustLogBo.class))).thenReturn(1);

        ScheduledTaskResult result = fixture.service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        assertThat(result.getMessage()).contains("自动出池 1 条 ABS");
        ArgumentCaptor<IpAdjustLogBo> captor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(fixture.securityPoolAdjustMapper).addAdjustLog(captor.capture());
        assertThat(captor.getValue().getAdjustMode()).isEqualTo(AdjustMode.OUT.getCode());
        assertThat(captor.getValue().getAdjustReason()).contains("主体下ABS债券自动出池");
    }

    /** 距到期不足保护天数的 ABS 不再补入，其余池内记录不受影响 */
    @Test
    public void execute_ShouldSkipInWhenMaturityTooClose() {
        Fixture fixture = new Fixture();
        fixture.companyInPool(15L, "C001");
        fixture.absMaster("ABS031", "C001", "测试集团", LocalDate.now().plusDays(10).format(BASIC_ISO_DATE));
        fixture.conf("{\"poolIds\":[15],\"minDaysToMaturity\":30}");
        fixture.allowSync(true);

        ScheduledTaskResult result = fixture.service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(0);
        verify(fixture.securityPoolAdjustMapper, never()).addPoolStatus(any(IpAdjustLogBo.class));
    }

    /** 距到期仍超过保护天数时正常入池 */
    @Test
    public void execute_ShouldInWhenMaturityBeyondProtectDays() {
        Fixture fixture = new Fixture();
        fixture.companyInPool(15L, "C001");
        fixture.absMaster("ABS032", "C001", "测试集团", LocalDate.now().plusDays(40).format(BASIC_ISO_DATE));
        fixture.conf("{\"poolIds\":[15],\"minDaysToMaturity\":30}");
        fixture.allowSync(true);
        fixture.stubWrite();

        ScheduledTaskResult result = fixture.service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        verify(fixture.securityPoolAdjustMapper).addPoolStatus(any(IpAdjustLogBo.class));
    }

    /** 保护天数为默认 0 时，到期当天仍可入池 */
    @Test
    public void execute_ShouldInWhenMaturityIsTodayWithDefaultProtectDays() {
        Fixture fixture = new Fixture();
        fixture.companyInPool(15L, "C001");
        fixture.absMaster("ABS033", "C001", "测试集团", LocalDate.now().format(BASIC_ISO_DATE));
        fixture.conf("{\"poolIds\":[15]}");
        fixture.allowSync(true);
        fixture.stubWrite();

        ScheduledTaskResult result = fixture.service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        verify(fixture.securityPoolAdjustMapper).addPoolStatus(any(IpAdjustLogBo.class));
    }

    /** 出池开关关闭时只入不出 */
    @Test
    public void execute_ShouldSkipOutWhenDisabled() {
        Fixture fixture = new Fixture();
        fixture.companyInPool(15L, "C999");
        fixture.absMaster("ABS021", "C001", "已离池集团");
        fixture.absInPool(15L, "ABS021");
        fixture.conf("{\"poolIds\":[15],\"enableOut\":false}");

        ScheduledTaskResult result = fixture.service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(0);
        verify(fixture.securityPoolAdjustMapper, never()).deletePoolStatusSoft(anyString(), any());
    }

    /** 入池与出池同时关闭时本轮失败，避免误以为执行过 */
    @Test
    public void execute_ShouldFailWhenBothSwitchesClosed() {
        Fixture fixture = new Fixture();
        fixture.conf("{\"poolIds\":[15],\"enableIn\":false,\"enableOut\":false}");

        ScheduledTaskResult result = fixture.service.execute();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("入池与出池开关同时关闭");
    }

    /** 参数解析：开关默认开启、到期保护天数默认 0 */
    @Test
    public void parseParams_ShouldUseSafeDefaults() {
        CompanyAbsBondAutoInService service = new CompanyAbsBondAutoInService();
        CompanyAbsBondAutoInService.AbsSyncParams params = service.parseParams("{}");
        assertThat(params.enableIn).isTrue();
        assertThat(params.enableOut).isTrue();
        assertThat(params.minDaysToMaturity).isEqualTo(0);
        assertThat(params.poolIds).isEmpty();
        assertThat(params.mappings).isEmpty();
    }

    /** 参数解析：可单独关闭出池并指定到期保护天数 */
    @Test
    public void parseParams_ShouldReadSwitchesAndProtectDays() {
        CompanyAbsBondAutoInService service = new CompanyAbsBondAutoInService();
        CompanyAbsBondAutoInService.AbsSyncParams params = service.parseParams(
                "{\"poolIds\":[15,17],\"enableOut\":false,\"minDaysToMaturity\":20,"
                        + "\"mappings\":[{\"bondPoolId\":16,\"companyPoolId\":15}]}");
        assertThat(params.enableIn).isTrue();
        assertThat(params.enableOut).isFalse();
        assertThat(params.minDaysToMaturity).isEqualTo(20);
        assertThat(params.poolIds).containsExactly(15L, 17L);
        assertThat(params.mappings).hasSize(1);
        assertThat(params.mappings.get(0)).containsExactly(16L, 15L);
    }

    /** 参数解析：非法取值直接失败 */
    @Test
    public void parseParams_ShouldRejectInvalidValues() {
        CompanyAbsBondAutoInService service = new CompanyAbsBondAutoInService();
        assertThatThrownBy(() -> service.parseParams("{\"minDaysToMaturity\":-1}"))
                .isInstanceOf(BizException.class).hasMessageContaining("minDaysToMaturity");
        assertThatThrownBy(() -> service.parseParams("{\"enableIn\":\"yes\"}"))
                .isInstanceOf(BizException.class).hasMessageContaining("enableIn");
        assertThatThrownBy(() -> service.parseParams("[]"))
                .isInstanceOf(BizException.class).hasMessageContaining("JSON 对象");
    }

    /** 任务编码与配置一致 */
    @Test
    public void taskCodeShouldMatchConfig() {
        assertThat(new CompanyAbsBondAutoInService().getTaskCode()).isEqualTo("company_abs_bond_auto_in");
    }

    /** 服务装配与默认桩 */
    private static final class Fixture {

        /** 被测服务 */
        private final CompanyAbsBondAutoInService service = new CompanyAbsBondAutoInService();
        /** ABS 对账查询 */
        private final AbsPoolReconcileMapper absPoolReconcileMapper = mock(AbsPoolReconcileMapper.class);
        /** 调库落地 */
        private final SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        /** 自动调库查询 */
        private final AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        /** 投资池 */
        private final InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        /** 定时任务配置 */
        private final ScheduledTaskMapper scheduledTaskMapper = mock(ScheduledTaskMapper.class);
        /** ABS 联动判定 */
        private final AbsCompanySyncService absCompanySyncService = mock(AbsCompanySyncService.class);
        /** 市场校验 */
        private final SecurityMarketService securityMarketService = mock(SecurityMarketService.class);

        /** 主档行 */
        private final List<AbsBondCandidateDto> absRows = new ArrayList<>();
        /** 关系行 */
        private final List<AbsCompanyRelationDto> relationRows = new ArrayList<>();
        /** 池生效主体 */
        private final List<PoolSecurityRowDto> companyRows = new ArrayList<>();
        /** 池生效 ABS */
        private final List<PoolSecurityRowDto> absPoolRows = new ArrayList<>();
        /** 测试池 */
        private final InvestmentPoolBo pool = new InvestmentPoolBo();

        private Fixture() {
            pool.setId(15L);
            pool.setPoolName("债券禁止库");
            pool.setPoolType("forbidden");
            ReflectionTestUtils.setField(service, "absPoolReconcileMapper", absPoolReconcileMapper);
            ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
            ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
            ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
            ReflectionTestUtils.setField(service, "scheduledTaskMapper", scheduledTaskMapper);
            ReflectionTestUtils.setField(service, "absCompanySyncService", absCompanySyncService);
            ReflectionTestUtils.setField(service, "securityMarketService", securityMarketService);
            ReflectionTestUtils.setField(service, "transactionManager", mock(PlatformTransactionManager.class));
            AutoAdjustTestSupport.bindPoolScope(service, autoAdjustMapper);
            when(investmentPoolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool));
            when(securityPoolAdjustMapper.queryAllPoolRelationList()).thenReturn(Collections.emptyList());
            when(securityPoolAdjustMapper.querySecurityCurrentPoolIdList(anyString()))
                    .thenReturn(Collections.emptyList());
            when(securityMarketService.queryMarketCode(anyString())).thenReturn("SSE");
        }

        /** 配置任务参数 */
        private void conf(String paramJson) {
            SysScheduledTaskBo conf = new SysScheduledTaskBo();
            conf.setTaskName("主体下ABS债券自动入库");
            conf.setParamJson(paramJson);
            when(scheduledTaskMapper.queryTaskByCode(CompanyAbsBondAutoInService.TASK_CODE)).thenReturn(conf);
        }

        /** 指定池内存在生效主体 */
        private void companyInPool(Long poolId, String companyCode) {
            PoolSecurityRowDto row = new PoolSecurityRowDto();
            row.setPoolId(poolId);
            row.setSecurityCode(companyCode);
            companyRows.add(row);
            when(absPoolReconcileMapper.queryEffectiveCompanyPoolRows()).thenReturn(companyRows);
        }

        /** 指定 ABS 主档 */
        private void absMaster(String securityCode, String issuerCode, String issuerName) {
            absMaster(securityCode, issuerCode, issuerName, "20991231");
        }

        /** 指定到期日的 ABS 主档，用于到期保护用例 */
        private void absMaster(String securityCode, String issuerCode, String issuerName, String maturityDate) {
            AbsBondCandidateDto abs = new AbsBondCandidateDto();
            abs.setSecurityCode(securityCode);
            abs.setSecurityShortName(securityCode + "简称");
            abs.setSecurityType("abs");
            abs.setAbsFlag(1);
            abs.setIssuerCode(issuerCode);
            abs.setIssuerName(issuerName);
            abs.setMaturityDate(maturityDate);
            abs.setMarket("SSE");
            absRows.add(abs);
            when(absPoolReconcileMapper.queryAbsBondList()).thenReturn(absRows);
        }

        /** 指定 ABS 四类关联主体 */
        private void relation(String absCode, String companyCode) {
            AbsCompanyRelationDto row = new AbsCompanyRelationDto();
            row.setAbsCode(absCode);
            row.setCompanyCode(companyCode);
            relationRows.add(row);
            when(absPoolReconcileMapper.queryAbsCompanyRelationList()).thenReturn(relationRows);
        }

        /** 指定 ABS 已在池 */
        private void absInPool(Long poolId, String... securityCodes) {
            for (String securityCode : securityCodes) {
                PoolSecurityRowDto row = new PoolSecurityRowDto();
                row.setPoolId(poolId);
                row.setSecurityCode(securityCode);
                absPoolRows.add(row);
            }
            when(absPoolReconcileMapper.queryEffectiveAbsPoolRows()).thenReturn(absPoolRows);
        }

        /** 指定 ABS 联动判定结果 */
        private void allowSync(boolean allowed) {
            when(absCompanySyncService.allowScheduledSync(any(), any(), any(), any(), anyBoolean(), any()))
                    .thenReturn(allowed);
        }

        /** 写入成功的默认桩 */
        private void stubWrite() {
            when(securityPoolAdjustMapper.addAdjustLog(any(IpAdjustLogBo.class))).thenReturn(1);
            when(securityPoolAdjustMapper.addPoolStatus(any(IpAdjustLogBo.class))).thenReturn(1);
        }
    }
}
