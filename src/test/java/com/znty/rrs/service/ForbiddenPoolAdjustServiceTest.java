package com.znty.rrs.service;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustCheckReq;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustDto;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustReq;
import com.znty.rrs.entity.securitypooladjust.PoolDto;
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
        when(poolMapper.queryPoolByIdsList(org.mockito.Matchers.anyListOf(Long.class)))
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

    /** 验证主体直通调入仅同步尚未在目标池的旗下债券。 */
    @Test
    public void syncCompanyBondsOnDirectShouldInsertOnlyActualBondChange() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        SecurityInfoBo existingBond = buildBond("B001");
        SecurityInfoBo newBond = buildBond("B002");
        when(mapper.queryCategoryTypeBySecurityType("company")).thenReturn("company");
        when(mapper.queryCompanyBondForAutoList("C10001"))
                .thenReturn(Arrays.asList(existingBond, newBond));
        when(mapper.querySecurityCurrentPoolIdList("B001")).thenReturn(Collections.singletonList(15L));
        when(mapper.querySecurityCurrentPoolIdList("B002")).thenReturn(Collections.<Long>emptyList());
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

    /** 禁投池链路报告必填校验：限制为 any 且无报告时应抛出异常。 */
    @Test
    public void checkReportRequiredShouldFailWhenAnyAndNoReport() {
        ForbiddenPoolAdjustService service = new ForbiddenPoolAdjustService();
        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        InvestmentPoolBo pool = buildPool(10L, "禁投池", "forbidden");
        try {
            ReflectionTestUtils.invokeMethod(service, "checkReportRequired", item, pool, "any");
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("要求研究报告");
            return;
        }
        throw new AssertionError("any 限制且无报告时应抛出异常");
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
        company.setSecurityType("company");
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
}
