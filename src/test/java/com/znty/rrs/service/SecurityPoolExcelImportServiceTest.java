package com.znty.rrs.service;


import com.github.pagehelper.PageHelper;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.bo.SysImpTmpBo;
import com.znty.rrs.entity.bo.SysImpTmpDetlBo;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustCheckReq;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustSubmitReq;
import com.znty.rrs.entity.forbiddenpooladjust.CompanyAdjustCheckContext;
import com.znty.rrs.entity.securitypoolexcelimport.SecurityPoolExcelImportDto;
import com.znty.rrs.entity.securitypoolexcelimport.PoolMemberDto;
import com.znty.rrs.entity.securitypoolexcelimport.CompanyImportMatchDto;
import com.znty.rrs.entity.securitypoolexcelimport.SecurityPoolExcelImportReq;
import com.znty.rrs.entity.securitypoolexcelimport.SecurityPoolExcelImportCheckItemDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.BatchSecurityCheckData;
import com.znty.rrs.entity.securitypooladjust.AdjustSubmitDto;
import com.znty.rrs.entity.securitypooladjust.RelatedRatingCompanyDto;
import com.znty.rrs.entity.securitypooladjust.SelfSelectedRightsHolderDto;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.CommonMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolExcelImportMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Date;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

/**
 * 证券池 Excel 导入服务单元测试
 */
public class SecurityPoolExcelImportServiceTest {

    /** 清空出库与导入共用提示放行入口，保留原原因和批量流程 */
    @Test
    public void clearOutboundShouldUseExcelWarningCheckAndKeepReasons() {
        PoolMemberDto member = new PoolMemberDto();
        member.setTargetPoolId(100L);
        member.setSecurityCode("C1");
        member.setSecurityShortName("测试凭证");
        member.setSecurityType("crmw");
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(100L);
        pool.setPoolName("产品库");
        AdjustCheckDto.CheckResultItem item = new AdjustCheckDto.CheckResultItem();
        item.setSecurityCode("C1");
        item.setSecurityType("crmw");
        item.setTargetPoolId(100L);
        item.setAdjustMode("调出");
        item.setItemTag("manual");
        item.setCanAdjust(true);
        item.setFailReasons(Collections.singletonList("目标投资池已锁定"));
        AdjustCheckDto dto = new AdjustCheckDto();
        dto.setItems(Collections.singletonList(item));
        when(batchSecurityCheckService.checkExcelItem(any(), any())).thenReturn(dto);
        when(investmentPoolMapper.queryPoolByIdsList(Collections.singletonList(100L))).thenReturn(Collections.singletonList(pool));
        when(importMapper.queryPoolMemberList(Collections.singletonList(100L))).thenReturn(Collections.singletonList(member));
        SysImpTmpDetlBo imported = new SysImpTmpDetlBo();
        imported.setFld001("NEW");
        imported.setFld009("100");
        List<SecurityPoolExcelImportCheckItemDto> results = ReflectionTestUtils.invokeMethod(service,
                "checkClearTargetOutbound", Collections.singletonList(imported), Collections.singleton(100L), false, false, "1");
        assertEquals(1, results.size());
        assertTrue(results.get(0).isCanAdjust());
        assertTrue(results.get(0).getFailReasons().contains("目标投资池已锁定"));
        assertEquals("Excel清空", results.get(0).getAdjustType());
        verify(batchSecurityCheckService).checkExcelItem(any(), any());
    }

    /** 两种模板都清空名单外的主体和证券，并按实际对象提交出库 */
    @Test
    public void clearTargetShouldCheckAndSubmitBothMemberTypesForEitherTemplate() {
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(16L);
        pool.setBatchOutFlowId(20L);
        pool.setBatchOutFlowKey("pool:batch-out");
        when(investmentPoolMapper.queryPoolByIdsList(Collections.singletonList(16L))).thenReturn(Collections.singletonList(pool));
        PoolMemberDto company = new PoolMemberDto();
        company.setTargetPoolId(16L);
        company.setSecurityCode("OLD_COMPANY");
        company.setSecurityType("company");
        PoolMemberDto bond = new PoolMemberDto();
        bond.setTargetPoolId(16L);
        bond.setSecurityCode("OLD_BOND.IB");
        bond.setSecurityType("bond");
        PoolMemberDto listed = new PoolMemberDto();
        listed.setTargetPoolId(16L);
        listed.setSecurityCode("LISTED");
        when(importMapper.queryPoolMemberList(Collections.singletonList(16L))).thenReturn(Arrays.asList(company, bond, listed));
        AdjustCheckDto companyCheck = clearCheckResult(company);
        companyCheck.getItems().get(0).setFailReasons(Collections.singletonList("存在进行中的流程"));
        when(forbiddenPoolAdjustService.checkCompanyExcelAdjust(any(ForbiddenPoolAdjustCheckReq.class), any(CompanyAdjustCheckContext.class)))
                .thenReturn(companyCheck);
        when(batchSecurityCheckService.checkExcelItem(any(AdjustCheckReq.class), any())).thenReturn(clearCheckResult(bond));
        when(batchCompanyAdjustSubmitService.submit(anyList())).thenReturn(Collections.singletonList(101L));
        AdjustSubmitDto bondSubmit = new AdjustSubmitDto();
        bondSubmit.setLogIds(Collections.singletonList(102L));
        when(batchSecurityAdjustSubmitService.submit(anyList(), any(), eq(true)))
                .thenReturn(Collections.singletonList(bondSubmit));
        SysImpTmpDetlBo imported = new SysImpTmpDetlBo();
        imported.setFld001("LISTED");
        imported.setFld009("16");

        for (boolean companyImport : new boolean[]{false, true}) {
            listed.setSecurityType(companyImport ? "company" : "bond");
            imported.setFld001(companyImport ? "LISTED" : "listed");
            List<SecurityPoolExcelImportCheckItemDto> results = ReflectionTestUtils.invokeMethod(service,
                    "checkClearTargetOutbound", Collections.singletonList(imported), Collections.singleton(16L), companyImport, false, "1");
            assertEquals(2, results.size());
            assertEquals("OLD_COMPANY", results.get(0).getSecurityCode());
            assertTrue(results.get(0).isCanAdjust());
            assertEquals(Collections.singletonList("存在进行中的流程"), results.get(0).getFailReasons());
            assertEquals("OLD_BOND.IB", results.get(1).getSecurityCode());
            for (SecurityPoolExcelImportCheckItemDto result : results) {
                assertEquals("batchOutbound", result.getFlowType());
                assertEquals(Long.valueOf(20L), result.getFlowId());
                assertEquals(1L, result.getFlowOptions().stream()
                        .filter(option -> "batchOutbound".equals(option.getFlowType())).count());
            }
            List<Long> ids = ReflectionTestUtils.invokeMethod(service, "submitClearTargetOutbound",
                    results, new SysImpTmpBo(), "1", "测试用户");
            assertEquals(Arrays.asList(101L, 102L), ids);
        }

        ArgumentCaptor<List<ForbiddenPoolAdjustSubmitReq>> companyRequests = ArgumentCaptor.forClass(List.class);
        verify(batchCompanyAdjustSubmitService, times(2)).submit(companyRequests.capture());
        ArgumentCaptor<List<SecurityPoolAdjustSubmitReq>> bondRequests = ArgumentCaptor.forClass(List.class);
        verify(batchSecurityAdjustSubmitService, times(2)).submit(bondRequests.capture(), any(), eq(true));
        for (List<ForbiddenPoolAdjustSubmitReq> requests : companyRequests.getAllValues()) {
            assertEquals(1, requests.size());
            ForbiddenPoolAdjustSubmitReq request = requests.get(0);
            assertEquals("OLD_COMPANY", request.getCompanyCode());
            assertEquals("Excel清空", request.getAdjustType());
            assertEquals("调出", request.getItems().get(0).getAdjustMode());
            assertEquals("pool:batch-out", request.getItems().get(0).getFlowKey());
        }
        for (List<SecurityPoolAdjustSubmitReq> requests : bondRequests.getAllValues()) {
            assertEquals(1, requests.size());
            SecurityPoolAdjustSubmitReq request = requests.get(0);
            assertEquals("OLD_BOND.IB", request.getSecurityCode());
            assertEquals("调出", request.getItems().get(0).getAdjustMode());
        }
    }

