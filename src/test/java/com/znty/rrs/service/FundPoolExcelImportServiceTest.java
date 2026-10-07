package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.entity.bo.FundInfoBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SysImpTmpBo;
import com.znty.rrs.entity.bo.SysImpTmpDetlBo;
import com.znty.rrs.entity.fundpooladjust.FundAdjustCheckDto;
import com.znty.rrs.entity.fundpooladjust.FundAdjustCheckReq;
import com.znty.rrs.entity.fundpooladjust.FundAdjustSubmitDto;
import com.znty.rrs.entity.fundpooladjust.FundPoolAdjustSubmitReq;
import com.znty.rrs.entity.fundpoolexcelimport.FundPoolExcelImportCheckItemDto;
import com.znty.rrs.entity.fundpoolexcelimport.FundPoolExcelImportDto;
import com.znty.rrs.entity.fundpoolexcelimport.FundPoolExcelImportReq;
import com.znty.rrs.entity.investmentpool.InvestmentPoolDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FundPoolAdjustMapper;
import com.znty.rrs.mapper.FundPoolExcelImportMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 基金 Excel 导入的来源、清空、冲突及快照提交行为测试。 */
public class FundPoolExcelImportServiceTest {
    /** 被测导入服务。 */
    private FundPoolExcelImportService service;
    /** 临时表及池查询组件。 */
    private FundPoolExcelImportMapper mapper;
    /** 基金业务组件。 */
    private FundPoolAdjustService adjustService;
    /** 基金基础信息及在途组件。 */
    private FundPoolAdjustMapper fundMapper;
    /** 权限组件。 */
    private InvestmentPoolMapper poolMapper;
    /** 当前模拟批次。 */
    private SysImpTmpBo batch;
    /** 当前模拟明细。 */
    private List<SysImpTmpDetlBo> rows;

