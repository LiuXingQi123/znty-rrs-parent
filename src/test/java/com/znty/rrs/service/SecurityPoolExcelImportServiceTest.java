package com.znty.rrs.service;


import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.bo.SysImpTmpBo;
import com.znty.rrs.entity.bo.SysImpTmpDetlBo;
import com.znty.rrs.entity.securitypoolexcelimport.SecurityPoolExcelImportDto;
import com.znty.rrs.entity.securitypoolexcelimport.SecurityPoolExcelImportReq;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.RelatedRatingCompanyDto;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.mapper.SecurityPoolExcelImportMapper;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Date;
import com.znty.rrs.entity.securitypoolexcelimport.SecurityPoolExcelImportCheckItemDto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 证券池 Excel 导入服务单元测试
 */
public class SecurityPoolExcelImportServiceTest {

    private SecurityPoolExcelImportService service;
    private SecurityPoolExcelImportMapper importMapper;
    private InvestmentPoolMapper investmentPoolMapper;
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;
    private SecurityPoolAdjustService securityPoolAdjustService;
    private ForbiddenPoolAdjustService forbiddenPoolAdjustService;

    @Before
    public void setUp() {
        service = new SecurityPoolExcelImportService();
        importMapper = mock(SecurityPoolExcelImportMapper.class);
        investmentPoolMapper = mock(InvestmentPoolMapper.class);
        securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        securityPoolAdjustService = mock(SecurityPoolAdjustService.class);
        forbiddenPoolAdjustService = mock(ForbiddenPoolAdjustService.class);
        ReflectionTestUtils.setField(service, "securityPoolExcelImportMapper", importMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustService", securityPoolAdjustService);
        ReflectionTestUtils.setField(service, "forbiddenPoolAdjustService", forbiddenPoolAdjustService);
    }

    @Test
    public void uploadExcel_MissingDirection_Throws() {
        SecurityPoolExcelImportReq req = new SecurityPoolExcelImportReq();
        req.setCurrentUserId("1");
        MockMultipartFile file = new MockMultipartFile("file", "a.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});
        try {
            service.uploadExcel(req, file);
            fail();
        } catch (BizException e) {
            assertEquals("调整方向必须为 in 或 out", e.getMessage());
        }
    }

    @Test
    public void cancelImport_AlreadySubmitted_Throws() {
        SysImpTmpBo batch = new SysImpTmpBo();
        batch.setImpId("IMP1");
        batch.setSaveRslt("1");
        when(importMapper.queryBatchByImpId("IMP1")).thenReturn(batch);

        SecurityPoolExcelImportReq req = new SecurityPoolExcelImportReq();
        req.setImpId("IMP1");
        try {
            service.cancelImport(req);
            fail();
        } catch (BizException e) {
            assertEquals("该批次已提交，不能取消", e.getMessage());
        }
        verify(importMapper, never()).deleteItemsByImpIdSoft(anyString());
    }

    /** 缺少服务器校验快照时拒绝证券 Excel 导入提交。 */
    @Test
    public void submitImport_WhenCheckSnapshotMissing_Throws() {
        SysImpTmpBo batch = new SysImpTmpBo();
        batch.setImpId("IMP2");
        batch.setChkRslt("2");
        batch.setFailCount(1);
        batch.setSaveRslt("0");
        batch.setResultJson(null);
        when(importMapper.queryBatchByImpId("IMP2")).thenReturn(batch);

        SecurityPoolExcelImportReq req = new SecurityPoolExcelImportReq();
        req.setImpId("IMP2");
        req.setCurrentUserId("1");
        req.setAdjustReason("测试");
        try {
            service.submitImport(req);
            fail();
        } catch (BizException e) {
            assertEquals("请先校验，修改主体选择后需要重新校验", e.getMessage());
        }
    }

    @Test
    public void checkImport_PassWhenSecurityOkAndNotInPool() {
        SysImpTmpBo batch = new SysImpTmpBo();
        batch.setImpId("IMP3");
        batch.setFld001("in");
        batch.setSaveRslt("0");
        batch.setOpterId("1");
        batch.setOptionJson("{\"clearTarget\":false,\"allowLinkMutex\":false,\"importType\":\"security\"}");
        batch.setBizType("security_pool_excel");
        when(importMapper.queryBatchByImpId("IMP3")).thenReturn(batch);

        SysImpTmpDetlBo item = new SysImpTmpDetlBo();
        item.setId(10L);
        item.setImpId("IMP3");
        item.setFld001("110001.SH");
        item.setFld015("CN001");
        SecurityInfoBo master = new SecurityInfoBo();
        master.setIsinCode("CN001");
        master.setWindCodeSh("110001.SH");
        when(securityPoolAdjustMapper.querySecurityByMarketCodeList("110001.SH"))
                .thenReturn(Collections.singletonList(master));
        item.setFld002("测试债");
        item.setFld003("信用债大库");
        item.setFld004("一级库");
        item.setChkRslt("0");
        List<SysImpTmpDetlBo> items = new ArrayList<>();
        items.add(item);
        when(importMapper.queryBatchItemList("IMP3")).thenReturn(items);

        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(5L);
        pool.setPoolName("一级库");
        pool.setPoolType("credit_bond");
        pool.setLockFlag(0);
        when(importMapper.queryEnabledLeafPoolByParentAndChildName("信用债大库", "一级库"))
                .thenReturn(pool);

        AdjustCheckDto.CheckResultItem ri = new AdjustCheckDto.CheckResultItem();
        ri.setIsinCode("CN001");
        ri.setSecurityShortName("测试债");
        ri.setSecurityType("bond");
        ri.setSourceSecurityCode("CN001");
        ri.setTargetPoolId(5L);
        ri.setPoolName("信用债大库/一级库");
        ri.setPoolType("credit_bond");
        ri.setAdjustMode("调入");
        ri.setItemTag("manual");
        ri.setAdjustGroupKey("5_调入");
        ri.setCanAdjust(true);
        ri.setFailReasons(new ArrayList<>());
        ri.setFlowOptions(new ArrayList<>());
        AdjustCheckDto checkDto = new AdjustCheckDto();
        checkDto.setItems(Collections.singletonList(ri));
        when(securityPoolAdjustService.checkAdjust(any(AdjustCheckReq.class))).thenReturn(checkDto);

        when(importMapper.queryItemCountByCheckResult(eq("IMP3"), eq("0"))).thenReturn(0);
        batch.setPassCount(1);
        batch.setFailCount(0);
        batch.setChkRslt("1");
        batch.setTotalCount(1);

        SecurityPoolExcelImportReq req = new SecurityPoolExcelImportReq();
        req.setImpId("IMP3");
        req.setCurrentUserId("1");
        req.setPageIndex(1);
        req.setPageSize(20);
        when(importMapper.queryItemPage(anyString(), any(), any()))
                .thenReturn(Collections.<SysImpTmpDetlBo>emptyList());

        SecurityPoolExcelImportDto dto = service.checkImport(req);
        assertNotNull(dto);
        verify(securityPoolAdjustService).checkAdjust(any(AdjustCheckReq.class));
        verify(importMapper).editItemCheckResult(any(SysImpTmpDetlBo.class));
        verify(importMapper).editBatchCheckResult(any(SysImpTmpBo.class));
        verify(forbiddenPoolAdjustService, never()).checkCompanyAdjust(any());
    }

    /** Excel 导入应按证券类型默认选择首个关联评级主体。 */
    @Test
    public void applyDefaultRatingCompany_ShouldFillAbsRightsHolderAndNonAbsGuarantor() {
        SecurityInfoBo absSecurity = new SecurityInfoBo();
        absSecurity.setAbsFlag(1);
        when(securityPoolAdjustMapper.querySecurityBoByCode("ABS001.IB")).thenReturn(absSecurity);
        when(securityPoolAdjustMapper.queryRelatedRatingCompanyList("ABS001.IB"))
                .thenReturn(buildRelatedCompanies("RIGHTS_FIRST", "RIGHTS_SECOND"));

        AdjustCheckReq absReq = new AdjustCheckReq();
        absReq.setIsinCode("ABS001.IB");
        ReflectionTestUtils.invokeMethod(service, "applyDefaultRatingCompany", absReq);

        assertEquals("RIGHTS_FIRST", absReq.getRightsHolderCode());
        assertEquals(null, absReq.getGuarantorCode());

        SecurityInfoBo normalSecurity = new SecurityInfoBo();
        normalSecurity.setAbsFlag(0);
        when(securityPoolAdjustMapper.querySecurityBoByCode("BOND001.IB")).thenReturn(normalSecurity);
        when(securityPoolAdjustMapper.queryRelatedRatingCompanyList("BOND001.IB"))
                .thenReturn(buildRelatedCompanies("GUARANTOR_FIRST", "GUARANTOR_SECOND"));

        SecurityPoolAdjustSubmitReq normalReq = new SecurityPoolAdjustSubmitReq();
        normalReq.setIsinCode("BOND001.IB");
        ReflectionTestUtils.invokeMethod(service, "applyDefaultRatingCompany", normalReq);

        assertEquals("GUARANTOR_FIRST", normalReq.getGuarantorCode());
        assertEquals(null, normalReq.getRightsHolderCode());
    }

    /** 构造已按关联主体接口顺序返回的测试数据。 */
    private List<RelatedRatingCompanyDto> buildRelatedCompanies(String firstCode, String secondCode) {
        RelatedRatingCompanyDto first = new RelatedRatingCompanyDto();
        first.setCompanyCode(firstCode);
        RelatedRatingCompanyDto second = new RelatedRatingCompanyDto();
        second.setCompanyCode(secondCode);
        List<RelatedRatingCompanyDto> companies = new ArrayList<>();
        companies.add(first);
        companies.add(second);
        return companies;
    }

    @Test
    public void queryTask_NotFound_Throws() {
        when(importMapper.queryBatchByImpId("NOPE")).thenReturn(null);
        SecurityPoolExcelImportReq req = new SecurityPoolExcelImportReq();
        req.setImpId("NOPE");
        try {
            service.queryTask(req);
            fail();
        } catch (BizException e) {
            assertEquals("导入批次不存在或已取消", e.getMessage());
        }
    }
    /** 同一只券的沪市码和银行间码导入同池时，两行都报重复。 */
    @Test
    public void crossMarketRowsShouldBothFailForSameIdentityAndPool() {
        SecurityInfoBo master = new SecurityInfoBo();
        master.setIsinCode("CNMULTIMARKET001");
        when(securityPoolAdjustMapper.querySecurityByMarketCodeList("123456.SH"))
                .thenReturn(Collections.singletonList(master));
        when(securityPoolAdjustMapper.querySecurityByMarketCodeList("102400001.IB"))
                .thenReturn(Collections.singletonList(master));
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(5L); pool.setPoolName("一级库"); pool.setPoolType("credit_bond");
        when(importMapper.queryEnabledLeafPoolByParentAndChildName("信用债大库", "一级库"))
                .thenReturn(pool);
        List<SysImpTmpDetlBo> rows = new ArrayList<>();
        for (String code : new String[] {"123456.SH", "102400001.IB"}) {
            SysImpTmpDetlBo row = new SysImpTmpDetlBo();
            row.setFld001(code); row.setFld003("信用债大库"); row.setFld004("一级库");
            rows.add(row);
        }
        List<SecurityPoolExcelImportCheckItemDto> results = ReflectionTestUtils.invokeMethod(
                service, "checkSecurityImport", rows, true, false, "1", new Date());
        assertEquals(2, results.size());
        for (SecurityPoolExcelImportCheckItemDto result : results) {
            assertEquals(false, result.isCanAdjust());
            assertEquals(true, result.getFailReasons().toString().contains("同一ISIN"));
        }
        verify(securityPoolAdjustService, never()).checkAdjust(any());
    }

}
