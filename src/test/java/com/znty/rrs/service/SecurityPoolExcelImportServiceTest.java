package com.znty.rrs.service;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SysImpTmpBatchBo;
import com.znty.rrs.entity.bo.SysImpTmpBo;
import com.znty.rrs.entity.securitypoolexcelimport.SecurityPoolExcelImportDto;
import com.znty.rrs.entity.securitypoolexcelimport.SecurityPoolExcelImportReq;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
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
        SysImpTmpBatchBo batch = new SysImpTmpBatchBo();
        batch.setImpId("IMP1");
        batch.setSaveRslt("1");
        when(importMapper.queryByImpId("IMP1")).thenReturn(batch);

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

    @Test
    public void submitImport_WhenNoCheckItems_Throws() {
        SysImpTmpBatchBo batch = new SysImpTmpBatchBo();
        batch.setImpId("IMP2");
        batch.setChkRslt("2");
        batch.setFailCount(1);
        batch.setSaveRslt("0");
        batch.setResultJson(null);
        when(importMapper.queryByImpId("IMP2")).thenReturn(batch);

        SecurityPoolExcelImportReq req = new SecurityPoolExcelImportReq();
        req.setImpId("IMP2");
        req.setCurrentUserId("1");
        req.setAdjustReason("测试");
        try {
            service.submitImport(req);
            fail();
        } catch (BizException e) {
            assertEquals("没有可提交的校验结果，请先校验", e.getMessage());
        }
    }

    @Test
    public void checkImport_PassWhenSecurityOkAndNotInPool() {
        SysImpTmpBatchBo batch = new SysImpTmpBatchBo();
        batch.setImpId("IMP3");
        batch.setBizMode("in");
        batch.setSaveRslt("0");
        batch.setOpterId("1");
        batch.setOptionJson("{\"clearTarget\":false,\"allowLinkMutex\":false,\"importType\":\"security\"}");
        batch.setBizType("security_pool_excel");
        when(importMapper.queryByImpId("IMP3")).thenReturn(batch);

        SysImpTmpBo item = new SysImpTmpBo();
        item.setId(10L);
        item.setImpId("IMP3");
        item.setFld001("110001.SH");
        item.setFld002("测试债");
        item.setFld003("信用债大库");
        item.setFld004("一级库");
        item.setChkRslt("0");
        List<SysImpTmpBo> items = new ArrayList<>();
        items.add(item);
        when(importMapper.queryAllByImpId("IMP3")).thenReturn(items);

        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(5L);
        pool.setPoolName("一级库");
        pool.setPoolType("credit_bond");
        pool.setLockFlag(0);
        when(importMapper.queryEnabledLeafPoolByParentAndChildName("信用债大库", "一级库"))
                .thenReturn(pool);

        AdjustCheckDto.CheckResultItem ri = new AdjustCheckDto.CheckResultItem();
        ri.setSecurityCode("110001.SH");
        ri.setSecurityShortName("测试债");
        ri.setSecurityType("bond");
        ri.setSourceSecurityCode("110001.SH");
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

        when(importMapper.countByChkRslt(eq("IMP3"), eq("0"))).thenReturn(0);
        batch.setPassCount(1);
        batch.setFailCount(0);
        batch.setChkRslt("1");
        batch.setTotalCount(1);

        SecurityPoolExcelImportReq req = new SecurityPoolExcelImportReq();
        req.setImpId("IMP3");
        req.setCurrentUserId("1");
        req.setPageIndex(1);
        req.setPageSize(20);
        when(importMapper.queryItemList(anyString(), any(), any()))
                .thenReturn(Collections.<SysImpTmpBo>emptyList());

        SecurityPoolExcelImportDto dto = service.checkImport(req);
        assertNotNull(dto);
        verify(securityPoolAdjustService).checkAdjust(any(AdjustCheckReq.class));
        verify(importMapper).updateItemCheckResult(any(SysImpTmpBo.class));
        verify(importMapper).updateBatchCheckResult(any(SysImpTmpBatchBo.class));
        verify(forbiddenPoolAdjustService, never()).checkCompanyAdjust(any());
    }

    @Test
    public void queryTask_NotFound_Throws() {
        when(importMapper.queryByImpId("NOPE")).thenReturn(null);
        SecurityPoolExcelImportReq req = new SecurityPoolExcelImportReq();
        req.setImpId("NOPE");
        try {
            service.queryTask(req);
            fail();
        } catch (BizException e) {
            assertEquals("导入批次不存在或已取消", e.getMessage());
        }
    }
}
