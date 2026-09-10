package com.znty.rrs.service;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SysImpTmpBo;
import com.znty.rrs.entity.bo.SysImpTmpDetlBo;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckDto;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckReq;
import com.znty.rrs.entity.crmwpooladjust.SecurityInfoDetailDto;
import com.znty.rrs.entity.crmwpoolexcelimport.CrmwPoolExcelImportDto;
import com.znty.rrs.entity.crmwpoolexcelimport.CrmwPoolExcelImportReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.CrmwPoolExcelImportMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
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
 * CRMW 池 Excel 导入服务单元测试
 */
public class CrmwPoolExcelImportServiceTest {

    /** 待测试的 CRMW 池 Excel 导入服务。 */
    private CrmwPoolExcelImportService service;

    /** CRMW 池 Excel 导入数据访问组件。 */
    private CrmwPoolExcelImportMapper importMapper;

    /** 投资池数据访问组件。 */
    private InvestmentPoolMapper investmentPoolMapper;

    /** CRMW 池调库服务。 */
    private CrmwPoolAdjustService crmwPoolAdjustService;

    /** 系统附件服务。 */
    private SysAttachmentService sysAttachmentService;

    /** 初始化待测试服务及其依赖。 */
    @Before
    public void setUp() {
        service = new CrmwPoolExcelImportService();
        importMapper = mock(CrmwPoolExcelImportMapper.class);
        investmentPoolMapper = mock(InvestmentPoolMapper.class);
        crmwPoolAdjustService = mock(CrmwPoolAdjustService.class);
        sysAttachmentService = mock(SysAttachmentService.class);
        ReflectionTestUtils.setField(service, "crmwPoolExcelImportMapper", importMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "crmwPoolAdjustService", crmwPoolAdjustService);
        ReflectionTestUtils.setField(service, "sysAttachmentService", sysAttachmentService);
    }