    /** 构建不连接数据库的行为测试环境。 */
    @Before
    public void setUp() {
        service = new FundPoolExcelImportService();
        mapper = mock(FundPoolExcelImportMapper.class);
        adjustService = mock(FundPoolAdjustService.class);
        fundMapper = mock(FundPoolAdjustMapper.class);
        poolMapper = mock(InvestmentPoolMapper.class);
        SysAttachmentService attachmentService = mock(SysAttachmentService.class);
        ReflectionTestUtils.setField(service, "fundPoolExcelImportMapper", mapper);
        ReflectionTestUtils.setField(service, "fundPoolAdjustService", adjustService);
        ReflectionTestUtils.setField(service, "fundPoolAdjustMapper", fundMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", attachmentService);
        batch = new SysImpTmpBo();
        batch.setImpId("IMPORT");
        batch.setBizType("fund_pool_excel");
        batch.setFld001("in");
        batch.setOptionJson("{\"clearTarget\":false,\"allowLinkMutex\":true}");
        batch.setTotalCount(1);
        batch.setPassCount(0);
        batch.setFailCount(0);
        batch.setChkRslt("0");
        batch.setSaveRslt("0");
        batch.setOpterId("1");
        rows = new ArrayList<>();
        when(mapper.queryBatchByImpId(anyString())).thenAnswer(invocation -> batch);
        when(mapper.queryBatchIdForUpdate(anyString())).thenReturn(1L);
        when(mapper.queryBatchItemList(anyString())).thenAnswer(invocation -> rows);
        when(mapper.queryItemPage(anyString(), any(), any())).thenAnswer(invocation -> rows);
        when(mapper.addBatch(any())).thenAnswer(invocation -> {
            batch = invocation.getArgument(0);
            return 1;
        });
        when(mapper.addItemList(anyList())).thenAnswer(invocation -> {
            List<SysImpTmpDetlBo> added = invocation.getArgument(0);
            for (SysImpTmpDetlBo row : added) {
                row.setId((long) rows.size() + 1);
                rows.add(row);
            }
            return added.size();
        });
        when(mapper.queryEnabledLeafPoolList(anyString(), anyString())).thenAnswer(invocation -> {
            InvestmentPoolBo pool = new InvestmentPoolBo();
            pool.setId(Long.valueOf(invocation.<String>getArgument(1).replace("池", "")));
            pool.setPoolType("fund");
            return Collections.singletonList(pool);
        });
        when(fundMapper.queryFundByCode(anyString())).thenAnswer(invocation -> {
            FundInfoBo fund = new FundInfoBo();
            fund.setFundCode(invocation.getArgument(0));
            fund.setFundName("权威基金名称");
            fund.setFundShortName("权威简称");
            fund.setSecurityType("fund_stock");
            return fund;
        });
        when(adjustService.checkExcelImportAdjust(any(), eq("1"))).thenAnswer(invocation -> {
            FundAdjustCheckReq request = invocation.getArgument(0);
            // 生成基金业务组件正常返回的一般流程结果
            return result(request, null);
        });
        when(attachmentService.parseOriginalFileNameListJson(any())).thenReturn(Collections.emptyList());
        when(attachmentService.resolveUploadOriginalFileName(any(), anyList())).thenReturn("基金.xlsx");
        FundAdjustSubmitDto submitted = new FundAdjustSubmitDto();
        submitted.setAdjustLogIds(Collections.singletonList(101L));
        submitted.setAdjustBatchNos(Collections.singletonList("FUND001"));
        when(adjustService.addExcelImportAdjustLogList(anyList())).thenReturn(Collections.singletonList(submitted));
    }

    /** 清理 Mock Mapper 未消费的分页线程状态。 */
    @After
    public void tearDown() {
        PageHelper.clearPage();
    }

    /** 七列原文、32 位批次号和权威名称在上传时保存。 */
    @Test
    public void uploadShouldPreserveRawFieldsAndAuthoritativeName() throws Exception {
        // 使用真实 POI 文件验证解析路径
        MockMultipartFile file = workbookFile(new String[][] {{"父池", "池10", "伪造名称", "F1", "80.1234", "stock", "0"}});
        // 构造管理员上传请求，关联前面生成的七列文件
        FundPoolExcelImportReq request = request();
        request.setDirection("in");
        FundPoolExcelImportDto dto = service.uploadExcel(request, file, null);
        assertEquals(32, dto.getImpId().length());
        assertEquals("80.1234", rows.get(0).getFld005());
        assertEquals("权威基金名称", rows.get(0).getFld002());
        assertEquals("stock", rows.get(0).getFld006());
        assertEquals(2, rows.get(0).getRowNo().intValue());
        assertFalse(dto.getCheckDone());
    }

    /** 数字列读取实际值，评分精度与审批值不得被 Excel 显示格式舍入掩盖。 */
    @Test
    public void uploadShouldPreserveNumericValuesDespiteDisplayRounding() throws Exception {
        // 构造使用模板显示格式但实际数字非法的两条来源行
        MockMultipartFile file = workbookFile(new String[][] {
                {"父池", "池10", "基金一", "F1", "5", "stock", "0"},
                {"父池", "池10", "基金二", "F2", "5", "stock", "0"}
        });
        // 构造上传请求以验证数字原值解析
        FundPoolExcelImportReq req = request();
        req.setDirection("in");
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle scoreStyle = workbook.createCellStyle();
            scoreStyle.setDataFormat(workbook.createDataFormat().getFormat("0.####"));
            CellStyle flagStyle = workbook.createCellStyle();
            flagStyle.setDataFormat(workbook.createDataFormat().getFormat("0"));
            workbook.getSheetAt(0).getRow(1).getCell(4).setCellValue(5.12345);
            workbook.getSheetAt(0).getRow(1).getCell(4).setCellStyle(scoreStyle);
            workbook.getSheetAt(0).getRow(2).getCell(6).setCellValue(1.2);
            workbook.getSheetAt(0).getRow(2).getCell(6).setCellStyle(flagStyle);
            workbook.write(output);
            service.uploadExcel(req, new MockMultipartFile("file", "基金.xlsx",
                    "application/octet-stream", output.toByteArray()), null);
        }
        assertEquals("5.12345", rows.get(0).getFld005());
        assertEquals("1.2", rows.get(1).getFld007());
        // 基础校验按实际数字拒绝两行，原文继续保留供用户定位
        service.checkImport(req);
        assertEquals("2", rows.get(0).getChkRslt());
        assertTrue(rows.get(0).getChkDscr().contains("四位小数"));
        assertEquals("2", rows.get(1).getChkRslt());
        assertTrue(rows.get(1).getChkDscr().contains("必须为 0 或 1"));
    }