    /** 只有可调入的 Excel 主项才能触发对应池的清空校验 */
    @Test
    public void clearCheckShouldSkipPoolsWithoutSubmittableInbound() {
        SysImpTmpDetlBo validRow = new SysImpTmpDetlBo();
        validRow.setFld001("NEW.IB");
        validRow.setFld009("16");
        SysImpTmpDetlBo failedRow = new SysImpTmpDetlBo();
        failedRow.setFld001("INVALID.IB");
        failedRow.setFld009("23");
        SecurityPoolExcelImportCheckItemDto valid = importAdjustItem("NEW.IB", 16L, "调入", "manual", 1L, "in16");
        SecurityPoolExcelImportCheckItemDto failed = importAdjustItem("INVALID.IB", 23L, "调入", "manual", 2L, "in23");
        failed.setCanAdjust(false);
        SecurityPoolExcelImportCheckItemDto linked = importAdjustItem("NEW.IB", 23L, "调入", "linkage", 1L, "in16");
        SecurityPoolExcelImportCheckItemDto outbound = importAdjustItem("OLD.IB", 23L, "调出", "manual", 3L, "out23");
        List<SysImpTmpDetlBo> rows = Arrays.asList(validRow, failedRow);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(16L);
        when(investmentPoolMapper.queryPoolByIdsList(Collections.singletonList(16L))).thenReturn(Collections.singletonList(pool));

        Set<Long> noInbound = ReflectionTestUtils.invokeMethod(service, "collectInboundPoolIds",
                Arrays.asList(failed, linked, outbound));
        List<SecurityPoolExcelImportCheckItemDto> noClear = ReflectionTestUtils.invokeMethod(service,
                "checkClearTargetOutbound", rows, noInbound, false, false, "1");
        assertTrue(noClear.isEmpty());
        verify(importMapper, never()).queryPoolMemberList(any());

        Set<Long> inbound = ReflectionTestUtils.invokeMethod(service, "collectInboundPoolIds",
                Arrays.asList(valid, failed, linked, outbound));
        ReflectionTestUtils.invokeMethod(service, "checkClearTargetOutbound", rows, inbound, false, false, "1");
        verify(importMapper).queryPoolMemberList(Collections.singletonList(16L));
        verify(importMapper, never()).queryPoolMemberList(Collections.singletonList(23L));
    }

    /** 只提交清空项时拒绝写库，其他池的调入也不能触发本池清空 */
    @Test
    public void submitImportShouldRequireInboundForEachClearedPool() {
        SysImpTmpBo batch = new SysImpTmpBo();
        batch.setImpId("CLEAR");
        batch.setFld001("in");
        batch.setChkRslt("1");
        batch.setSaveRslt("0");
        batch.setOpterId("1");
        batch.setOptionJson("{\"clearTarget\":true,\"importType\":\"security\"}");
        batch.setResultJson("{}");
        when(importMapper.queryBatchByImpId("CLEAR")).thenReturn(batch);
        SecurityPoolExcelImportReq req = new SecurityPoolExcelImportReq();
        req.setImpId("CLEAR");
        SecurityPoolExcelImportCheckItemDto clear16 = importAdjustItem("OLD.IB", 16L, "调出", "clear", null, "out16");
        SecurityPoolExcelImportCheckItemDto clear23 = importAdjustItem("OLD.IB", 23L, "调出", "clear", null, "out23");
        SecurityPoolExcelImportCheckItemDto linked23 = importAdjustItem("OLD.IB", 17L, "调出", "linkage", null, "out23");
        req.setCheckItems(Arrays.asList(clear16, clear23, linked23));

        try {
            service.submitImport(req);
            fail();
        } catch (BizException e) {
            assertEquals("没有可提交的导入项", e.getMessage());
        }
        verify(batchSecurityAdjustSubmitService, never()).submit(anyList(), any(), eq(true));

        SysImpTmpDetlBo source = new SysImpTmpDetlBo();
        source.setId(1L);
        source.setFld001("NEW.IB");
        source.setFld009("16");
        source.setChkRslt("1");
        when(importMapper.queryBatchItemList("CLEAR")).thenReturn(Collections.singletonList(source));
        SecurityPoolExcelImportCheckItemDto inbound = importAdjustItem("NEW.IB", 16L, "调入", "manual", 1L, "in16");
        req.setCheckItems(Arrays.asList(clear16, clear23, linked23, inbound));
        service.submitImport(req);

        ArgumentCaptor<List<SecurityPoolAdjustSubmitReq>> clearRequest = ArgumentCaptor.forClass(List.class);
        InOrder order = inOrder(batchSecurityAdjustSubmitService);
        order.verify(batchSecurityAdjustSubmitService).submit(clearRequest.capture(), any(), eq(true));
        order.verify(batchSecurityAdjustSubmitService).submit(anyList(), any(), eq(false));
        verify(importMapper).editItemSaveResultByIds(eq("CLEAR"), eq(Collections.singletonList(1L)), any(Date.class));
        verify(importMapper).queryBatchItemList("CLEAR");
        assertEquals(1, clearRequest.getValue().size());
        assertEquals(1, clearRequest.getValue().get(0).getItems().size());
        assertEquals(Long.valueOf(16L), clearRequest.getValue().get(0).getItems().get(0).getTargetPoolId());
    }

    private SecurityPoolExcelImportCheckItemDto importAdjustItem(String code, Long poolId, String mode,
                                                                 String tag, Long sourceId, String groupKey) {
        SecurityPoolExcelImportCheckItemDto item = new SecurityPoolExcelImportCheckItemDto();
        item.setSecurityCode(code);
        item.setSourceSecurityCode(code);
        item.setSecurityType("bond");
        item.setTargetPoolId(poolId);
        item.setAdjustMode(mode);
        item.setItemTag(tag);
        item.setSourceItemId(sourceId);
        item.setAdjustGroupKey(groupKey);
        item.setCanAdjust(true);
        return item;
    }

    private AdjustCheckDto clearCheckResult(PoolMemberDto member) {
        AdjustCheckDto.CheckResultItem item = new AdjustCheckDto.CheckResultItem();
        item.setSecurityCode(member.getSecurityCode());
        item.setSecurityType(member.getSecurityType());
        item.setTargetPoolId(16L);
        item.setAdjustMode("调出");
        item.setItemTag("manual");
        item.setCanAdjust(true);
        AdjustCheckDto dto = new AdjustCheckDto();
        dto.setItems(Collections.singletonList(item));
        return dto;
    }

    private SecurityPoolExcelImportService service;
    private SecurityPoolExcelImportMapper importMapper;
    private InvestmentPoolMapper investmentPoolMapper;
    private BatchSecurityAdjustSubmitService batchSecurityAdjustSubmitService;
    private BatchSecurityCheckService batchSecurityCheckService;
    private ForbiddenPoolAdjustService forbiddenPoolAdjustService;
    private BatchCompanyAdjustSubmitService batchCompanyAdjustSubmitService;
    private BatchCompanyAdjustCheckService batchCompanyAdjustCheckService;
    /** 模拟数据库按原条件返回的批量匹配结果 */
    private final Map<String, List<String>> companyMatches = new HashMap<>();

