package com.znty.rrs.service;


import com.github.pagehelper.PageHelper;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.bo.SysImpTmpBo;
import com.znty.rrs.entity.bo.SysImpTmpDetlBo;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustCheckReq;
import com.znty.rrs.entity.forbiddenpooladjust.CompanyAdjustCheckContext;
import com.znty.rrs.entity.securitypoolexcelimport.SecurityPoolExcelImportDto;
import com.znty.rrs.entity.securitypoolexcelimport.CompanyImportMatchDto;
import com.znty.rrs.entity.securitypoolexcelimport.SecurityPoolExcelImportReq;
import com.znty.rrs.entity.securitypoolexcelimport.SecurityPoolExcelImportCheckItemDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.RelatedRatingCompanyDto;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.mapper.SecurityPoolExcelImportMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

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
    /** 模拟数据库按原条件返回的批量匹配结果 */
    private final Map<String, List<String>> companyMatches = new HashMap<>();

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
        when(forbiddenPoolAdjustService.createCompanyCheckContext(anyMap())).thenAnswer(invocation ->
                new CompanyAdjustCheckContext(invocation.getArgument(0), Collections.emptyList(), Collections.emptyList()));
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
        ArgumentCaptor<List<String>> chunks = ArgumentCaptor.forClass(List.class);
        verify(importMapper, times(6)).queryCompanyMatchList(eq(0), chunks.capture());
        for (List<String> chunk : chunks.getAllValues()) {
            assertEquals(500, chunk.size());
        }
        verify(importMapper, never()).queryCompanyMatchList(eq(1), anyList());
        verify(importMapper, never()).queryCompanyMatchList(eq(2), anyList());
        verify(forbiddenPoolAdjustService, times(1)).createCompanyCheckContext(anyMap());
        companyMatches.put("0|CREDIT0", Collections.singletonList("CHANGED_COMP"));
        service.checkImport(req);
        assertEquals("CHANGED_COMP", rows.get(0).getFld001());
        verify(importMapper, times(12)).queryCompanyMatchList(eq(0), anyList());
        verify(forbiddenPoolAdjustService, times(2)).createCompanyCheckContext(anyMap());
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
        verify(forbiddenPoolAdjustService, never()).createCompanyCheckContext(anyMap());
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
        verify(forbiddenPoolAdjustService, never()).checkCompanyAdjust(any(), any());
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
            verify(importMapper).editItemCheckResult(item);
        }
        verify(forbiddenPoolAdjustService, times(2)).checkCompanyAdjust(any(), any());
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
        verify(forbiddenPoolAdjustService, times(1)).checkCompanyAdjust(any(), any());
        for (SysImpTmpDetlBo row : rows) {
            verify(importMapper).editItemCheckResult(row);
        }
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

    /** 构造主体 Excel，并捕获上传写入的明细。 */
    private List<SysImpTmpDetlBo> uploadCompanyRows(String[][] values) throws Exception {
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
            Sheet sheet = workbook.createSheet("主体");
            Row header = sheet.createRow(0);
            String[] columns = values[0].length == 5
                    ? new String[]{"父池名称", "子池名称", "统一社会信用代码", "主体全称", "主体代码"}
                    : new String[]{"父池名称", "子池名称", "主体全称", "主体代码"};
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
            req.setImportType("company");
            req.setDirection("in");
            req.setCurrentUserId("1");
            service.uploadExcel(req, new MockMultipartFile("file", "主体.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray()));
        }
        return savedRows;
    }

    /** 构造主体批次、池及完整主体校验返回结果。 */
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
        when(importMapper.queryEnabledLeafPoolByParentAndChildName(null, "观察池")).thenReturn(pool);
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
            AdjustCheckDto dto = new AdjustCheckDto();
            dto.setItems(Collections.singletonList(result));
            return dto;
        }).when(forbiddenPoolAdjustService).checkCompanyAdjust(any(), any());
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
        verify(forbiddenPoolAdjustService, never()).checkCompanyAdjust(any(), any());
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
        when(securityPoolAdjustMapper.querySecurityBoByCode("ABS001.IB")).thenReturn(absSecurity);
        when(securityPoolAdjustMapper.queryRelatedRatingCompanyList("ABS001.IB"))
                .thenReturn(buildRelatedCompanies("RIGHTS_FIRST", "RIGHTS_SECOND"));

        AdjustCheckReq absReq = new AdjustCheckReq();
        absReq.setSecurityCode("ABS001.IB");
        ReflectionTestUtils.invokeMethod(service, "applyDefaultRatingCompany", absReq);

        assertEquals("RIGHTS_FIRST", absReq.getRightsHolderCode());
        assertEquals(null, absReq.getGuarantorCode());

        SecurityInfoBo normalSecurity = new SecurityInfoBo();
        normalSecurity.setAbsFlag(0);
        when(securityPoolAdjustMapper.querySecurityBoByCode("BOND001.IB")).thenReturn(normalSecurity);
        when(securityPoolAdjustMapper.queryRelatedRatingCompanyList("BOND001.IB"))
                .thenReturn(buildRelatedCompanies("GUARANTOR_FIRST", "GUARANTOR_SECOND"));

        SecurityPoolAdjustSubmitReq normalReq = new SecurityPoolAdjustSubmitReq();
        normalReq.setSecurityCode("BOND001.IB");
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
}
