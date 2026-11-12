package com.znty.rrs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.bo.SysImpTmpBo;
import com.znty.rrs.entity.bo.SysImpTmpDetlBo;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckDto;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckReq;
import com.znty.rrs.entity.crmwpooladjust.CrmwPoolAdjustSubmitReq;
import com.znty.rrs.entity.crmwpooladjust.SecurityInfoDetailDto;
import com.znty.rrs.entity.crmwpoolexcelimport.CrmwPoolExcelImportCheckItemDto;
import com.znty.rrs.entity.crmwpoolexcelimport.CrmwPoolExcelImportDto;
import com.znty.rrs.entity.crmwpoolexcelimport.CrmwPoolExcelImportReq;
import com.znty.rrs.entity.securitypooladjust.RatingCompanySelectionReq;
import com.znty.rrs.entity.securitypooladjust.RelatedRatingCompanyDto;
import com.znty.rrs.entity.securitypooladjust.SelfSelectedRightsHolderDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.CrmwPoolExcelImportMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    /** 标的证券及评级主体查询 */
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

    /** ABS 主体冲突检查 */
    private AbsIdentityService absIdentityService;

    /** 系统附件服务。 */
    private SysAttachmentService sysAttachmentService;

    /** 初始化待测试服务及其依赖。 */
    @Before
    public void setUp() {
        service = new CrmwPoolExcelImportService();
        SecurityMarketService marketService = mock(SecurityMarketService.class);
        when(marketService.queryMarketCode("CRMW001.IB")).thenReturn("NIB");
        when(marketService.queryMarketCode("XYB001.IB")).thenReturn("SSE");
        ReflectionTestUtils.setField(service, "securityMarketService", marketService);
        importMapper = mock(CrmwPoolExcelImportMapper.class);
        investmentPoolMapper = mock(InvestmentPoolMapper.class);
        crmwPoolAdjustService = mock(CrmwPoolAdjustService.class);
        sysAttachmentService = mock(SysAttachmentService.class);
        ReflectionTestUtils.setField(service, "crmwPoolExcelImportMapper", importMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "crmwPoolAdjustService", crmwPoolAdjustService);
        ReflectionTestUtils.setField(service, "sysAttachmentService", sysAttachmentService);
        securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        absIdentityService = mock(AbsIdentityService.class);
        when(absIdentityService.findRelated(anyString())).thenReturn(null);
        ReflectionTestUtils.setField(service, "absIdentityService", absIdentityService);
        when(importMapper.queryBatchIdForUpdate(anyString())).thenReturn(1L);
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
            assertEquals("请先完成导入校验", e.getMessage());
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
        item.setFld012("RELATED");
        item.setFld014("SELF");
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
        when(importMapper.querySecurityImportInfoByCode("CRMW001.IB")).thenReturn(crmw);

        SecurityInfoDetailDto security = new SecurityInfoDetailDto();
        security.setWindCode("110001.SH");
        security.setFullName("测试债");
        security.setShortName("测试债");
        security.setSecurityType("abs");
        security.setSecurityTypeName("债券");
        when(importMapper.querySecurityImportInfoByCode("110001.SH")).thenReturn(security);
        when(securityPoolAdjustMapper.querySecurityBoByCode("110001.SH")).thenReturn(security);
        RelatedRatingCompanyDto related = new RelatedRatingCompanyDto();
        related.setCompanyCode("RELATED");
        related.setCompanyName("关联主体");
        related.setInnerRating("3");
        when(securityPoolAdjustMapper.queryRelatedRatingCompanyList("110001.SH"))
                .thenReturn(Collections.singletonList(related));
        SelfSelectedRightsHolderDto self = new SelfSelectedRightsHolderDto();
        self.setCompanyCode("SELF");
        self.setCompanyName("自选主体");
        self.setInnerRating("2");
        when(securityPoolAdjustMapper.querySelfSelectedRightsHolderByCode("SELF")).thenReturn(self);

        AdjustCheckDto.CheckResultItem ri = new AdjustCheckDto.CheckResultItem();
        ri.setSecurityCode("110001.SH");
        ri.setSecurityShortName("测试债");
        ri.setSecurityType("abs");
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
        ri.setFailReasons(Collections.singletonList("目标投资池容量已满"));
        AdjustCheckDto.FlowOption flow = new AdjustCheckDto.FlowOption();
        flow.setFlowId(12L);
        flow.setFlowKey("crmw:normal");
        flow.setFlowType("normalInbound");
        flow.setSelectable(true);
        AdjustCheckDto.FlowOption batchFlow = new AdjustCheckDto.FlowOption();
        batchFlow.setFlowId(11L);
        batchFlow.setFlowKey("crmw:batch");
        batchFlow.setFlowType("batchInbound");
        batchFlow.setSelectable(true);
        batchFlow.setRecommended(true);
        ri.setFlowOptions(Arrays.asList(batchFlow, flow));
        AdjustCheckDto.CheckResultItem linkage = new AdjustCheckDto.CheckResultItem();
        linkage.setSecurityCode("110001.SH");
        linkage.setCrmwScode("CRMW001.IB");
        linkage.setSourceSecurityCode("CRMW001.IB|110001.SH");
        linkage.setTargetPoolId(19L);
        linkage.setPoolName("联动池");
        linkage.setAdjustMode("调入");
        linkage.setItemTag("linkage");
        linkage.setCanAdjust(true);
        linkage.setFailReasons(Collections.singletonList("联动池未开放"));
        AdjustCheckDto checkDto = new AdjustCheckDto();
        checkDto.setItems(Arrays.asList(ri, linkage));
        when(crmwPoolAdjustService.checkExcelAdjust(any(AdjustCheckReq.class))).thenReturn(checkDto);

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
        assertEquals("目标投资池容量已满", dto.getCheckItems().get(0).getFailReasons().get(0));
        ArgumentCaptor<AdjustCheckReq> check = ArgumentCaptor.forClass(AdjustCheckReq.class);
        verify(crmwPoolAdjustService).checkExcelAdjust(check.capture());
        assertEquals("RELATED", check.getValue().getRightsHolderCode());
        assertEquals("SELF", check.getValue().getSelectedRatingCompany().getCompanyCode());
        assertEquals("2", check.getValue().getSelectedRatingCompany().getInnerRating());
        assertEquals("关联主体", dto.getCheckItems().get(0).getSelectedRatingCompany().getRightsHolderName());
        assertEquals("自选主体", dto.getCheckItems().get(0).getRatingCompanyName());
        assertEquals("SELF", dto.getCheckItems().get(0).getRatingCompanyCode());
        assertEquals("全部可提交，其中 2 条含调整提示", dto.getChkDscr());
        assertEquals("调整提示：目标投资池容量已满；联动池未开放", item.getChkDscr());
        assertEquals("联动池", dto.getCheckItems().get(1).getTargetPoolName());
        verify(importMapper).updateItemCheckResult(any(SysImpTmpDetlBo.class));
        verify(importMapper).updateBatchCheckResult(any(SysImpTmpBo.class));

        // 提交重新复核来源行，客户端只提供所选流程
        CrmwPoolExcelImportCheckItemDto submittedItem = dto.getCheckItems().get(0);
        assertEquals(Long.valueOf(11L), submittedItem.getFlowId());
        submittedItem.setFlowId(12L);
        submittedItem.setFlowKey("crmw:normal");
        submittedItem.setFlowType("normalInbound");
        submittedItem.setSelectedFlowKey("0_crmw:normal_1");
        submittedItem.getSelectedRatingCompany().setCompanyCode("CLIENT");
        req.setCheckItems(dto.getCheckItems());
        when(securityPoolAdjustMapper.queryRelatedRatingCompanyList("110001.SH")).thenReturn(Collections.emptyList());
        try {
            service.submitImport(req);
            fail();
        } catch (BizException e) {
            assertEquals("所选担保人/权益人不属于当前证券，请重新选择", e.getMessage());
        }
        verify(crmwPoolAdjustService, never()).submitAdjustLog(any(CrmwPoolAdjustSubmitReq.class), any(), any());

        when(securityPoolAdjustMapper.queryRelatedRatingCompanyList("110001.SH"))
                .thenReturn(Collections.singletonList(related));
        self.setInnerRating("5");
        CrmwPoolExcelImportDto submitted = service.submitImport(req);
        ArgumentCaptor<CrmwPoolAdjustSubmitReq> submit = ArgumentCaptor.forClass(CrmwPoolAdjustSubmitReq.class);
        verify(crmwPoolAdjustService).submitAdjustLog(submit.capture(), any(), any());
        assertEquals("SELF", submit.getValue().getSelectedRatingCompany().getCompanyCode());
        assertEquals("5", submit.getValue().getSelectedRatingCompany().getInnerRating());
        assertEquals("RELATED", submit.getValue().getRightsHolderCode());
        assertEquals("SELF", submit.getValue().getSelfSelectedRightsHolderCode());
        assertEquals(Long.valueOf(12L), submit.getValue().getItems().get(0).getFlowId());
        assertEquals(Long.valueOf(12L), submitted.getCheckItems().get(0).getFlowId());
        assertEquals("0_crmw:normal_1", submitted.getCheckItems().get(0).getSelectedFlowKey());
        assertEquals(Long.valueOf(11L), service.queryTask(req).getCheckItems().get(0).getFlowId());
        assertEquals("联动池未开放", submitted.getCheckItems().get(1).getFailReasons().get(0));
    }

    /** 只有主目标池权限时，展开项无权限会在创建调库记录前阻断整批 */
    @Test
    public void submitImportShouldRejectPoolWithoutExcelPermission() throws Exception {
        SysImpTmpBo batch = new SysImpTmpBo();
        batch.setImpId("IMP_PERMISSION");
        batch.setFld004("18");
        batch.setChkRslt("1");
        batch.setSaveRslt("0");
        CrmwPoolExcelImportCheckItemDto main = new CrmwPoolExcelImportCheckItemDto();
        main.setItemTag("manual");
        main.setTargetPoolId(18L);
        main.setCanAdjust(true);
        main.setSelectedRatingCompany(new RatingCompanySelectionReq());
        CrmwPoolExcelImportCheckItemDto expanded = new CrmwPoolExcelImportCheckItemDto();
        expanded.setItemTag("linkage");
        expanded.setTargetPoolId(19L);
        expanded.setCanAdjust(true);
        expanded.setSelectedRatingCompany(new RatingCompanySelectionReq());
        batch.setResultJson(new ObjectMapper().writeValueAsString(
                Collections.singletonMap("checkItems", Arrays.asList(main, expanded))));
        when(importMapper.queryByImpId(batch.getImpId())).thenReturn(batch);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(18L);
        pool.setPoolType("crmw");
        when(importMapper.queryEnabledLeafCrmwPoolById(18L)).thenReturn(pool);
        PoolPermissionBo permission = new PoolPermissionBo();
        permission.setPoolId(18L);
        permission.setHandlerType("user");
        permission.setHandlerId(2L);
        when(investmentPoolMapper.queryPermissionListByType("excel_importable"))
                .thenReturn(Collections.singletonList(permission));

        CrmwPoolExcelImportReq req = new CrmwPoolExcelImportReq();
        req.setImpId(batch.getImpId());
        req.setCurrentUserId("2");
        try {
            service.submitImport(req);
            fail();
        } catch (BizException e) {
            assertEquals("当前用户无权对该投资池进行 Excel 导入", e.getMessage());
        }
        verify(crmwPoolAdjustService, never()).submitAdjustLog(any(CrmwPoolAdjustSubmitReq.class), any(), any());
        verify(importMapper, never()).updateBatchSaveResult(any(SysImpTmpBo.class));
    }

    /** 同一 ABS 在同批选择不同主体时，所有展开项保留原提示并阻断 */
    @Test
    public void conflictingAbsSelectionsShouldKeepAndBlockExpandedItems() {
        SysImpTmpBo batch = new SysImpTmpBo();
        batch.setImpId("IMP_CONFLICT");
        batch.setFld001("in");
        batch.setFld004("18");
        batch.setSaveRslt("0");
        when(importMapper.queryByImpId(batch.getImpId())).thenReturn(batch);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(18L);
        pool.setPoolType("crmw");
        when(importMapper.queryEnabledLeafCrmwPoolById(18L)).thenReturn(pool);
        SecurityInfoDetailDto abs = new SecurityInfoDetailDto();
        abs.setWindCode("ABS.IB");
        abs.setSecurityType("abs");
        when(importMapper.querySecurityImportInfoByCode("ABS.IB")).thenReturn(abs);
        when(securityPoolAdjustMapper.querySecurityBoByCode("ABS.IB")).thenReturn(abs);

        List<SysImpTmpDetlBo> items = new ArrayList<>();
        for (long id = 1; id <= 2; id++) {
            String certificateCode = "CERT" + id + ".IB";
            String companyCode = "SELF" + id;
            SysImpTmpDetlBo item = new SysImpTmpDetlBo();
            item.setId(id);
            item.setFld001(certificateCode);
            item.setFld004("ABS.IB");
            item.setFld014(companyCode);
            items.add(item);
            SecurityInfoDetailDto certificate = new SecurityInfoDetailDto();
            certificate.setWindCode(certificateCode);
            certificate.setSecurityType("crmw");
            when(importMapper.querySecurityImportInfoByCode(certificateCode)).thenReturn(certificate);
            SelfSelectedRightsHolderDto self = new SelfSelectedRightsHolderDto();
            self.setCompanyCode(companyCode);
            when(securityPoolAdjustMapper.querySelfSelectedRightsHolderByCode(companyCode)).thenReturn(self);
        }
        when(importMapper.queryAllByImpId(batch.getImpId())).thenReturn(items);
        String conflict = "同一 ABS[ABS.IB]在同批申请中选择了不同主体，请统一后重新校验";
        when(absIdentityService.queryBatchIdentityFailures(anyMap()))
                .thenReturn(Collections.singletonMap("ABS.IB", conflict));
        when(crmwPoolAdjustService.checkExcelAdjust(any(AdjustCheckReq.class))).thenAnswer(invocation -> {
            AdjustCheckReq request = invocation.getArgument(0);
            List<AdjustCheckDto.CheckResultItem> results = new ArrayList<>();
            long poolId = 18L;
            for (String tag : Arrays.asList("manual", "linkage", "mutex", "related")) {
                AdjustCheckDto.CheckResultItem result = new AdjustCheckDto.CheckResultItem();
                result.setCrmwScode(request.getCrmwScode());
                result.setSecurityCode("related".equals(tag) ? "ABS.SH" : request.getSecurityCode());
                result.setSourceSecurityCode(request.getCrmwScode() + "|ABS.IB");
                result.setTargetPoolId(poolId++);
                result.setAdjustMode("调入");
                result.setItemTag(tag);
                result.setCanAdjust(true);
                result.setFailReasons(Collections.singletonList("原调整提示"));
                results.add(result);
            }
            AdjustCheckDto response = new AdjustCheckDto();
            response.setItems(results);
            return response;
        });

        CrmwPoolExcelImportReq req = new CrmwPoolExcelImportReq();
        req.setImpId(batch.getImpId());
        req.setCurrentUserId("1");
        CrmwPoolExcelImportDto result = service.checkImport(req);
        assertEquals(8, result.getCheckItems().size());
        for (CrmwPoolExcelImportCheckItemDto row : result.getCheckItems()) {
            assertFalse(row.isCanAdjust());
            assertEquals(Arrays.asList("原调整提示", conflict), row.getFailReasons());
            assertTrue(row.getFlowOptions().isEmpty());
        }
        assertEquals("2", items.get(0).getChkRslt());
        assertEquals("2", items.get(1).getChkRslt());
    }

    /** 校验失败时仍展示来源行已保存的主体 */
    @Test
    public void failedCheckShouldShowSavedRatingCompany() {
        SysImpTmpDetlBo item = new SysImpTmpDetlBo();
        item.setFld012("RELATED");
        item.setFld013("关联主体");
        item.setFld014("SELF");
        item.setFld015("自选主体");
        InvestmentPoolBo pool = new InvestmentPoolBo();
        CrmwPoolExcelImportCheckItemDto result = ReflectionTestUtils.invokeMethod(service,
                "buildFailManualItem", item, pool, "调入", Collections.singletonList("主体身份冲突"));

        assertNotNull(result);
        assertEquals("自选主体", result.getRatingCompanyName());
        assertEquals("SELF", result.getRatingCompanyCode());
        assertNull(result.getSelectedRatingCompany());
    }

    /** 改选只保存当前行，并拒绝使用已清除的校验结果提交 */
    @Test
    public void editRatingCompanyShouldInvalidateOldCheckResults() {
        SysImpTmpBo batch = new SysImpTmpBo();
        batch.setImpId("IMP4");
        batch.setFld004("18");
        batch.setChkRslt("1");
        batch.setSaveRslt("0");
        batch.setResultJson("{\"checkItems\":[]}");
        when(importMapper.queryByImpId("IMP4")).thenReturn(batch);
        SysImpTmpDetlBo item = new SysImpTmpDetlBo();
        item.setId(9L);
        item.setImpId("IMP4");
        item.setFld004("ABS.IB");
        when(importMapper.queryItemById("IMP4", 9L)).thenReturn(item);
        when(importMapper.editRatingCompany(item)).thenReturn(1);
        SecurityInfoDetailDto security = new SecurityInfoDetailDto();
        security.setWindCode("ABS.IB");
        security.setSecurityType("abs");
        when(securityPoolAdjustMapper.querySecurityBoByCode("ABS.IB")).thenReturn(security);
        SelfSelectedRightsHolderDto self = new SelfSelectedRightsHolderDto();
        self.setCompanyCode("SELF");
        self.setCompanyName("自选主体");
        when(securityPoolAdjustMapper.querySelfSelectedRightsHolderByCode("SELF")).thenReturn(self);

        CrmwPoolExcelImportReq req = new CrmwPoolExcelImportReq();
        req.setImpId("IMP4");
        req.setItemId(9L);
        req.setCurrentUserId("1");
        req.setSelfSelectedRightsHolderCode("SELF");
        service.editRatingCompany(req);

        assertEquals("SELF", item.getFld014());
        assertEquals("自选主体", item.getFld015());
        verify(importMapper).editRatingCompany(item);
        verify(importMapper).resetItemCheckResults(eq("IMP4"), any());
        assertEquals("0", batch.getChkRslt());
        assertNull(batch.getResultJson());

        try {
            service.submitImport(req);
            fail();
        } catch (BizException e) {
            assertEquals("请先完成导入校验", e.getMessage());
        }
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
