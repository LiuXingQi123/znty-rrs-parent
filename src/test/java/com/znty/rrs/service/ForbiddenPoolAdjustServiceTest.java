package com.znty.rrs.service;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustCheckReq;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustDto;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustReq;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustSubmitReq;
import com.znty.rrs.entity.securitypooladjust.PoolDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckContext;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.ForbiddenPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 禁投池主体调整服务测试。 */
public class ForbiddenPoolAdjustServiceTest {

    /** 验证主体详情通过一次汇总查询回填旗下债券数量。 */
    @Test
    public void queryCompanyDetailShouldFillCompanyBondCount() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        ForbiddenPoolAdjustDto company = buildCompany("C10001");
        ForbiddenPoolAdjustDto.CompanyBondCount count = new ForbiddenPoolAdjustDto.CompanyBondCount();
        count.setCompanyCode("C10001");
        count.setBondCount(3);
        when(mapper.queryCompanyDetail("C10001")).thenReturn(company);
        when(mapper.queryCompanyBondCountList(Collections.singletonList("C10001")))
                .thenReturn(Collections.singletonList(count));

        ForbiddenPoolAdjustReq req = new ForbiddenPoolAdjustReq();
        req.setCompanyCode("C10001");

        ForbiddenPoolAdjustDto result = service.queryCompanyDetail(req);