    /** 七列模板缺列应直接拒绝。 */
    @Test
    public void uploadShouldRejectMissingHeader() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet().createRow(0).createCell(0).setCellValue("基金代码");
            workbook.getSheetAt(0).createRow(1).createCell(0).setCellValue("F1");
            workbook.write(output);
            // 构造缺列文件的上传请求
            FundPoolExcelImportReq request = request();
            request.setDirection("in");
            try {
                service.uploadExcel(request, new MockMultipartFile("file", "test.xlsx", "application/octet-stream", output.toByteArray()), null);
                fail("缺列模板应失败");
            } catch (BizException ex) {
                assertTrue(ex.getMessage().contains("七列"));
            }
        }
    }

    /** 文件大小和统一清空字段在上传前校验。 */
    @Test
    public void uploadShouldValidateFileSizeAndClearFields() {
        // 构造文件大小与清空必填字段的验证请求
        FundPoolExcelImportReq request = request();
        request.setDirection("in");
        try {
            service.uploadExcel(request, new MockMultipartFile("file", "test.xlsx", "application/octet-stream", new byte[5 * 1024 * 1024 + 1]), null);
            fail("超大文件应失败");
        } catch (BizException ex) {
            assertTrue(ex.getMessage().contains("5MB"));
        }
        request.setClearTarget(true);
        try {
            service.uploadExcel(request, new MockMultipartFile("file", "test.xlsx", "application/octet-stream", new byte[] {1}), null);
            fail("缺少清空字段应失败");
        } catch (BizException ex) {
            assertTrue(ex.getMessage().contains("评分不能为空"));
        }
    }

    /** 非法评分仍保留原文并解析目标池。 */
    @Test
    public void invalidScoreShouldFailRowWithoutLosingResolvedPool() {
        InvestmentPoolDto pool = new InvestmentPoolDto();
        pool.setId(10L);
        pool.setPoolFullName("基金根池/父池/池10");
        when(poolMapper.queryPoolFullNameList()).thenReturn(Collections.singletonList(pool));
        // 构造不能解析为数字的原始评分
        rows.add(row(1L, "F1", 10L, "not-number", "stock", "0"));
        // 构造校验请求，验证非法评分行仍保存已解析目标池
        FundPoolExcelImportDto dto = service.checkImport(request());
        assertEquals("10", rows.get(0).getFld009());
        assertEquals("基金根池/父池/池10", dto.getCheckItems().get(0).getPoolName());
        assertEquals("not-number", dto.getItems().getRecords().get(0).getFundScoreRaw());
        assertFalse(dto.getCheckItems().get(0).isCanAdjust());
        assertTrue(rows.get(0).getChkDscr().contains("合法数字"));
        verify(adjustService, never()).checkExcelImportAdjust(any(), anyString());
    }

    /** 存储精度校验只防数据截断，不引入基金评分准入范围。 */
    @Test
    public void scoreShouldRespectDecimalStoragePrecisionWithoutBusinessRange() {
        // 现有基金日志评分列为 DECIMAL(10,4)
        rows.add(row(1L, "F1", 10L, "-999999.9999", "other", "1"));
        // 构造校验请求，确认负评分在存储精度内可以通过
        assertTrue(service.checkImport(request()).getCheckItems().get(0).isCanAdjust());
        rows.get(0).setFld005("1.00001");
        // 构造重校验请求，验证超过四位小数的评分被拒绝
        assertFalse(service.checkImport(request()).getCheckItems().get(0).isCanAdjust());
        assertTrue(rows.get(0).getChkDscr().contains("四位小数"));
    }

    /** 投资类型和领导审批仅接受现有 code 及 0/1。 */
    @Test
    public void investmentAndLeaderFieldsShouldRejectLabelsAndInvalidFlags() {
        // 中文名称不参与后端字典映射
        rows.add(row(1L, "F1", 10L, "80", "股票型", "0"));
        // 构造校验请求，验证中文投资类型不能代替 code
        assertFalse(service.checkImport(request()).getCheckItems().get(0).isCanAdjust());
        rows.get(0).setFld006("stock");
        rows.get(0).setFld007("是");
        // 构造重校验请求，验证中文审批标记不能代替 0 或 1
        assertFalse(service.checkImport(request()).getCheckItems().get(0).isCanAdjust());
        assertTrue(rows.get(0).getChkDscr().contains("0 或 1"));
    }

    /** 失败导入行仍属清空保留集合，在途差集成员跳过。 */
    @Test
    public void clearShouldReserveFailedRowsAndAllowOnlyValidClearSubmission() {
        batch.setOptionJson("{\"clearTarget\":true,\"allowLinkMutex\":false,\"clearFundScore\":90,\"clearFundInvestmentType\":\"other\",\"clearNeedRiskLeaderApproval\":1}");
        // 当前 Excel 中的 F1 即使评分无效也不能被清空
        rows.add(row(1L, "F1", 10L, "bad", "stock", "0"));
        FundInfoBo keep = new FundInfoBo();
        keep.setFundCode("F1");
        FundInfoBo clear = new FundInfoBo();
        clear.setFundCode("F2");
        FundInfoBo pending = new FundInfoBo();
        pending.setFundCode("F3");
        when(mapper.queryPoolMemberList(10L)).thenReturn(Arrays.asList(keep, clear, pending));
        when(fundMapper.queryFundHasPendingProcess(eq("F3"), eq(10L), isNull())).thenReturn(true);
        // 构造校验请求，验证失败行保留集合和在途差集成员跳过
        FundPoolExcelImportDto checked = service.checkImport(request());
        assertEquals(2, checked.getCheckItems().size());
        assertEquals("F2", checked.getCheckItems().get(0).getFundCode());
        assertEquals("clear", checked.getCheckItems().get(0).getItemTag());
        assertEquals(0, new BigDecimal("90").compareTo(checked.getCheckItems().get(0).getFundScore()));
        // 构造提交请求，验证仅有效清空来源组生成审批申请
        assertEquals("FUND001", service.submitImport(request()).getAdjustBatchNoList().get(0));
        // 构造类型安全捕获器，核对清空来源组的提交字段
        ArgumentCaptor<List<FundPoolAdjustSubmitReq>> captured = listCaptor();
        verify(adjustService).addExcelImportAdjustLogList(captured.capture());
        assertEquals(1, captured.getValue().size());
        assertEquals("Excel清空", captured.getValue().get(0).getAdjustType());
        assertEquals("manual", captured.getValue().get(0).getItems().get(0).getItemTag());
        assertEquals(AdjustMode.OUT.getCode(), captured.getValue().get(0).getItems().get(0).getAdjustMode());
        assertEquals("0", rows.get(0).getSaveRslt());
    }

    /** 同基金不同目标池及不同三字段保留为独立来源请求。 */
    @Test
    public void submissionShouldKeepDifferentRowMetadataAndIgnoreClientBusinessValues() {
        // 两行基金评分及投资类型不同，不能按基金代码合并
        rows.add(row(1L, "F1", 10L, "50", "stock", "0"));
        // 构造同基金另一目标池的来源行，保留不同三字段
        rows.add(row(2L, "F1", 20L, "60", "other", "1"));
        batch.setTotalCount(2);
        // 构造校验请求并取得后续防篡改断言使用的快照
        FundPoolExcelImportDto checked = service.checkImport(request());
        checked.getCheckItems().get(0).setFundScore(new BigDecimal("999"));
        checked.getCheckItems().get(0).setFundInvestmentType("money_market");
        // 构造提交请求，回传被改动的客户端业务字段
        FundPoolExcelImportReq request = request();
        request.setCheckItems(checked.getCheckItems());
        service.submitImport(request);
        // 构造列表捕获器，核对服务器采用持久化来源三字段
        ArgumentCaptor<List<FundPoolAdjustSubmitReq>> captured = listCaptor();
        verify(adjustService).addExcelImportAdjustLogList(captured.capture());
        assertEquals(2, captured.getValue().size());
        assertEquals(new BigDecimal("50"), captured.getValue().get(0).getFundScore());
        assertEquals("stock", captured.getValue().get(0).getFundInvestmentType());
        assertEquals(new BigDecimal("60"), captured.getValue().get(1).getFundScore());
        assertEquals(Integer.valueOf(1), captured.getValue().get(1).getNeedRiskLeaderApproval());
        assertEquals(AdjustMode.IN.getCode(), captured.getValue().get(0).getItems().get(0).getAdjustMode());
        ArgumentCaptor<FundAdjustCheckReq> checkedRequest = ArgumentCaptor.forClass(FundAdjustCheckReq.class);
        verify(adjustService, org.mockito.Mockito.times(2)).checkExcelImportAdjust(checkedRequest.capture(), eq("1"));
        assertEquals(AdjustMode.IN.getCode(), checkedRequest.getValue().getItems().get(0).getAdjustMode());
    }

    /** 跨来源组关系项同基金同池冲突应阻断所有相关组并定位行号。 */
    @Test
    public void overlappingRelationTargetsShouldBlockAllSourceGroups() {
        // 构造第一个来源行，用于展开共享目标池的关系项
        rows.add(row(1L, "F1", 10L, "50", "stock", "0"));
        // 构造另一来源行，验证相同关系目标池的跨组冲突
        rows.add(row(2L, "F1", 20L, "60", "other", "1"));
        batch.setTotalCount(2);
        when(adjustService.checkExcelImportAdjust(any(), eq("1"))).thenAnswer(invocation -> {
            // 不同方向的相同关系目标池也视为冲突
            FundAdjustCheckReq request = invocation.getArgument(0);
            // 构造通过的主项校验结果，供追加冲突关系项
            FundAdjustCheckDto dto = result(request, null);
            // 构造共享目标池的联动项，分别使用调入和调出方向
            FundAdjustCheckDto.CheckResultItem relation = resultItem(request.getFundCode(), 30L,
                    request.getItems().get(0).getTargetPoolId() == 10L ? AdjustMode.IN.getCode() : AdjustMode.OUT.getCode(), "linkage");
            dto.getItems().add(relation);
            return dto;
        });
        // 构造校验请求，检查所有冲突来源组均被阻断
        FundPoolExcelImportDto dto = service.checkImport(request());
        assertEquals(4, dto.getCheckItems().size());
        assertTrue(dto.getCheckItems().stream().noneMatch(FundPoolExcelImportCheckItemDto::isCanAdjust));
        assertTrue(rows.get(0).getChkDscr().contains("第 2 行"));
        assertTrue(rows.get(1).getChkDscr().contains("第 3 行"));
    }

    /** 主项报告失败时，其通过的关系项不得独立提交。 */
    @Test
    public void failedReportPrimaryShouldBlockRelations() {
        // 构造报告限制用例的主项来源行
        rows.add(row(1L, "F1", 10L, "50", "stock", "0"));
        when(adjustService.checkExcelImportAdjust(any(), eq("1"))).thenAnswer(invocation -> {
            FundAdjustCheckReq request = invocation.getArgument(0);
            // 报告失败由基金内部导入检查入口提前返回
            FundAdjustCheckDto dto = result(request, "Excel 导入暂不支持提交基金报告");
            // 构造通过的联动项，验证失败主项仍阻止该项独立提交
            dto.getItems().add(resultItem("F1", 20L, AdjustMode.IN.getCode(), "linkage"));
            return dto;
        });
        // 构造校验请求，验证报告主项与关系项的提交门禁
        FundPoolExcelImportDto dto = service.checkImport(request());
        assertTrue(dto.getCheckItems().stream().noneMatch(FundPoolExcelImportCheckItemDto::isCanAdjust));
        assertTrue(rows.get(0).getChkDscr().contains("基金报告"));
    }

    /** 即使基金调整检查通过，缺失 Excel 导入权限仍不能提交。 */
    @Test
    public void checkShouldRequireExcelPermissionInAdditionToFundCheck() {
        // 构造调整规则通过但需额外检查导入权限的来源行
        rows.add(row(1L, "F1", 10L, "50", "stock", "0"));
        // 构造用于模拟无 Excel 导入权限用户的校验请求
        FundPoolExcelImportReq request = request();
        request.setCurrentUserId("2");
        // 构造基金调整检查通过的结果，将阻断原因限定为导入权限
        when(adjustService.checkExcelImportAdjust(any(), eq("2"))).thenAnswer(invocation -> result(invocation.getArgument(0), null));
        assertFalse(service.checkImport(request).getCheckItems().get(0).isCanAdjust());
        assertTrue(rows.get(0).getChkDscr().contains("Excel 导入"));
    }

    /** 客户端不能选择未出现在服务器快照的一般流程。 */
    @Test
    public void submitShouldRejectForgedFlowSelection() {
        // 构造用于流程防篡改验证的来源行
        rows.add(row(1L, "F1", 10L, "50", "stock", "0"));
        // 构造校验请求并读取服务器实际保存的流程候选
        FundPoolExcelImportDto checked = service.checkImport(request());
        checked.getCheckItems().get(0).setSelectedFlowId(999L);
        // 构造提交请求，回传未出现在服务器候选中的流程
        FundPoolExcelImportReq request = request();
        request.setCheckItems(checked.getCheckItems());
        try {
            service.submitImport(request);
            fail("伪造流程应失败");
        } catch (BizException ex) {
            assertTrue(ex.getMessage().contains("一般审批流程"));
        }
        verify(adjustService, never()).addExcelImportAdjustLogList(anyList());
    }

    /** 未校验、已提交及其他业务批次不能绕过批次状态保护。 */
    @Test
    public void batchGuardsShouldRejectUncheckedSubmittedAndOtherBusiness() {
        try {
            // 构造未校验批次的提交请求，验证状态保护
            service.submitImport(request());
            fail("未校验应失败");
        } catch (BizException ex) {
            assertTrue(ex.getMessage().contains("先校验"));
        }
        batch.setSaveRslt("1");
        try {
            // 构造已提交批次的取消请求，验证申请不能经导入重置
            service.cancelImport(request());
            fail("已提交不能取消");
        } catch (BizException ex) {
            assertTrue(ex.getMessage().contains("已提交"));
        }
        batch.setBizType("security_pool_excel");
        try {
            // 构造其他业务批次的查询请求，验证基金导入隔离
            service.queryTask(request());
            fail("其他业务应隔离");
        } catch (BizException ex) {
            assertTrue(ex.getMessage().contains("不存在"));
        }
    }

    /** 原因建议超过 200 字仍使用现有 TEXT 槽完整保存，最多 1000 字。 */
    @Test
    public void longReasonAdviceShouldUseTextSlotsAndRespectLimit() throws Exception {
        String text = String.join("", Collections.nCopies(1000, "文"));
        // 构造用于验证长原因和意见保存上限的上传请求
        FundPoolExcelImportReq req = request();
        req.setDirection("in");
        req.setAdjustReason(text);
        req.setAdjustAdvice(text);
        // 构造七列文件并上传，验证长文保存不使用 VARCHAR(200) 业务槽
        FundPoolExcelImportDto dto = service.uploadExcel(req,
                workbookFile(new String[][] {{"父池", "池10", "名称", "F1", "80", "stock", "0"}}), null);
        assertEquals(text, batch.getFld011());
        assertEquals(text, batch.getFld012());
        assertEquals(text, dto.getReason());
        assertEquals(text, dto.getAdvice());
        req.setAdjustReason(text + "超");
        try {
            service.submitImport(req);
            fail("1001 字应失败");
        } catch (BizException ex) {
            assertTrue(ex.getMessage().contains("1000"));
        }
    }

    /** 多来源冲突定位使用有限来源及总数，行级说明不得超过 500 字。 */
    @Test
    public void manyConflictsShouldKeepBoundedLocationSummary() {
        for (long index = 1; index <= 20; index++) {
            // 全部来源使用同一基金和池，不允许静默合并
            rows.add(row(index, "F1", 10L, "80", "stock", "0"));
        }
        batch.setTotalCount(20);
        // 构造校验请求，验证大量冲突来源的定位摘要受到长度约束
        FundPoolExcelImportDto dto = service.checkImport(request());
        assertTrue(dto.getCheckItems().stream().noneMatch(FundPoolExcelImportCheckItemDto::isCanAdjust));
        assertTrue(rows.stream().allMatch(item -> item.getChkDscr().length() <= 500));
        assertTrue(rows.get(0).getChkDscr().contains("共 20 个来源组"));
        assertTrue(batch.getResultJson().length() < 30000);
    }

    /** 非法评分加小写基金代码仍通过主档权威代码加入清空保留集合。 */
    @Test
    public void clearShouldReserveCanonicalCodeEvenWhenScoreInvalid() {
        batch.setOptionJson("{\"clearTarget\":true,\"allowLinkMutex\":false,\"clearFundScore\":90,\"clearFundInvestmentType\":\"other\",\"clearNeedRiskLeaderApproval\":1}");
        // 模拟 MySQL ai_ci 按原始代码查询成功后返回主档规范代码
        rows.add(row(1L, "FUND001.sh", 10L, "bad", "stock", "0"));
        FundInfoBo fund = new FundInfoBo();
        fund.setFundCode("FUND001.SH");
        fund.setFundName("权威基金名称");
        when(fundMapper.queryFundByCode("FUND001.sh")).thenReturn(fund);
        when(mapper.queryPoolMemberList(10L)).thenReturn(Collections.singletonList(fund));
        // 构造校验请求，验证非法评分行按主档规范代码保留基金
        FundPoolExcelImportDto dto = service.checkImport(request());
        assertEquals(1, dto.getCheckItems().size());
        assertEquals("FUND001.SH", dto.getCheckItems().get(0).getFundCode());
        assertEquals("manual", dto.getCheckItems().get(0).getItemTag());
        assertEquals("FUND001.sh", rows.get(0).getFld001());
    }

    /** 原始小写代码可正常提交，日志请求使用主档规范代码。 */
    @Test
    public void submitShouldUseCanonicalFundCodeAndRetainRawSourceCode() {
        // 构造代码后缀小写的来源行，验证提交采用主档规范代码
        rows.add(row(1L, "FUND001.sh", 10L, "80", "stock", "0"));
        FundInfoBo fund = new FundInfoBo();
        fund.setFundCode("FUND001.SH");
        fund.setFundName("权威基金名称");
        when(fundMapper.queryFundByCode("FUND001.sh")).thenReturn(fund);
        // 构造校验请求，识别原始代码对应的基金主档
        service.checkImport(request());
        // 构造提交请求，验证规范代码与原始来源代码同时保留
        service.submitImport(request());
        // 构造列表捕获器，核对实际提交的规范基金代码
        ArgumentCaptor<List<FundPoolAdjustSubmitReq>> captured = listCaptor();
        verify(adjustService).addExcelImportAdjustLogList(captured.capture());
        assertEquals("FUND001.SH", captured.getValue().get(0).getFundCode());
        assertEquals("FUND001.sh", rows.get(0).getFld001());
    }

    /** 大小写不同的原始代码识别为同一主档，不能产生重复来源申请。 */
    @Test
    public void canonicalCodeShouldIdentifyCaseVariantSourceConflicts() {
        // 构造使用小写后缀的第一个原始来源行
        rows.add(row(1L, "FUND001.sh", 10L, "80", "stock", "0"));
        // 构造规范后缀的第二个来源行，验证大小写变体仍属同一基金
        rows.add(row(2L, "FUND001.SH", 10L, "90", "other", "1"));
        batch.setTotalCount(2);
        FundInfoBo fund = new FundInfoBo();
        fund.setFundCode("FUND001.SH");
        fund.setFundName("权威基金名称");
        when(fundMapper.queryFundByCode("FUND001.sh")).thenReturn(fund);
        // 构造校验请求，验证规范代码识别跨来源组冲突
        FundPoolExcelImportDto dto = service.checkImport(request());
        assertTrue(dto.getCheckItems().stream().noneMatch(FundPoolExcelImportCheckItemDto::isCanAdjust));
        assertTrue(rows.get(0).getChkDscr().contains("第 3 行"));
    }

    /** 首个 sheet 超过 2000 个非空数据行时整个上传明确拒绝。 */
    @Test
    public void uploadShouldRejectMoreThanTwoThousandDataRows() throws Exception {
        String[][] data = new String[2001][7];
        for (int index = 0; index < data.length; index++) {
            data[index] = new String[] {"父池", "池10", "名称", "F" + index, "80", "stock", "0"};
        }
        // 构造超过数据行上限的上传请求
        FundPoolExcelImportReq req = request();
        req.setDirection("in");
        try {
            // 真实工作簿验证解析器的非空行上限
            service.uploadExcel(req, workbookFile(data), null);
            fail("2001 行应失败");
        } catch (BizException ex) {
            assertTrue(ex.getMessage().contains("2000"));
        }
        verify(mapper, never()).addBatch(any());
    }

    /** 旧版 xls 正常导入，非 Excel 扩展名明确拒绝。 */
    @Test
    public void uploadShouldAcceptXlsAndRejectOtherExtensions() throws Exception {
        // 构造用于验证 xls 支持和非法扩展名拒绝的上传请求
        FundPoolExcelImportReq req = request();
        req.setDirection("in");
        // 验证 HSSF 与 XSSF 都走同一七列解析流程
        FundPoolExcelImportDto dto = service.uploadExcel(req,
                workbookFile(new String[][] {{"父池", "池10", "名称", "F1", "80", "stock", "0"}}, true), null);
        assertEquals(1, dto.getTotalCount().intValue());
        try {
            service.uploadExcel(req, new MockMultipartFile("file", "test.csv", "text/plain", new byte[] {1}), null);
            fail("csv 应被拒绝");
        } catch (BizException ex) {
            assertTrue(ex.getMessage().contains("xls / xlsx"));
        }
    }

    /** 构造标准批次请求。 */
    private FundPoolExcelImportReq request() {
        FundPoolExcelImportReq req = new FundPoolExcelImportReq();
        req.setImpId("IMPORT");
        req.setCurrentUserId("1");
        return req;
    }

    /**
     * 构造原始来源行。
     *
     * @param id 来源明细 ID，同时用于确定物理行号
     * @param code Excel 原始基金代码
     * @param poolId 通过子池名称关联的目标池 ID
     * @param score Excel 原始基金评分文本
     * @param type Excel 原始基金投资类型文本
     * @param leader Excel 原始领导审批文本
     */
    private SysImpTmpDetlBo row(Long id, String code, Long poolId, String score, String type, String leader) {
        SysImpTmpDetlBo row = new SysImpTmpDetlBo();
        row.setId(id);
        row.setImpId("IMPORT");
        row.setRowNo(id.intValue() + 1);
        row.setFld001(code);
        row.setFld003("父池");
        row.setFld004("池" + poolId);
        row.setFld005(score);
        row.setFld006(type);
        row.setFld007(leader);
        row.setChkRslt("0");
        row.setSaveRslt("0");
        return row;
    }

    /**
     * 构造基金检查返回值。
     *
     * @param request 基金代码和主项目标池、方向的校验请求
     * @param failure 需要附加的主项阻断原因，为空时保持通过
     */
    private FundAdjustCheckDto result(FundAdjustCheckReq request, String failure) {
        FundAdjustCheckReq.CheckItem primary = request.getItems().get(0);
        // 映射调用参数为基金组件的单项结果
        FundAdjustCheckDto.CheckResultItem item = resultItem(request.getFundCode(), primary.getTargetPoolId(), primary.getAdjustMode(), "manual");
        if (failure != null) {
            item.setCanAdjust(false);
            item.getFailReasons().add(failure);
        }
        FundAdjustCheckDto dto = new FundAdjustCheckDto();
        dto.setItems(new ArrayList<>(Collections.singletonList(item)));
        return dto;
    }

    /**
     * 构造一般流程校验项。
     *
     * @param code 基金主档代码
     * @param poolId 调整项的目标池 ID
     * @param mode 基金内部调入或调出编码
     * @param tag 手工或关系项来源标签
     */
    private FundAdjustCheckDto.CheckResultItem resultItem(String code, Long poolId, String mode, String tag) {
        FundAdjustCheckDto.CheckResultItem item = new FundAdjustCheckDto.CheckResultItem();
        item.setFundCode(code);
        item.setFundShortName("权威简称");
        item.setTargetPoolId(poolId);
        item.setPoolName("父池/池" + poolId);
        item.setPoolType("fund");
        item.setAdjustMode(mode);
        item.setItemTag(tag);
        item.setCanAdjust(true);
        item.setFailReasons(new ArrayList<>());
        item.setWarnings(Collections.emptyList());
        FundAdjustCheckDto.FlowOption option = new FundAdjustCheckDto.FlowOption();
        option.setFlowId(AdjustMode.IN.getCode().equals(mode) ? 121L : 122L);
        option.setFlowKey(AdjustMode.IN.getCode().equals(mode) ? "fund:normal-in" : "fund:normal-out");
        option.setFlowType(AdjustMode.IN.getCode().equals(mode) ? "normalInbound" : "normalOutbound");
        option.setSelectable(true);
        item.setFlowOptions(Collections.singletonList(option));
        return item;
    }

    /**
     * 构造真实七列表头工作簿。
     *
     * @param data 按固定七列顺序排列的数据行
     */
    private MockMultipartFile workbookFile(String[][] data) throws Exception {
        // 缺省使用 xlsx 文件构造器
        return workbookFile(data, false);
    }

    /**
     * 构造指定 xls/xlsx 格式的真实七列工作簿。
     *
     * @param data 按固定七列顺序排列的数据行
     * @param xls 是否使用旧版 xls 格式，为否则使用 xlsx
     */
    private MockMultipartFile workbookFile(String[][] data, boolean xls) throws Exception {
        try (Workbook workbook = xls ? new HSSFWorkbook() : new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet();
            String[] headers = {"父池名称", "子池名称", "基金名称", "基金代码", "基金评分", "基金投资类型", "风管领导审批"};
            workbook.getSheetAt(0).createRow(0);
            for (int column = 0; column < headers.length; column++) {
                workbook.getSheetAt(0).getRow(0).createCell(column).setCellValue(headers[column]);
            }
            for (int index = 0; index < data.length; index++) {
                workbook.getSheetAt(0).createRow(index + 1);
                for (int column = 0; column < data[index].length; column++) {
                    workbook.getSheetAt(0).getRow(index + 1).createCell(column).setCellValue(data[index][column]);
                }
            }
            workbook.write(output);
            return new MockMultipartFile("file", xls ? "基金.xls" : "基金.xlsx", "application/octet-stream", output.toByteArray());
        }
    }

    /** 为独立来源提交列表提供类型安全的捕获器。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<List<FundPoolAdjustSubmitReq>> listCaptor() {
        return ArgumentCaptor.forClass((Class) List.class);
    }
}