    @Before
    public void setUp() {
        service = AbsIdentityTestSupport.withIdentity(new SecurityPoolExcelImportService());
        importMapper = mock(SecurityPoolExcelImportMapper.class);
        investmentPoolMapper = mock(InvestmentPoolMapper.class);
        batchSecurityAdjustSubmitService = mock(BatchSecurityAdjustSubmitService.class);
        batchSecurityCheckService = mock(BatchSecurityCheckService.class);
        when(batchSecurityCheckService.loadExcelCheckData(anyList(), anyBoolean())).thenReturn(new BatchSecurityCheckData());
        when(batchSecurityCheckService.loadExcelRatingCandidates(anyList(), anyList())).thenReturn(new BatchSecurityCheckData());
        forbiddenPoolAdjustService = mock(ForbiddenPoolAdjustService.class);
        batchCompanyAdjustSubmitService = mock(BatchCompanyAdjustSubmitService.class);
        ReflectionTestUtils.setField(service, "batchCompanyAdjustSubmitService", batchCompanyAdjustSubmitService);
        ReflectionTestUtils.setField(service, "securityPoolExcelImportMapper", importMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "batchSecurityAdjustSubmitService", batchSecurityAdjustSubmitService);
        ReflectionTestUtils.setField(service, "batchSecurityCheckService", batchSecurityCheckService);
        ReflectionTestUtils.setField(service, "forbiddenPoolAdjustService", forbiddenPoolAdjustService);
        batchCompanyAdjustCheckService = mock(BatchCompanyAdjustCheckService.class);
        ReflectionTestUtils.setField(service, "batchCompanyAdjustCheckService", batchCompanyAdjustCheckService);
        when(batchCompanyAdjustCheckService.load(anyMap(), anySet(), anyBoolean())).thenAnswer(invocation ->
                new CompanyAdjustCheckContext(invocation.getArgument(0), Collections.emptyList(), Collections.emptyList()));
        when(batchCompanyAdjustCheckService.loadClear(anyList(), anySet())).thenReturn(
                new CompanyAdjustCheckContext(Collections.emptyMap(), Collections.emptyList(), Collections.emptyList()));
        when(importMapper.queryCompanyMatchList(anyInt(), anyList())).thenAnswer(invocation -> {
            int type = invocation.getArgument(0);
            List<String> values = invocation.getArgument(1);
            List<CompanyImportMatchDto> result = new ArrayList<>();
            for (String value : values) {
                for (String code : companyMatches.getOrDefault(type + "|" + value, Collections.emptyList())) {
                    CompanyImportMatchDto match = new CompanyImportMatchDto();
                    match.setLookupValue(value);
                    match.setCompanyCode(code);
                    match.setCompanyFullName("主体" + code + "有限公司");
                    match.setCompanyShortName("主体" + code);
                    result.add(match);
                }
            }
            return result;
        });
        companyMatches.put(1 + "|" + "C001", Collections.singletonList("C001"));
        companyMatches.put(1 + "|" + "C002", Collections.singletonList("C002"));
    }

    /** Mock Mapper 不消费 PageHelper 上下文，测试后主动清理。 */
    @After
    public void tearDown() {
        PageHelper.clearPage();
    }

    /** 3000 个主体的内部校验只需六次匹配查询，重校验重新读取，不跨请求复用。 */
    @Test
    public void checkCompany_ShouldBatchThousandsOfMatchesAndRefreshNextRequest() {
        List<SysImpTmpDetlBo> rows = new ArrayList<>();
        for (int i = 0; i < 3000; i++) {
            SysImpTmpDetlBo row = new SysImpTmpDetlBo();
            row.setId((long) i + 1);
            row.setFld005("CREDIT" + i);
            row.setFld001("INVALID" + i);
            row.setFld004("观察池");
            rows.add(row);
            companyMatches.put("0|CREDIT" + i, Collections.singletonList("COMP" + i));
        }
        // 仅上传限制为 2000 行；此处直接构造存储明细验证批量算法的规模与次数
        SecurityPoolExcelImportReq req = prepareCompanyCheck("out", rows);
        SecurityPoolExcelImportDto result = service.checkImport(req);
        assertEquals(3000, result.getPassCount().intValue());
        assertEquals(0, result.getPendingCount().intValue());
        assertEquals(3000L, result.getItems().getTotal());
        assertEquals(20, result.getItems().getRecords().size());
        verify(importMapper, never()).queryItemPage(anyString(), any(), any());
        verify(importMapper, never()).queryItemCountByCheckResult(anyString(), any());
        ArgumentCaptor<List<String>> chunks = ArgumentCaptor.forClass(List.class);
        verify(importMapper, times(6)).queryCompanyMatchList(eq(0), chunks.capture());
        for (List<String> chunk : chunks.getAllValues()) {
            assertEquals(500, chunk.size());
        }
        verify(importMapper, never()).queryCompanyMatchList(eq(1), anyList());
        verify(importMapper, never()).queryCompanyMatchList(eq(2), anyList());
        verify(batchCompanyAdjustCheckService, times(1)).load(anyMap(), anySet(), anyBoolean());
        companyMatches.put("0|CREDIT0", Collections.singletonList("CHANGED_COMP"));
        service.checkImport(req);
        assertEquals("CHANGED_COMP", rows.get(0).getFld001());
        verify(importMapper, times(12)).queryCompanyMatchList(eq(0), anyList());
        verify(batchCompanyAdjustCheckService, times(2)).load(anyMap(), anySet(), anyBoolean());
    }

    /** 1000 行明细分五次写入，并在批次摘要之前保存 */
    @Test
    public void checkCompany_ShouldWriteThousandRowsInFiveBatches() {
        List<SysImpTmpDetlBo> rows = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            String code = "COMP" + i;
            SysImpTmpDetlBo row = new SysImpTmpDetlBo();
            row.setId((long) i + 1);
            row.setFld001(code);
            row.setFld004("观察池");
            rows.add(row);
            companyMatches.put("1|" + code, Collections.singletonList(code));
        }
        SecurityPoolExcelImportReq req = prepareCompanyCheck("in", rows);

        SecurityPoolExcelImportDto result = service.checkImport(req);