        assertThat(result.getCompanyBondCount()).isEqualTo(3);
        verify(mapper).queryCompanyBondCountList(Collections.singletonList("C10001"));
    }

    /** 验证主体调库基础信息从主体表读取，不再查询主体证券记录。 */
    @Test
    public void queryAdjustSecurityInfoShouldReadCompanyFromCompanyTable() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        SecurityInfoBo company = new SecurityInfoBo();
        company.setWindCode("C10001");
        company.setSecurityType("company");
        when(mapper.queryCompanySecurityBoByCode("C10001")).thenReturn(company);

        SecurityInfoBo result = ReflectionTestUtils.invokeMethod(service, "queryAdjustSecurityInfo", "C10001", "company");

        assertThat(result).isSameAs(company);
        verify(mapper).queryCompanySecurityBoByCode("C10001");
        verify(mapper, never()).querySecurityBoByCode("C10001");
    }

    /** 验证主体提交时固定写入 company 类型，不依赖前端传入类型。 */
    @Test
    public void convertCompanySubmitReqShouldUseFixedCompanySecurityType() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        ForbiddenPoolAdjustSubmitReq req = new ForbiddenPoolAdjustSubmitReq();
        req.setItems(Collections.<ForbiddenPoolAdjustSubmitReq.AdjustItem>emptyList());
        ForbiddenPoolAdjustDto company = buildCompany("C10001");

        SecurityPoolAdjustSubmitReq result = ReflectionTestUtils.invokeMethod(
                service, "convertCompanySubmitReq", req, company);

        assertThat(result.getSecurityType()).isEqualTo("company");
        assertThat(result.getSecurityCode()).isEqualTo("C10001");
    }

    /** 验证手工调库目标池超出 15、16、17 时直接拒绝。 */
    @Test(expected = BizException.class)
    public void checkCompanyAdjustShouldRejectPoolOutsideAllowedRange() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        when(mapper.queryCompanyDetail("C10001")).thenReturn(buildCompany("C10001"));
        when(mapper.queryCompanyBondCountList(Collections.singletonList("C10001")))
                .thenReturn(Collections.<ForbiddenPoolAdjustDto.CompanyBondCount>emptyList());
        ForbiddenPoolAdjustCheckReq.CheckItem item = new ForbiddenPoolAdjustCheckReq.CheckItem();
        item.setTargetPoolId(2L);
        item.setAdjustMode("调入");
        ForbiddenPoolAdjustCheckReq req = new ForbiddenPoolAdjustCheckReq();
        req.setCompanyCode("C10001");
        req.setItems(Collections.singletonList(item));

        service.checkCompanyAdjust(req);
    }

    /** 验证可调投资池仅返回禁投池、观察池和黑名单质押库。 */
    @Test
    public void queryCompanyAdjustPoolListShouldOnlyReturnConfiguredRiskPools() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        when(poolMapper.queryPoolByIdsList(anyListOf(Long.class)))
                .thenReturn(Arrays.asList(
                        buildPool(15L, "禁投池", "forbidden"),
                        buildPool(16L, "观察池", "observe"),
                        buildPool(17L, "黑名单质押库", "blacklist")));
        when(poolMapper.queryMutexRelationList()).thenReturn(Collections.emptyList());
        when(mapper.queryPoolCurrentCountList()).thenReturn(Collections.<PoolDto>emptyList());
        ForbiddenPoolAdjustReq req = new ForbiddenPoolAdjustReq();
        req.setCurrentUserId("1");

        List<PoolDto> result = service.queryCompanyAdjustPoolList(req);

        assertThat(result).extracting(PoolDto::getId).containsOnly(15L, 16L, 17L);
    }

    /** 验证主体调入仅同步 SQL 已筛选出的未到期非 ABS 债券。 */
    @Test
    public void syncCompanyBondsOnDirectShouldInsertOnlyActualBondChange() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        SecurityInfoBo newBond = buildBond("B002");
        when(mapper.queryCategoryTypeBySecurityType("company")).thenReturn("company");
        when(mapper.queryCompanyInboundBondForAutoList("C10001", 15L))
                .thenReturn(Collections.singletonList(newBond));
        when(mapper.addPoolStatus(any(IpAdjustLogBo.class))).thenReturn(1);
        doAnswer(invocation -> {
            IpAdjustLogBo log = (IpAdjustLogBo) invocation.getArguments()[0];
            log.setId(99L);
            return 1;
        }).when(mapper).addAdjustLog(any(IpAdjustLogBo.class));
        IpAdjustLogBo companyLog = new IpAdjustLogBo();
        companyLog.setSecurityCode("C10001");
        companyLog.setSecurityShortName("某公司");
        companyLog.setSecurityType("company");
        companyLog.setAdjustMode("调入");
        companyLog.setAdjustBatchNo("COMPANY202606281001");
        companyLog.setTargetPoolId(15L);
        companyLog.setTargetPoolName("禁投池");
        companyLog.setPoolType("forbidden");
        companyLog.setAdjusterId("1");
        companyLog.setAdjusterName("管理员");

        ReflectionTestUtils.invokeMethod(service, "syncCompanyBondsOnDirect", companyLog);

        ArgumentCaptor<IpAdjustLogBo> captor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(mapper).addAdjustLog(captor.capture());
        IpAdjustLogBo autoLog = captor.getValue();
        assertThat(autoLog.getSecurityCode()).isEqualTo("B002");
        assertThat(autoLog.getAdjustType()).isEqualTo("自动调整");
        assertThat(autoLog.getAdjustBatchNo()).isEqualTo(companyLog.getAdjustBatchNo());
        verify(mapper).addPoolStatus(autoLog);
        verify(mapper, never()).deletePoolStatusSoft(any(String.class), any(Long.class));
    }

    /** 验证同步债券调入写入数量异常时阻断整批事务。 */
    @Test(expected = BizException.class)
    public void syncCompanyBondsShouldFailWhenInboundInsertCountIsInvalid() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        when(mapper.queryCategoryTypeBySecurityType("company")).thenReturn("company");
        when(mapper.queryCompanyInboundBondForAutoList("C10001", 15L))
                .thenReturn(Collections.singletonList(buildBond("B002")));
        doAnswer(invocation -> {
            ((IpAdjustLogBo) invocation.getArguments()[0]).setId(99L);
            return 1;
        }).when(mapper).addAdjustLog(any(IpAdjustLogBo.class));
        when(mapper.addPoolStatus(any(IpAdjustLogBo.class))).thenReturn(0);

        IpAdjustLogBo companyLog = buildCompanyLog();
        ReflectionTestUtils.invokeMethod(service, "syncCompanyBondsOnDirect", companyLog);
    }

    /** 验证主体调入不受证券品种和市场配置拦截。 */
    @Test
    public void checkInConditionsShouldSkipVarietyAndMarketForCompany() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        InvestmentPoolBo pool = buildPool(15L, "禁投池", "forbidden");
        pool.setVarietyCodes("[\"bond\"]");
        pool.setMarketCodes("[\"SSE\"]");
        SecurityInfoBo company = new SecurityInfoBo();
        company.setWindCode("C10001");
        company.setSecurityType("company");
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setSecurityInfo(company);
        ctx.setTargetPool(pool);
        ctx.setCategoryType("company");
        ctx.setCurrentPoolIds(Collections.<Long>emptySet());
        ctx.setTargetPoolRelations(Collections.<String, List<Long>>emptyMap());
        ctx.setRequestInPoolIds(Collections.<Long>emptySet());
        ctx.setRequestOutPoolIds(Collections.<Long>emptySet());
        when(mapper.querySecurityInForbiddenPool("C10001")).thenReturn(false);

        List<String> failures = service.checkInConditions(ctx);

        assertThat(failures).isEmpty();
        verify(mapper, never()).queryCategoryTypeBySecurityType("company");
    }

    /** 禁投池链路报告必填校验：限制为 any 且无报告时应抛出异常。 */
    @Test
    public void checkReportRequiredShouldFailWhenAnyAndNoReport() {
        ForbiddenPoolAdjustService service = new ForbiddenPoolAdjustService();
        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        InvestmentPoolBo pool = buildPool(10L, "禁投池", "forbidden");
        try {
            ReflectionTestUtils.invokeMethod(service, "checkReportRequired", item, pool, "any", null);
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("要求研究报告");
            return;
        }
        throw new AssertionError("any 限制且无报告时应抛出异常");
    }

    /** 验证内部报告限制会调用统一附件来源校验。 */
    @Test
    public void checkReportRequiredShouldValidateInternalReportSources() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        SysAttachmentService attachmentService = mock(SysAttachmentService.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", attachmentService);
        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        item.setCreditReportSourceAttachmentIds(Collections.singletonList(100L));
        InvestmentPoolBo pool = buildPool(15L, "禁投池", "forbidden");

        ReflectionTestUtils.invokeMethod(service, "checkReportRequired", item, pool, "internal", "C10001");

        verify(attachmentService).validateCreditReportSources(Collections.singletonList(100L), true);
    }

    /** 构建服务并注入公共依赖。 */
    private ForbiddenPoolAdjustService buildService(ForbiddenPoolAdjustMapper mapper) {
        ForbiddenPoolAdjustService service = new ForbiddenPoolAdjustService();
        ReflectionTestUtils.setField(service, "forbiddenPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "investmentPoolService", mock(InvestmentPoolService.class));
        ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));
        return service;
    }

    /** 构建主体数据。 */
    private ForbiddenPoolAdjustDto buildCompany(String companyCode) {
        ForbiddenPoolAdjustDto company = new ForbiddenPoolAdjustDto();
        company.setCompanyCode(companyCode);
        company.setCompanyShortName("某公司");
        return company;
    }

    /** 构建投资池数据。 */
    private InvestmentPoolBo buildPool(Long id, String poolName, String poolType) {
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(id);
        pool.setPoolName(poolName);
        pool.setPoolType(poolType);
        pool.setStatus("enabled");
        pool.setIsDeleted(0);
        return pool;
    }

    /** 构建债券数据。 */
    private SecurityInfoBo buildBond(String windCode) {
        SecurityInfoBo bond = new SecurityInfoBo();
        bond.setWindCode(windCode);
        bond.setShortName(windCode + "简称");
        bond.setSecurityType("company_bond");
        return bond;
    }

    /** 构建已审批通过的主体调入日志。 */
    private IpAdjustLogBo buildCompanyLog() {
        IpAdjustLogBo companyLog = new IpAdjustLogBo();
        companyLog.setSecurityCode("C10001");
        companyLog.setSecurityShortName("某公司");
        companyLog.setSecurityType("company");
        companyLog.setAdjustMode("调入");
        companyLog.setAdjustBatchNo("COMPANY202606281001");
        companyLog.setTargetPoolId(15L);
        companyLog.setTargetPoolName("禁投池");
        companyLog.setPoolType("forbidden");
        companyLog.setAdjusterId("1");
        companyLog.setAdjusterName("管理员");
        return companyLog;
    }
}