    /** 验证上传时缺少调整方向会被拒绝。 */
    @Test
    public void uploadExcelShouldThrowWhenDirectionMissing() {
        CrmwPoolExcelImportReq req = new CrmwPoolExcelImportReq();
        req.setCurrentUserId("1");
        req.setTargetPoolId(18L);
        MockMultipartFile file = new MockMultipartFile("file", "a.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});
        try {
            service.uploadExcel(req, file);
            fail();
        } catch (BizException e) {
            assertEquals("调整方向必须为 in 或 out", e.getMessage());
        }
    }

    /** 验证上传时缺少目标池会被拒绝。 */
    @Test
    public void uploadExcelShouldThrowWhenTargetPoolMissing() {
        CrmwPoolExcelImportReq req = new CrmwPoolExcelImportReq();
        req.setCurrentUserId("1");
        req.setDirection("in");
        MockMultipartFile file = new MockMultipartFile("file", "a.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});
        try {
            service.uploadExcel(req, file);
            fail();
        } catch (BizException e) {
            assertEquals("目标投资池 ID 不能为空", e.getMessage());
        }
    }

    /** 验证已提交的导入批次不能取消。 */
    @Test
    public void cancelImportShouldThrowWhenAlreadySubmitted() {
        SysImpTmpBo batch = new SysImpTmpBo();
        batch.setImpId("IMP1");
        batch.setSaveRslt("1");
        when(importMapper.queryByImpId("IMP1")).thenReturn(batch);

        CrmwPoolExcelImportReq req = new CrmwPoolExcelImportReq();
        req.setImpId("IMP1");
        try {
            service.cancelImport(req);
            fail();
        } catch (BizException e) {
            assertEquals("该批次已提交，不能取消", e.getMessage());
        }
        verify(importMapper, never()).deleteItemsByImpIdSoft(anyString());
    }

    /** 验证没有有效校验项时不能提交导入批次。 */
    @Test
    public void submitImportShouldThrowWhenCheckItemsMissing() {
        SysImpTmpBo batch = new SysImpTmpBo();
        batch.setImpId("IMP2");
        batch.setChkRslt("2");
        batch.setFailCount(1);
        batch.setSaveRslt("0");
        batch.setResultJson(null);
        when(importMapper.queryByImpId("IMP2")).thenReturn(batch);

        CrmwPoolExcelImportReq req = new CrmwPoolExcelImportReq();
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

    /** 验证凭证与标的主数据有效时校验通过并保存结果。 */
    @Test
    public void checkImportShouldPassWhenCombinationValid() {
        SysImpTmpBo batch = new SysImpTmpBo();
        batch.setImpId("IMP3");
        batch.setFld001("in");
        batch.setFld004("18");
        batch.setFld005("CRMW库");
        batch.setSaveRslt("0");
        batch.setOpterId("1");
        batch.setBizType("crmw_pool_excel");
        when(importMapper.queryByImpId("IMP3")).thenReturn(batch);

        SysImpTmpDetlBo item = new SysImpTmpDetlBo();
        item.setId(10L);
        item.setImpId("IMP3");
        item.setFld001("CRMW001.IB");
        item.setFld004("110001.SH");
        item.setChkRslt("0");
        List<SysImpTmpDetlBo> items = new ArrayList<>();
        items.add(item);
        when(importMapper.queryAllByImpId("IMP3")).thenReturn(items);

        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(18L);
        pool.setPoolName("CRMW库");
        pool.setPoolType("crmw");
        when(importMapper.queryEnabledLeafCrmwPoolById(18L)).thenReturn(pool);

        SecurityInfoDetailDto crmw = new SecurityInfoDetailDto();
        crmw.setWindCode("CRMW001.IB");
        crmw.setFullName("测试凭证");
        crmw.setSecurityType("crmw");
        crmw.setWindCodeNib("CRMW001.IB");
        when(importMapper.querySecurityImportInfoByCode("CRMW001.IB")).thenReturn(crmw);

        SecurityInfoDetailDto security = new SecurityInfoDetailDto();
        security.setWindCode("110001.SH");
        security.setFullName("测试债");
        security.setShortName("测试债");
        security.setSecurityType("bond");
        security.setSecurityTypeName("债券");
        security.setWindCodeSh("110001.SH");
        when(importMapper.querySecurityImportInfoByCode("110001.SH")).thenReturn(security);

        AdjustCheckDto.CheckResultItem ri = new AdjustCheckDto.CheckResultItem();
        ri.setSecurityCode("110001.SH");
        ri.setSecurityShortName("测试债");
        ri.setSecurityType("bond");
        ri.setCrmwScode("CRMW001.IB");
        ri.setCrmwName("测试凭证");
        ri.setCrmwStype("crmw");
        ri.setSourceSecurityCode("CRMW001.IB|110001.SH");
        ri.setTargetPoolId(18L);
        ri.setPoolName("CRMW库");
        ri.setPoolType("crmw");
        ri.setAdjustMode("调入");
        ri.setItemTag("manual");
        ri.setAdjustGroupKey("18_调入");
        ri.setCanAdjust(true);
        ri.setFailReasons(new ArrayList<>());
        ri.setFlowOptions(new ArrayList<>());
        AdjustCheckDto checkDto = new AdjustCheckDto();
        checkDto.setItems(Collections.singletonList(ri));
        when(crmwPoolAdjustService.checkCrmwAdjust(any(AdjustCheckReq.class))).thenReturn(checkDto);

        when(importMapper.countByChkRslt(eq("IMP3"), eq("0"))).thenReturn(0);
        when(importMapper.queryItemList(anyString(), any(), any()))
                .thenReturn(Collections.<SysImpTmpDetlBo>emptyList());

        CrmwPoolExcelImportReq req = new CrmwPoolExcelImportReq();
        req.setImpId("IMP3");
        req.setCurrentUserId("1");
        req.setPageIndex(1);
        req.setPageSize(20);

        CrmwPoolExcelImportDto dto = service.checkImport(req);
        assertNotNull(dto);
        verify(crmwPoolAdjustService).checkCrmwAdjust(any(AdjustCheckReq.class));
        verify(importMapper).updateItemCheckResult(any(SysImpTmpDetlBo.class));
        verify(importMapper).updateBatchCheckResult(any(SysImpTmpBo.class));
    }

    /** 验证查询不存在的导入批次会被拒绝。 */
    @Test
    public void queryTaskShouldThrowWhenBatchMissing() {
        when(importMapper.queryByImpId("NOPE")).thenReturn(null);
        CrmwPoolExcelImportReq req = new CrmwPoolExcelImportReq();
        req.setImpId("NOPE");
        try {
            service.queryTask(req);
            fail();
        } catch (BizException e) {
            assertEquals("导入批次不存在或已取消", e.getMessage());
        }
    }
}