        assertEquals(1000, result.getPassCount().intValue());
        InOrder writes = inOrder(importMapper);
        for (int i = 0; i < rows.size(); i += 200) {
            writes.verify(importMapper).editItemCheckResultList(
                    eq(req.getImpId()), eq(rows.subList(i, i + 200)), any(Date.class));
        }
        writes.verify(importMapper).editBatchCheckResult(any(SysImpTmpBo.class));
        verify(importMapper, times(5)).editItemCheckResultList(eq(req.getImpId()), anyList(), any(Date.class));
    }

    /** 相同目标池的权限拒绝在当前批次复用，不影响每行失败结果。 */
    @Test
    public void checkCompany_ShouldReusePoolPermissionDenialWithinRequest() {
        List<SysImpTmpDetlBo> rows = new ArrayList<>();
        for (String code : Arrays.asList("C001", "C002")) {
            SysImpTmpDetlBo row = new SysImpTmpDetlBo();
            row.setId((long) rows.size() + 1);
            row.setFld001(code);
            row.setFld004("观察池");
            rows.add(row);
        }
        SecurityPoolExcelImportReq req = prepareCompanyCheck("in", rows);
        req.setCurrentUserId("2");
        SecurityPoolExcelImportDto result = service.checkImport(req);
        assertEquals(2, result.getFailCount().intValue());
        assertTrue(rows.get(0).getChkDscr().contains("无权"));
        assertTrue(rows.get(1).getChkDscr().contains("无权"));
        verify(investmentPoolMapper, times(1)).queryUserRoleIdList(2L);
        verify(investmentPoolMapper, times(1)).queryPermissionListByType("excel_importable");
        verify(batchCompanyAdjustCheckService, never()).load(anyMap(), anySet(), anyBoolean());
    }

    /** 空代码按名称匹配并持久化，首尾空格不影响匹配，同名跨池只查询一次。 */
    @Test
    public void uploadCompany_ShouldResolveMissingCodeByName() throws Exception {
        companyMatches.put(2 + "|" + "测试主体", Collections.singletonList("C001"));
        // 使用真实 Excel 上传覆盖解析、匹配及写入流程
        List<SysImpTmpDetlBo> rows = uploadCompanyRows(new String[][]{
                {"", "观察池", " 测试主体 ", " "}, {"", "禁投池", "测试主体", ""}});

        assertEquals("C001", rows.get(0).getFld001());
        assertEquals("C001", rows.get(1).getFld001());
        assertEquals("0", rows.get(0).getChkRslt());
        verify(importMapper, times(1)).queryCompanyMatchList(eq(2), eq(Collections.singletonList("测试主体")));
    }

    /** 无匹配、重名和代码名称均为空时给出明确行级错误。 */
    @Test
    public void uploadCompany_ShouldExplainNameMatchingFailures() throws Exception {
        companyMatches.put(2 + "|" + "重名主体", Arrays.asList("C001", "C002"));
        // 上传三种不能唯一定位主体的行
        List<SysImpTmpDetlBo> rows = uploadCompanyRows(new String[][]{
                {"", "观察池", "不存在主体", ""}, {"", "观察池", "重名主体", ""},
                {"", "观察池", " ", ""}});

        assertTrue(rows.get(0).getChkDscr().contains("未找到主体"));
        assertEquals("主体全称[重名主体]匹配多个主体代码，请核对主体信息", rows.get(1).getChkDscr());
        assertEquals("统一社会信用代码、主体代码和主体全称均为空", rows.get(2).getChkDscr());
        for (SysImpTmpDetlBo row : rows) {
            assertEquals("2", row.getChkRslt());
        }
        verify(forbiddenPoolAdjustService, never()).checkCompanyExcelAdjust(any(), any());
    }

    /** 已填主体代码命中时优先采用，不再查询名称。 */
    @Test
    public void uploadCompany_ShouldKeepProvidedCode() throws Exception {
        // 代码命中后不因名称不同而改用其他主体
        List<SysImpTmpDetlBo> rows = uploadCompanyRows(new String[][]{
                {"", "观察池", "其他主体", "C001"}, {"", "观察池", "", "C002"}});

        assertEquals("C001", rows.get(0).getFld001());
        assertEquals("C002", rows.get(1).getFld001());
        verify(importMapper, never()).queryCompanyMatchList(eq(2), anyList());
    }

    /** 名称回填后仍识别与显式代码行的重复。 */
    @Test
    public void uploadCompany_ShouldDetectDuplicateAfterNameMatching() throws Exception {
        companyMatches.put(2 + "|" + "测试主体", Collections.singletonList("C001"));
        // 同一主体通过代码和名称分别导入同一子池
        List<SysImpTmpDetlBo> rows = uploadCompanyRows(new String[][]{
                {"", "观察池", "测试主体", "C001"}, {"", "观察池", "测试主体", ""}});

        assertEquals("C001", rows.get(1).getFld001());
        assertEquals("2", rows.get(1).getChkRslt());
        assertEquals("Excel 内相同主体代码与子池重复", rows.get(1).getChkDscr());
    }

    /** 调入和调出重新校验均能回填代码，复用完整主体规则并保存回填结果。 */
    @Test
    public void checkCompany_ShouldResolveMissingCodeForBothDirections() {
        companyMatches.put(2 + "|" + "测试主体", Collections.singletonList("C001"));
        for (String direction : Arrays.asList("in", "out")) {
            SysImpTmpDetlBo item = new SysImpTmpDetlBo();
            item.setId("in".equals(direction) ? 101L : 102L);
            item.setFld002("测试主体");
            item.setFld004("观察池");
            // 构造主体批次并使用完整服务的模拟结果
            SecurityPoolExcelImportReq req = prepareCompanyCheck(direction, Collections.singletonList(item));

            SecurityPoolExcelImportDto result = service.checkImport(req);

            assertEquals("C001", item.getFld001());
            assertEquals("1", item.getChkRslt());
            assertEquals(1, result.getPassCount().intValue());
            verify(importMapper).editItemCheckResultList(
                    eq(req.getImpId()), eq(Collections.singletonList(item)), any(Date.class));
        }
        verify(forbiddenPoolAdjustService, times(2)).checkCompanyExcelAdjust(any(), any());
    }

    /** 重新校验不能绕过名称歧义和重复检查。 */
    @Test
    public void checkCompany_ShouldRejectAmbiguousMissingAndDuplicateRows() {
        companyMatches.put(2 + "|" + "重名主体", Arrays.asList("C002", "C003"));
        companyMatches.put(2 + "|" + "测试主体", Collections.singletonList("C001"));
        List<SysImpTmpDetlBo> rows = new ArrayList<>();
        for (String name : Arrays.asList("测试主体", "测试主体", "重名主体", "不存在主体", "")) {
            SysImpTmpDetlBo row = new SysImpTmpDetlBo();
            row.setId((long) rows.size() + 1);
            row.setFld002(name);
            row.setFld004("观察池");
            rows.add(row);
        }
        // 先用显式代码导入，第二行名称匹配后应判重
        rows.get(0).setFld001("C001");
        SecurityPoolExcelImportReq req = prepareCompanyCheck("in", rows);

        service.checkImport(req);

        assertEquals("1", rows.get(0).getChkRslt());
        assertEquals("Excel 内相同主体代码与子池重复", rows.get(1).getChkDscr());
        assertTrue(rows.get(2).getChkDscr().contains("匹配多个主体代码"));
        assertTrue(rows.get(3).getChkDscr().contains("未找到主体"));
        assertEquals("统一社会信用代码、主体代码和主体全称均为空", rows.get(4).getChkDscr());
        verify(forbiddenPoolAdjustService, times(1)).checkCompanyExcelAdjust(any(), any());
        verify(importMapper).editItemCheckResultList(eq(req.getImpId()), eq(rows), any(Date.class));
    }

    /** 主体代码未命中时继续按名称查找，采用匹配后的主体代码校验。 */
    @Test
    public void checkCompany_ShouldFallbackFromMissingCodeToName() {
        SysImpTmpDetlBo item = new SysImpTmpDetlBo();
        item.setId(103L);
        item.setFld001("INVALID");
        item.setFld002("测试主体");
        item.setFld004("观察池");
        // 名称匹配成功后交给完整主体服务校验
        SecurityPoolExcelImportReq req = prepareCompanyCheck("in", Collections.singletonList(item));
        companyMatches.put(2 + "|" + "测试主体", Collections.singletonList("C001"));

        service.checkImport(req);

        assertEquals("C001", item.getFld001());
        assertEquals("1", item.getChkRslt());
        verify(importMapper).queryCompanyMatchList(eq(1), eq(Collections.singletonList("INVALID")));
        verify(importMapper).queryCompanyMatchList(eq(2), eq(Collections.singletonList("测试主体")));
    }

    /** 信用代码命中后优先采用，忽略冲突的主体代码和名称，并保留信用代码供预览。 */
    @Test
    public void uploadCompany_ShouldPreferCreditCode() throws Exception {
        companyMatches.put(0 + "|" + "91320100100010001B", Collections.singletonList("C001"));
        // 使用五列模板验证新列读取与优先级
        List<SysImpTmpDetlBo> rows = uploadCompanyRows(new String[][]{
                {"", "观察池", " 91320100100010001B ", "其他主体", "C002"}});
        assertEquals("C001", rows.get(0).getFld001());
        assertEquals("91320100100010001B", rows.get(0).getFld005());
        assertEquals("0", rows.get(0).getChkRslt());
        verify(importMapper, never()).queryCompanyMatchList(eq(1), anyList());
        verify(importMapper, never()).queryCompanyMatchList(eq(2), anyList());
    }

    /** 信用代码未命中则继续主体代码，主体代码命中后不查询名称。 */
    @Test
    public void uploadCompany_ShouldFallbackFromCreditToCode() throws Exception {
        // 信用代码不存在，但主体代码有效
        List<SysImpTmpDetlBo> rows = uploadCompanyRows(new String[][]{
                {"", "观察池", "NOT_FOUND", "其他主体", "C002"}});
        assertEquals("C002", rows.get(0).getFld001());
        assertEquals("0", rows.get(0).getChkRslt());
        verify(importMapper).queryCompanyMatchList(eq(0), eq(Collections.singletonList("NOT_FOUND")));
        verify(importMapper).queryCompanyMatchList(eq(1), eq(Collections.singletonList("C002")));
        verify(importMapper, never()).queryCompanyMatchList(eq(2), anyList());
    }

    /** 前两级未命中时按主体全称精确匹配。 */
    @Test
    public void uploadCompany_ShouldFallbackThroughAllThreeKeys() throws Exception {
        companyMatches.put(2 + "|" + "测试主体", Collections.singletonList("C001"));
        // 三个查询字段均填写，前两个不存在
        List<SysImpTmpDetlBo> rows = uploadCompanyRows(new String[][]{
                {"", "观察池", "NOT_FOUND", "测试主体", "INVALID"}});
        assertEquals("C001", rows.get(0).getFld001());
        assertEquals("0", rows.get(0).getChkRslt());
        verify(importMapper).queryCompanyMatchList(eq(0), eq(Collections.singletonList("NOT_FOUND")));
        verify(importMapper).queryCompanyMatchList(eq(1), eq(Collections.singletonList("INVALID")));
        verify(importMapper).queryCompanyMatchList(eq(2), eq(Collections.singletonList("测试主体")));
    }

    /** 信用代码异常一对多时明确报错，不使用低优先级条件掩盖数据问题。 */
    @Test
    public void uploadCompany_ShouldRejectAmbiguousCreditCode() throws Exception {
        companyMatches.put(0 + "|" + "DUPLICATE", Arrays.asList("C001", "C002"));
        // 即使主体代码有效，也不能绕过信用代码一对多错误
        List<SysImpTmpDetlBo> rows = uploadCompanyRows(new String[][]{
                {"", "观察池", "DUPLICATE", "测试主体", "C002"}});
        assertEquals("2", rows.get(0).getChkRslt());
        assertTrue(rows.get(0).getChkDscr().contains("统一社会信用代码[DUPLICATE]匹配多个主体代码"));
        verify(importMapper, never()).queryCompanyMatchList(eq(1), anyList());
        verify(importMapper, never()).queryCompanyMatchList(eq(2), anyList());
    }

    /** 三个条件全部未命中时按行保留失败原因。 */
    @Test
    public void uploadCompany_ShouldFailWhenAllKeysMiss() throws Exception {
        // 错误数据不阻断整个文件的预览
        List<SysImpTmpDetlBo> rows = uploadCompanyRows(new String[][]{
                {"", "观察池", "NOT_FOUND", "不存在主体", "INVALID"}});
        assertEquals("2", rows.get(0).getChkRslt());
        assertTrue(rows.get(0).getChkDscr().contains("均未匹配"));
    }

    /** 重新校验仍优先信用代码，并把最终主体代码交给完整调库服务。 */
    @Test
    public void checkCompany_ShouldUseCreditCodeBeforeProvidedCompanyCode() {
        SysImpTmpDetlBo item = new SysImpTmpDetlBo();
        item.setId(104L);
        item.setFld001("C002");
        item.setFld002("其他主体");
        item.setFld004("观察池");
        item.setFld005("91320100100010001B");
        companyMatches.put(0 + "|" + "91320100100010001B", Collections.singletonList("C001"));
        // 构造重新校验请求，完整服务应收到优先命中的主体
        SecurityPoolExcelImportReq req = prepareCompanyCheck("in", Collections.singletonList(item));
        SecurityPoolExcelImportDto result = service.checkImport(req);
        assertEquals("C001", item.getFld001());
        assertEquals("1", item.getChkRslt());
        assertEquals("91320100100010001B", result.getItems().getRecords().get(0).getUnifiedSocialCreditCode());
        verify(importMapper, never()).queryCompanyMatchList(eq(1), anyList());
        verify(importMapper, never()).queryCompanyMatchList(eq(2), anyList());
    }

    /** 证券上传必须填写父池，根池允许父池和子池填写同名 */
    @Test
    public void uploadSecurityShouldRequireParentPoolName() throws Exception {
        ReflectionTestUtils.setField(service, "commonMapper", mock(CommonMapper.class));
        List<SysImpTmpDetlBo> rows = uploadRows("security",
                new String[]{"父池名称", "子池名称", "证券名称", "证券代码"}, new String[][]{
                        {"", "CRMW库", "测试凭证一", "CRMW001.IB"},
                        {"CRMW库", "CRMW库", "测试凭证二", "CRMW002.IB"}
                });
        assertEquals("2", rows.get(0).getChkRslt());
        assertTrue(rows.get(0).getChkDscr().contains("父池名称为空"));
        assertEquals("0", rows.get(1).getChkRslt());
    }

    /** 同池证券代码仅大小写不同时，上传按重复处理 */
    @Test
    public void uploadSecurityShouldDetectDuplicateCodeIgnoringCase() throws Exception {
        ReflectionTestUtils.setField(service, "commonMapper", mock(CommonMapper.class));
        List<SysImpTmpDetlBo> rows = uploadRows("security",
                new String[]{"父池名称", "子池名称", "证券名称", "证券代码"}, new String[][]{
                        {"债券库", "一级库", "测试债", "110001.sh"},
                        {"债券库", "一级库", "测试债", "110001.SH"},
                        {"债券库", "二级库", "测试债", "110001.Sh"}
                });
        assertEquals("0", rows.get(0).getChkRslt());
        assertEquals("2", rows.get(1).getChkRslt());
        assertTrue(rows.get(1).getChkDscr().contains("相同证券代码与子池重复"));
        assertEquals("0", rows.get(2).getChkRslt());
    }

    /** 构造主体 Excel，并捕获上传写入的明细。 */
    private List<SysImpTmpDetlBo> uploadCompanyRows(String[][] values) throws Exception {
        String[] columns = values[0].length == 5
                ? new String[]{"父池名称", "子池名称", "统一社会信用代码", "主体全称", "主体代码"}
                : new String[]{"父池名称", "子池名称", "主体全称", "主体代码"};
        return uploadRows("company", columns, values);
    }

    /** 构造指定模板的 Excel，并捕获上传写入的明细 */
    private List<SysImpTmpDetlBo> uploadRows(String importType, String[] columns, String[][] values) throws Exception {
        List<SysImpTmpDetlBo> savedRows = new ArrayList<>();
        SysImpTmpBo[] savedBatch = new SysImpTmpBo[1];
        SysAttachmentService attachmentService = mock(SysAttachmentService.class);
        ReflectionTestUtils.setField(service, "sysAttachmentService", attachmentService);
        when(attachmentService.resolveUploadOriginalFileName(any(), any())).thenReturn("主体.xlsx");
        when(importMapper.addBatch(any())).thenAnswer(invocation -> {
            savedBatch[0] = invocation.getArgument(0);
            return 1;
        });
        when(importMapper.addItemList(anyList())).thenAnswer(invocation -> {
            savedRows.addAll(invocation.getArgument(0));
            return 1;
        });
        when(importMapper.queryBatchByImpId(anyString())).thenAnswer(invocation -> savedBatch[0]);
        when(importMapper.queryItemPage(anyString(), any(), any())).thenReturn(savedRows);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(importType);
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }
            for (int i = 0; i < values.length; i++) {
                Row row = sheet.createRow(i + 1);
                for (int j = 0; j < values[i].length; j++) {
                    row.createCell(j).setCellValue(values[i][j]);
                }
            }
            workbook.write(output);
            SecurityPoolExcelImportReq req = new SecurityPoolExcelImportReq();
            req.setImportType(importType);
            req.setDirection("in");
            req.setCurrentUserId("1");
            service.uploadExcel(req, new MockMultipartFile("file", "导入.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray()));
        }
        return savedRows;
    }

    /** 验证主体导入无需清空目标池即可推荐对应方向的批量流程 */
    @Test
    public void checkCompany_ShouldRecommendConfiguredBatchFlowAndKeepSingleFlow() {
        SysImpTmpDetlBo row = new SysImpTmpDetlBo();
        row.setId(1L);
        row.setRowNo(2);
        row.setFld001("C001");
        row.setFld004("观察池");

        for (String direction : Arrays.asList("in", "out")) {
            SecurityPoolExcelImportReq req = prepareCompanyCheck(direction, Collections.singletonList(row));
            InvestmentPoolBo pool = importMapper.queryEnabledLeafPoolByParentAndChildName(null, "观察池", false);
            boolean inbound = "in".equals(direction);
            String normalType = inbound ? "normalInbound" : "normalOutbound";
            String batchType = inbound ? "batchInbound" : "batchOutbound";

            SecurityPoolExcelImportCheckItemDto single = service.checkImport(req).getCheckItems().get(0);
            assertEquals(1, single.getFlowOptions().size());
            assertEquals(normalType, single.getFlowType());
            assertTrue(single.getFlowOptions().get(0).isRecommended());

            pool.setBatchInFlowId(20L);
            pool.setBatchOutFlowKey("pool:batch-out");
            SecurityPoolExcelImportCheckItemDto combined = service.checkImport(req).getCheckItems().get(0);
            assertEquals(2, combined.getFlowOptions().size());
            assertEquals(batchType, combined.getFlowType());
            assertEquals(inbound ? Long.valueOf(20L) : null, combined.getFlowId());
            assertEquals(inbound ? null : "pool:batch-out", combined.getFlowKey());
            assertTrue(combined.getFlowOptions().get(0).isRecommended());
            assertEquals(normalType, combined.getFlowOptions().get(1).getFlowType());
            assertTrue(combined.getFlowOptions().get(1).isSelectable());
            assertFalse(combined.getFlowOptions().get(1).isRecommended());
        }

        verify(importMapper, never()).queryPoolMemberList(anyList());
        verify(investmentPoolMapper, never()).queryPoolById(any());
    }

    /** 主体 Excel 名单内调入和调出均保留调整提示并按所选流程提交 */
    @Test
    public void companyImportShouldKeepWarningsAndSubmitBothDirections() {
        for (String direction : Arrays.asList("in", "out")) {
            boolean inbound = "in".equals(direction);
            SysImpTmpDetlBo row = new SysImpTmpDetlBo();
            row.setId(1L);
            row.setRowNo(2);
            row.setFld001("C001");
            row.setFld004("观察池");
            SecurityPoolExcelImportReq req = prepareCompanyCheck(direction, Collections.singletonList(row));
            InvestmentPoolBo pool = importMapper.queryEnabledLeafPoolByParentAndChildName(null, "观察池", false);
            pool.setBatchInFlowId(20L);
            pool.setBatchOutFlowId(21L);
            PoolMemberDto company = new PoolMemberDto();
            company.setSecurityCode("C001");
            company.setSecurityType("company");
            AdjustCheckDto checked = clearCheckResult(company);
            List<String> tips = Collections.singletonList("目标投资池已锁定");
            checked.getItems().get(0).setAdjustMode(inbound ? "调入" : "调出");
            checked.getItems().get(0).setFailReasons(tips);
            doReturn(checked).when(forbiddenPoolAdjustService)
                    .checkCompanyExcelAdjust(any(), any(CompanyAdjustCheckContext.class));
            when(batchCompanyAdjustSubmitService.submit(anyList())).thenReturn(Collections.singletonList(101L));

            SecurityPoolExcelImportDto dto = service.checkImport(req);
            assertEquals("1", row.getChkRslt());
            assertTrue(row.getChkDscr().contains("调整提示"));
            SecurityPoolExcelImportCheckItemDto choice = dto.getCheckItems().get(0);
            assertTrue(choice.isCanAdjust());
            assertEquals(tips, choice.getFailReasons());
            assertEquals(Long.valueOf(inbound ? 20L : 21L), choice.getFlowId());

            req.setCheckItems(dto.getCheckItems());
            choice.setTargetPoolId(23L);
            try {
                service.submitImport(req);
                fail("提交校验结果以外的目标池应失败");
            } catch (BizException ex) {
                assertTrue(ex.getMessage().contains("来源导入明细不一致"));
            }
            choice.setTargetPoolId(16L);
            SecurityPoolExcelImportDto submitted = service.submitImport(req);
            assertEquals(Collections.singletonList(101L), submitted.getLogIds());
            assertEquals(tips, submitted.getCheckItems().get(0).getFailReasons());
        }

        ArgumentCaptor<List<ForbiddenPoolAdjustSubmitReq>> requests = ArgumentCaptor.forClass(List.class);
        verify(batchCompanyAdjustSubmitService, times(2)).submit(requests.capture());
        for (List<ForbiddenPoolAdjustSubmitReq> batch : requests.getAllValues()) {
            assertEquals(1, batch.size());
            assertEquals("Excel导入", batch.get(0).getAdjustType());
        }
        assertEquals(Long.valueOf(20L), requests.getAllValues().get(0).get(0).getItems().get(0).getFlowId());
        assertEquals(Long.valueOf(21L), requests.getAllValues().get(1).get(0).getItems().get(0).getFlowId());
    }

    /** 单笔候选为空时，主体导入只使用已配置的批量流程 */
    @Test
    public void mapCompanyCheckResult_ShouldOnlyAddConfiguredBatchWhenSingleFlowsAreEmpty() {
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(16L);
        pool.setInFlowId(10L);
        AdjustCheckDto.CheckResultItem row = new AdjustCheckDto.CheckResultItem();
        row.setTargetPoolId(16L);
        row.setAdjustMode("调入");
        row.setItemTag("manual");
        row.setCanAdjust(true);
        row.setFlowOptions(Collections.emptyList());

        SecurityPoolExcelImportCheckItemDto withoutBatch = ReflectionTestUtils.invokeMethod(
                service, "mapCompanyCheckResult", null, row, "C001", pool);
        assertTrue(withoutBatch.getFlowOptions().isEmpty());

        pool.setBatchInFlowId(20L);
        SecurityPoolExcelImportCheckItemDto withBatch = ReflectionTestUtils.invokeMethod(
                service, "mapCompanyCheckResult", null, row, "C001", pool);
        assertEquals(1, withBatch.getFlowOptions().size());
        assertTrue(withBatch.getFlowOptions().get(0).isRecommended());
        assertEquals("batchInbound", withBatch.getFlowType());
        assertEquals(Long.valueOf(20L), withBatch.getFlowId());
    }

    /** 构造主体批次、池及完整主体校验返回结果 */
    private SecurityPoolExcelImportReq prepareCompanyCheck(String direction, List<SysImpTmpDetlBo> rows) {
        SysImpTmpBo batch = new SysImpTmpBo();
        batch.setImpId("COMPANY_" + direction);
        batch.setBizType("company_pool_excel");
        batch.setFld001(direction);
        batch.setSaveRslt("0");
        batch.setOptionJson("{\"clearTarget\":false,\"allowLinkMutex\":false,\"importType\":\"company\"}");
        when(importMapper.queryBatchByImpId(batch.getImpId())).thenReturn(batch);
        when(importMapper.queryBatchItemList(batch.getImpId())).thenReturn(rows);
        when(importMapper.queryItemPage(eq(batch.getImpId()), any(), any())).thenReturn(rows);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(16L);
        pool.setPoolName("观察池");
        pool.setPoolType("observe");
        when(importMapper.queryEnabledLeafPoolByParentAndChildName(null, "观察池", false)).thenReturn(pool);
        doAnswer(invocation -> {
            ForbiddenPoolAdjustCheckReq checkReq = invocation.getArgument(0);
            AdjustCheckDto.CheckResultItem result = new AdjustCheckDto.CheckResultItem();
            result.setSecurityCode(checkReq.getCompanyCode());
            result.setSecurityShortName("测试主体");
            result.setSecurityType("company");
            result.setTargetPoolId(16L);
            result.setAdjustMode(checkReq.getItems().get(0).getAdjustMode());
            result.setItemTag("manual");
            result.setCanAdjust(true);
            AdjustCheckDto.FlowOption normal = new AdjustCheckDto.FlowOption();
            normal.setFlowType("调入".equals(result.getAdjustMode()) ? "normalInbound" : "normalOutbound");
            normal.setFlowId(10L);
            normal.setSelectable(true);
            normal.setRecommended(true);
            result.setFlowOptions(Collections.singletonList(normal));
            AdjustCheckDto dto = new AdjustCheckDto();
            dto.setItems(Collections.singletonList(result));
            return dto;
        }).when(forbiddenPoolAdjustService).checkCompanyExcelAdjust(any(), any());
        SecurityPoolExcelImportReq req = new SecurityPoolExcelImportReq();
        req.setImpId(batch.getImpId());
        req.setCurrentUserId("1");
        return req;
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
        assertSecurityImportWithTips(Collections.emptyList(), "110001.SH");
    }

    @Test
    public void checkAndSubmitShouldKeepWarningsAndAcceptOriginallyFailedSecurity() {
        assertSecurityImportWithTips(Arrays.asList("证券当前在调入限制池中：债券禁止库", "证券当前在禁止池中"),
                "110001.sh");
    }

    /** 自选主体按本次批量查询的结果匹配历史编码 */
    @Test
    public void submitShouldResolveLegacySelfSelectedCompanyCode() {
        SecurityInfoBo master = new SecurityInfoBo();
        master.setWindCode("ABS1");
        master.setSecurityType("abs");
        BatchSecurityCheckData data = new BatchSecurityCheckData();
        data.securities.put("ABS1", master);
        SelfSelectedRightsHolderDto candidate = new SelfSelectedRightsHolderDto();
        candidate.setCompanyCode("C001");
        candidate.setCompanyName("测试权益人");
        data.selfSelectedCompanies.put("C001", candidate);
        SysImpTmpDetlBo item = new SysImpTmpDetlBo();
        item.setFld001("ABS1");
        item.setFld013("c001");
        SecurityPoolAdjustSubmitReq req = new SecurityPoolAdjustSubmitReq();

        ReflectionTestUtils.invokeMethod(service, "applyImportedRatingCompany", req, item, data);

        assertEquals("C001", req.getSelfSelectedRightsHolderCode());
        assertEquals("测试权益人", req.getSelectedRatingCompany().getCompanyName());
    }

    /** 同批 ABS 多主体全部作为不可调整行返回，其他独立证券仍通过 */
    @Test
    public void conflictingAbsRowsShouldReturnReasonsWithoutAbortingImportCheck() {
        AbsIdentityService identity = new AbsIdentityService();
        ReflectionTestUtils.setField(service, "absIdentityService", identity);
        SecurityInfoBo abs = new SecurityInfoBo();
        abs.setWindCode("ABS1");
        abs.setSecurityType("abs");
        RelatedRatingCompanyDto first = new RelatedRatingCompanyDto();
        first.setCompanyCode("A");
        RelatedRatingCompanyDto second = new RelatedRatingCompanyDto();
        second.setCompanyCode("B");
        BatchSecurityCheckData data = new BatchSecurityCheckData();
        data.securities.put("ABS1", abs);
        data.ratingCompanies.put("ABS1", Arrays.asList(first, second));
        when(batchSecurityCheckService.loadExcelCheckData(anyList(), eq(false))).thenReturn(data);
        when(batchSecurityCheckService.findConflictingAbsCodes(anyList(), eq(data))).thenCallRealMethod();
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(101L);
        when(importMapper.queryEnabledLeafPoolByParentAndChildName("父池", "子池", true)).thenReturn(pool);
        List<SysImpTmpDetlBo> rows = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            SysImpTmpDetlBo row = new SysImpTmpDetlBo();
            row.setId((long) index + 1);
            row.setFld001(index == 2 ? "OTHER" : "ABS1");
            row.setFld003("父池");
            row.setFld004("子池");
            if (index < 2) row.setFld011(index == 0 ? "A" : "B");
            rows.add(row);
        }
        when(batchSecurityCheckService.checkExcelItem(any(), any())).thenAnswer(invocation -> {
            AdjustCheckDto.CheckResultItem item = new AdjustCheckDto.CheckResultItem();
            item.setItemTag("manual");
            item.setCanAdjust(true);
            AdjustCheckDto result = new AdjustCheckDto();
            result.setItems(Collections.singletonList(item));
            return result;
        });
        SecurityPoolExcelImportService.SecurityImportCheckResult checked = ReflectionTestUtils.invokeMethod(service,
                "checkSecurityImport", rows, true, false, "1", new Date());
        List<SecurityPoolExcelImportCheckItemDto> result = checked.items;
        assertEquals(3, result.size());
        for (SecurityPoolExcelImportCheckItemDto item : result.subList(0, 2)) {
            assertTrue(!item.isCanAdjust());
            assertTrue(item.getFailReasons().get(0).contains("同批申请中选择了不同主体"));
        }
        assertTrue(result.get(2).isCanAdjust());
        assertEquals("2", rows.get(0).getChkRslt());
        assertEquals("2", rows.get(1).getChkRslt());
        assertEquals("1", rows.get(2).getChkRslt());

        // 一行的池名称错误不掩盖同一 ABS 的不同主体选择
        rows.get(1).setFld004(null);
        checked = ReflectionTestUtils.invokeMethod(service,
                "checkSecurityImport", rows, true, false, "1", new Date());
        result = checked.items;
        assertEquals(3, result.size());
        assertTrue(result.get(0).getFailReasons().get(0).contains("同批申请中选择了不同主体"));
        assertTrue(result.get(1).getFailReasons().contains("子池名称为空"));
        assertTrue(result.get(2).isCanAdjust());
    }

    /** 使用真实导入编排验证来源明细、校验快照和提交参数保持一致 */
    private void assertSecurityImportWithTips(List<String> tips, String inputCode) {
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
        item.setFld001(inputCode);
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
        pool.setBatchInFlowId(12L);
        pool.setBatchInFlowKey("bond:inbound");
        when(importMapper.queryEnabledLeafPoolByParentAndChildName("信用债大库", "一级库", true))
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
        ri.setFailReasons(new ArrayList<>(tips));
        AdjustCheckDto.FlowOption normal = new AdjustCheckDto.FlowOption();
        normal.setFlowId(12L);
        normal.setFlowKey("bond:inbound");
        normal.setFlowType("normalInbound");
        normal.setSelectable(true);
        ri.setFlowOptions(Collections.singletonList(normal));
        AdjustCheckDto checkDto = new AdjustCheckDto();
        checkDto.setItems(Collections.singletonList(ri));
        when(batchSecurityCheckService.checkExcelItem(any(AdjustCheckReq.class), any())).thenReturn(checkDto);
        BatchSecurityCheckData data = new BatchSecurityCheckData();
        data.excelSecurityCodes.put("110001.SH", "110001.SH");
        SecurityInfoBo master = new SecurityInfoBo();
        master.setWindCode("110001.SH");
        master.setSecurityType("bond");
        data.securities.put(master.getWindCode(), master);
        when(batchSecurityCheckService.loadExcelCheckData(anyList(), eq(false))).thenAnswer(invocation -> {
            List<AdjustCheckReq> checks = invocation.getArgument(0);
            assertEquals(inputCode, checks.get(0).getSecurityCode());
            checks.get(0).setSecurityCode(data.resolveExcelSecurityCode(inputCode));
            return data;
        });

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
        verify(batchSecurityCheckService).checkExcelItem(any(AdjustCheckReq.class), any());
        verify(importMapper).editItemCheckResultList(eq("IMP3"), eq(items), any(Date.class));
        verify(importMapper).editBatchCheckResult(any(SysImpTmpBo.class));
        verify(forbiddenPoolAdjustService, never()).checkCompanyExcelAdjust(any(), any());
        assertEquals("1", item.getChkRslt());
        assertEquals("110001.SH", item.getFld001());
        assertEquals(1, dto.getCheckPassCount().intValue());
        assertEquals(0, dto.getCheckFailCount().intValue());
        assertEquals(0, dto.getPendingCount().intValue());
        assertEquals(1L, dto.getItems().getTotal());
        assertEquals(1, dto.getItems().getPageIndex());
        assertEquals(20, dto.getItems().getPageSize());
        assertEquals("bond", dto.getItems().getRecords().get(0).getSecurityType());
        assertEquals("110001.SH", dto.getItems().getRecords().get(0).getSecurityCode());
        // 明细仍展示持久化的原 Excel 名称，校验结果使用主档名称
        assertEquals(null, dto.getItems().getRecords().get(0).getSecurityName());
        assertEquals("测试债", dto.getCheckItems().get(0).getSecurityShortName());
        verify(importMapper).queryBatchByImpId("IMP3");
        verify(importMapper, never()).queryItemCountByCheckResult(anyString(), anyString());
        verify(importMapper, never()).queryItemPage(anyString(), any(), any());
        verify(batchSecurityCheckService, never()).loadExcelRatingCandidates(anyList(), anyList());
        verify(batchSecurityCheckService).completeExcelRatingCandidates(
                eq(data), eq(Collections.singletonList("110001.SH")), eq(Collections.emptyList()));
        if (!tips.isEmpty()) {
            assertTrue(item.getChkDscr().contains("调整提示"));
            assertEquals(tips, dto.getCheckItems().get(0).getFailReasons());
            assertTrue(batch.getResultJson().contains("证券当前在禁止池中"));
            SecurityPoolExcelImportCheckItemDto chosen = dto.getCheckItems().get(0);
            assertEquals("batchInbound", chosen.getFlowType());
            chosen.setFlowType("normalInbound");
            chosen.setSelectedFlowKey("0_bond:inbound_1");
            req.setCheckItems(dto.getCheckItems());
            req.setCurrentUserName("测试研究员");
            SecurityPoolExcelImportDto submitted = service.submitImport(req);
            verify(batchSecurityCheckService).loadExcelRatingCandidates(
                    Collections.singletonList("110001.SH"), Collections.emptyList());
            verify(importMapper).editItemSaveResultByIds(eq("IMP3"), eq(Collections.singletonList(10L)), any(Date.class));
            verify(importMapper, times(2)).queryBatchItemList("IMP3");
            ArgumentCaptor<List<SecurityPoolAdjustSubmitReq>> requests = ArgumentCaptor.forClass(List.class);
            verify(batchSecurityAdjustSubmitService).submit(requests.capture(), any(), eq(false));
            assertEquals(1, requests.getValue().size());
            assertEquals("110001.SH", requests.getValue().get(0).getSecurityCode());
            assertEquals("Excel导入", requests.getValue().get(0).getAdjustType());
            assertEquals(Long.valueOf(5), requests.getValue().get(0).getItems().get(0).getTargetPoolId());
            assertEquals("normalInbound", requests.getValue().get(0).getItems().get(0).getFlowType());
            assertEquals("0_bond:inbound_1", submitted.getCheckItems().get(0).getSelectedFlowKey());
            assertEquals(tips, submitted.getCheckItems().get(0).getFailReasons());
            assertEquals("batchInbound", service.queryTask(req).getCheckItems().get(0).getFlowType());
        }
    }

    /** Excel 保留单券快速候选和豁免信息，同时默认选择配置的批量流程 */
    @Test
    public void mapSecurityCheckResult_ShouldRecommendBatchAndKeepFastExemption() {
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(5L);
        pool.setBatchInFlowId(10L);
        pool.setBatchInFlowKey("bond:batch-inbound");
        pool.setBatchInFlowName("债券批量入库流程");
        AdjustCheckDto.FlowOption fast = new AdjustCheckDto.FlowOption();
        fast.setFlowId(11L);
        fast.setFlowKey("bond:simple-inbound");
        fast.setFlowType("simpleInbound");
        fast.setSelectable(true);
        fast.setRecommended(true);
        fast.setMaterialExemptionType("productSource");
        AdjustCheckDto.FlowOption normal = new AdjustCheckDto.FlowOption();
        normal.setFlowType("normalInbound");
        normal.setSelectable(true);
        AdjustCheckDto.CheckResultItem row = new AdjustCheckDto.CheckResultItem();
        row.setTargetPoolId(5L);
        row.setAdjustMode("调入");
        row.setItemTag("manual");
        row.setCanAdjust(true);
        row.setFlowOptions(Arrays.asList(fast, normal));
        row.setMaterialExemptionType("productSource");

        SecurityPoolExcelImportCheckItemDto result = ReflectionTestUtils.invokeMethod(service,
                "mapSecurityCheckResult", null, row, "108008901.IB", pool, "in");

        assertEquals(3, result.getFlowOptions().size());
        assertEquals("batchInbound", result.getFlowType());
        assertEquals("productSource", result.getMaterialExemptionType());
        assertEquals(Long.valueOf(10L), result.getFlowId());
        assertTrue(result.getFlowOptions().get(0).isRecommended());
        assertEquals(null, result.getFlowOptions().get(0).getMaterialExemptionType());
        assertEquals("productSource", result.getFlowOptions().get(1).getMaterialExemptionType());
        assertEquals(false, result.getFlowOptions().get(1).isRecommended());
        assertTrue(fast.isRecommended());
        verify(investmentPoolMapper, never()).queryPoolById(any());

        row.setCanAdjust(false);
        SecurityPoolExcelImportCheckItemDto failed = ReflectionTestUtils.invokeMethod(service,
                "mapSecurityCheckResult", null, row, "108008901.IB", pool, "in");
        assertEquals(2, failed.getFlowOptions().size());
    }

    /** Excel 导入应按证券类型默认选择首个关联评级主体。 */
    @Test
    public void applyDefaultRatingCompany_ShouldFillAbsRightsHolderAndNonAbsGuarantor() {
        SecurityInfoBo absSecurity = new SecurityInfoBo();
        absSecurity.setAbsFlag(1);
        BatchSecurityCheckData data = new BatchSecurityCheckData();
        data.securities.put("ABS001.IB", absSecurity);
        data.ratingCompanies.put("ABS001.IB", buildRelatedCompanies("RIGHTS_FIRST", "RIGHTS_SECOND"));

        AdjustCheckReq absReq = new AdjustCheckReq();
        absReq.setSecurityCode("ABS001.IB");
        ReflectionTestUtils.invokeMethod(service, "applyClearRatingCompany", absReq, data);

        assertEquals("RIGHTS_FIRST", absReq.getRightsHolderCode());
        assertEquals(null, absReq.getGuarantorCode());

        BatchSecurityCheckData.Row occupied = new BatchSecurityCheckData.Row();
        occupied.setCompanyCode("OCCUPIED");
        data.absIdentities.put("ABS001.IB", Collections.singletonList(occupied));
        absReq.setRightsHolderCode(null);
        ReflectionTestUtils.invokeMethod(service, "applyClearRatingCompany", absReq, data);
        assertEquals("OCCUPIED", absReq.getSelfSelectedRightsHolderCode());

        SecurityInfoBo normalSecurity = new SecurityInfoBo();
        normalSecurity.setAbsFlag(0);
        data.securities.put("BOND001.IB", normalSecurity);
        data.ratingCompanies.put("BOND001.IB", buildRelatedCompanies("GUARANTOR_FIRST", "GUARANTOR_SECOND"));

        SecurityPoolAdjustSubmitReq normalReq = new SecurityPoolAdjustSubmitReq();
        normalReq.setSecurityCode("BOND001.IB");
        ReflectionTestUtils.invokeMethod(service, "applyDefaultRatingCompany", normalReq, data, Collections.emptyMap());

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
}
