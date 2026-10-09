package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.entity.bo.StockInfoBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SysImpTmpBo;
import com.znty.rrs.entity.bo.SysImpTmpDetlBo;
import com.znty.rrs.entity.stockpooladjust.StockAdjustCheckDto;
import com.znty.rrs.entity.stockpooladjust.StockAdjustCheckReq;
import com.znty.rrs.entity.stockpooladjust.StockAdjustSubmitDto;
import com.znty.rrs.entity.stockpooladjust.StockPoolAdjustSubmitReq;
import com.znty.rrs.entity.stockpoolexcelimport.StockPoolExcelImportCheckItemDto;
import com.znty.rrs.entity.stockpoolexcelimport.StockPoolExcelImportDto;
import com.znty.rrs.entity.stockpoolexcelimport.StockPoolExcelImportReq;
import com.znty.rrs.entity.investmentpool.InvestmentPoolDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.StockPoolAdjustMapper;
import com.znty.rrs.mapper.StockPoolExcelImportMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.poi.ss.usermodel.Workbook;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 股票 Excel 导入的来源、清空、冲突及快照提交行为测试。 */
public class StockPoolExcelImportServiceTest {
    /** 被测导入服务。 */
    private StockPoolExcelImportService service;
    /** 临时表及池查询组件。 */
    private StockPoolExcelImportMapper mapper;
    /** 股票业务组件。 */
    private StockPoolAdjustService adjustService;
    /** 股票基础信息及在途组件。 */
    private StockPoolAdjustMapper stockMapper;
    /** 权限组件。 */
    private InvestmentPoolMapper poolMapper;
    /** 当前模拟批次。 */
    private SysImpTmpBo batch;
    /** 当前模拟明细。 */
    private List<SysImpTmpDetlBo> rows;

    /** 构建不连接数据库的行为测试环境。 */
    @Before
    public void setUp() {
        service = new StockPoolExcelImportService();
        mapper = mock(StockPoolExcelImportMapper.class);
        adjustService = mock(StockPoolAdjustService.class);
        stockMapper = mock(StockPoolAdjustMapper.class);
        poolMapper = mock(InvestmentPoolMapper.class);
        SysAttachmentService attachmentService = mock(SysAttachmentService.class);
        ReflectionTestUtils.setField(service, "stockPoolExcelImportMapper", mapper);
        ReflectionTestUtils.setField(service, "stockPoolAdjustService", adjustService);
        ReflectionTestUtils.setField(service, "stockPoolAdjustMapper", stockMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", attachmentService);
        batch = new SysImpTmpBo();
        batch.setImpId("IMPORT");
        batch.setBizType("stock_pool_excel");
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
            pool.setPoolType("stock");
            return Collections.singletonList(pool);
        });
        when(stockMapper.queryStockByCode(anyString())).thenAnswer(invocation -> {
            StockInfoBo stock = new StockInfoBo();
            stock.setStockCode(invocation.getArgument(0));
            stock.setStockName("权威股票名称");
            stock.setStockShortName("权威简称");
            stock.setSecurityType("stock_a");
            return stock;
        });
        when(adjustService.checkExcelImportAdjust(any(), eq("1"))).thenAnswer(invocation -> {
            StockAdjustCheckReq request = invocation.getArgument(0);
            // 生成股票业务组件正常返回的一般流程结果
            return result(request, null);
        });
        when(attachmentService.parseOriginalFileNameListJson(any())).thenReturn(Collections.emptyList());
        when(attachmentService.resolveUploadOriginalFileName(any(), anyList())).thenReturn("股票.xlsx");
        StockAdjustSubmitDto submitted = new StockAdjustSubmitDto();
        submitted.setAdjustLogIds(Collections.singletonList(101L));
        submitted.setAdjustBatchNos(Collections.singletonList("STOCK001"));
        when(adjustService.addExcelImportAdjustLogList(anyList(), anyBoolean())).thenReturn(Collections.singletonList(submitted));
    }

    /** 清理 Mock Mapper 未消费的分页线程状态。 */
    @After
    public void tearDown() {
        PageHelper.clearPage();
    }

    /** 四列原文、32 位批次号和权威名称在上传时保存。 */
    @Test
    public void uploadShouldPreserveRawFieldsAndAuthoritativeName() throws Exception {
        // 使用真实 POI 文件验证解析路径
        MockMultipartFile file = workbookFile(new String[][] {{"父池", "池10", "伪造名称", "F1"}});
        // 构造管理员上传请求，关联前面生成的四列文件
        StockPoolExcelImportReq request = request();
        request.setDirection("in");
        StockPoolExcelImportDto dto = service.uploadExcel(request, file, null);
        assertEquals(32, dto.getImpId().length());
        assertEquals("权威股票名称", rows.get(0).getFld002());
        assertEquals(2, rows.get(0).getRowNo().intValue());
        assertFalse(dto.getCheckDone());
    }

    /** 四列模板缺列应直接拒绝。 */
    @Test
    public void uploadShouldRejectMissingHeader() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet().createRow(0).createCell(0).setCellValue("股票代码");
            workbook.getSheetAt(0).createRow(1).createCell(0).setCellValue("F1");
            workbook.write(output);
            // 构造缺列文件的上传请求
            StockPoolExcelImportReq request = request();
            request.setDirection("in");
            try {
                service.uploadExcel(request, new MockMultipartFile("file", "test.xlsx", "application/octet-stream", output.toByteArray()), null);
                fail("缺列模板应失败");
            } catch (BizException ex) {
                assertTrue(ex.getMessage().contains("四列"));
            }
        }
    }

    /** 文件大小和方向和清空选项在上传前校验。 */
    @Test
    public void uploadShouldValidateFileSizeAndClearFields() {
        // 构造文件大小与清空必填字段的验证请求
        StockPoolExcelImportReq request = request();
        request.setDirection("in");
        try {
            service.uploadExcel(request, new MockMultipartFile("file", "test.xlsx", "application/octet-stream", new byte[5 * 1024 * 1024 + 1]), null);
            fail("超大文件应失败");
        } catch (BizException ex) {
            assertTrue(ex.getMessage().contains("5MB"));
        }
        request.setClearTarget(true);
        request.setDirection("out");
        try {
            service.uploadExcel(request, new MockMultipartFile("file", "test.xlsx", "application/octet-stream", new byte[] {1}), null);
            fail("调出不能清空");
        } catch (BizException ex) {
            assertTrue(ex.getMessage().contains("清空"));
        }
    }

    /** 失败导入行仍属清空保留集合，在途差集成员跳过。 */
    @Test
    public void clearShouldReserveFailedRowsAndAllowOnlyValidClearSubmission() {
        batch.setOptionJson("{\"clearTarget\":true,\"allowLinkMutex\":false}");
        // 可识别但报告校验失败的来源行仍进入保留集合
        rows.add(row(1L, "F1", 10L));
        when(adjustService.checkExcelImportAdjust(any(), eq("1"))).thenAnswer(invocation -> {
            StockAdjustCheckReq req = invocation.getArgument(0);
            return result(req, "F1".equals(req.getStockCode()) ? "股票报告未提供" : null);
        });
        StockInfoBo keep = new StockInfoBo();
        keep.setStockCode("F1");
        StockInfoBo clear = new StockInfoBo();
        clear.setStockCode("F2");
        StockInfoBo pending = new StockInfoBo();
        pending.setStockCode("F3");
        when(mapper.queryPoolMemberList(10L)).thenReturn(Arrays.asList(keep, clear, pending));
        when(stockMapper.queryStockHasPendingProcess(eq("F3"), eq(10L), isNull())).thenReturn(true);
        // 构造校验请求，验证失败行保留集合和在途差集成员跳过
        StockPoolExcelImportDto checked = service.checkImport(request());
        assertEquals(2, checked.getCheckItems().size());
        assertEquals("F2", checked.getCheckItems().get(0).getStockCode());
        assertEquals("clear", checked.getCheckItems().get(0).getItemTag());
        // 构造提交请求，验证仅有效清空来源组生成审批申请
        assertEquals("STOCK001", service.submitImport(request()).getAdjustBatchNoList().get(0));
        // 构造类型安全捕获器，核对清空来源组的提交字段
        ArgumentCaptor<List<StockPoolAdjustSubmitReq>> captured = listCaptor();
        verify(adjustService).addExcelImportAdjustLogList(captured.capture(), eq(false));
        assertEquals(1, captured.getValue().size());
        assertEquals("Excel清空", captured.getValue().get(0).getAdjustType());
        assertEquals("manual", captured.getValue().get(0).getItems().get(0).getItemTag());
        assertEquals(AdjustMode.OUT.getCode(), captured.getValue().get(0).getItems().get(0).getAdjustMode());
        assertEquals("0", rows.get(0).getSaveRslt());
    }

    /** 客户端不能改变上传选项及股票业务数据，同代码不同池仍独立提交。 */
    @Test public void submitShouldUsePersistentSourcesAndIgnoreClientBusinessValues() {
        rows.add(row(1L, "F1", 10L)); rows.add(row(2L, "F1", 20L)); batch.setTotalCount(2);
        StockPoolExcelImportDto checked = service.checkImport(request());
        checked.getCheckItems().forEach(item -> { item.setCanAdjust(false); item.setPoolName("伪造名称"); item.setAdjustType("伪造类型"); });
        StockPoolExcelImportReq req = request(); req.setCheckItems(checked.getCheckItems());
        req.setDirection("out"); req.setClearTarget(true); req.setAllowLinkMutex(false);
        service.submitImport(req);
        ArgumentCaptor<List<StockPoolAdjustSubmitReq>> captured = listCaptor();
        verify(adjustService).addExcelImportAdjustLogList(captured.capture(), eq(true));
        assertEquals(2, captured.getValue().size());
        assertEquals("Excel导入", captured.getValue().get(0).getAdjustType());
        assertEquals(AdjustMode.IN.getCode(), captured.getValue().get(0).getItems().get(0).getAdjustMode());
        assertEquals("F1", captured.getValue().get(0).getStockCode());
    }

    /** 跨来源组关系项同股票同池冲突应阻断所有相关组并定位行号。 */
    @Test
    public void overlappingRelationTargetsShouldBlockAllSourceGroups() {
        // 构造第一个来源行，用于展开共享目标池的关系项
        rows.add(row(1L, "F1", 10L));
        // 构造另一来源行，验证相同关系目标池的跨组冲突
        rows.add(row(2L, "F1", 20L));
        batch.setTotalCount(2);
        when(adjustService.checkExcelImportAdjust(any(), eq("1"))).thenAnswer(invocation -> {
            // 不同方向的相同关系目标池也视为冲突
            StockAdjustCheckReq request = invocation.getArgument(0);
            // 构造通过的主项校验结果，供追加冲突关系项
            StockAdjustCheckDto dto = result(request, null);
            // 构造共享目标池的联动项，分别使用调入和调出方向
            StockAdjustCheckDto.CheckResultItem relation = resultItem(request.getStockCode(), 30L,
                    request.getItems().get(0).getTargetPoolId() == 10L ? AdjustMode.IN.getCode() : AdjustMode.OUT.getCode(), "linkage");
            dto.getItems().add(relation);
            return dto;
        });
        // 构造校验请求，检查所有冲突来源组均被阻断
        StockPoolExcelImportDto dto = service.checkImport(request());
        assertEquals(4, dto.getCheckItems().size());
        assertTrue(dto.getCheckItems().stream().noneMatch(StockPoolExcelImportCheckItemDto::isCanAdjust));
        assertTrue(rows.get(0).getChkDscr().contains("第 2 行"));
        assertTrue(rows.get(1).getChkDscr().contains("第 3 行"));
    }

    /** 主项报告失败时，其通过的关系项不得独立提交。 */
    @Test
    public void failedReportPrimaryShouldBlockRelations() {
        // 构造报告限制用例的主项来源行
        rows.add(row(1L, "F1", 10L));
        when(adjustService.checkExcelImportAdjust(any(), eq("1"))).thenAnswer(invocation -> {
            StockAdjustCheckReq request = invocation.getArgument(0);
            // 报告失败由股票内部导入检查入口提前返回
            StockAdjustCheckDto dto = result(request, "Excel 导入暂不支持提交股票报告");
            // 构造通过的联动项，验证失败主项仍阻止该项独立提交
            dto.getItems().add(resultItem("F1", 20L, AdjustMode.IN.getCode(), "linkage"));
            return dto;
        });
        // 构造校验请求，验证报告主项与关系项的提交门禁
        StockPoolExcelImportDto dto = service.checkImport(request());
        assertTrue(dto.getCheckItems().stream().noneMatch(StockPoolExcelImportCheckItemDto::isCanAdjust));
        assertTrue(rows.get(0).getChkDscr().contains("股票报告"));
    }

    /** 即使股票调整检查通过，缺失 Excel 导入权限仍不能提交。 */
    @Test
    public void checkShouldRequireExcelPermissionInAdditionToStockCheck() {
        // 构造调整规则通过但需额外检查导入权限的来源行
        rows.add(row(1L, "F1", 10L));
        // 构造用于模拟无 Excel 导入权限用户的校验请求
        StockPoolExcelImportReq request = request();
        request.setCurrentUserId("2");
        // 构造股票调整检查通过的结果，将阻断原因限定为导入权限
        when(adjustService.checkExcelImportAdjust(any(), eq("2"))).thenAnswer(invocation -> result(invocation.getArgument(0), null));
        doThrow(new BizException("没有 Excel 导入权限")).when(adjustService).validateExcelImportPermission("2", 10L);
        assertFalse(service.checkImport(request).getCheckItems().get(0).isCanAdjust());
        assertTrue(rows.get(0).getChkDscr().contains("Excel 导入"));
    }

    /** 客户端不能选择未出现在服务器快照的一般流程。 */
    @Test
    public void submitShouldRejectForgedFlowSelection() {
        // 构造用于流程防篡改验证的来源行
        rows.add(row(1L, "F1", 10L));
        // 构造校验请求并读取服务器实际保存的流程候选
        StockPoolExcelImportDto checked = service.checkImport(request());
        checked.getCheckItems().get(0).setSelectedFlowId(999L);
        // 构造提交请求，回传未出现在服务器候选中的流程
        StockPoolExcelImportReq request = request();
        request.setCheckItems(checked.getCheckItems());
        try {
            service.submitImport(request);
            fail("伪造流程应失败");
        } catch (BizException ex) {
            assertTrue(ex.getMessage().contains("一般审批流程"));
        }
        verify(adjustService, never()).addExcelImportAdjustLogList(anyList(), anyBoolean());
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
            // 构造其他业务批次的查询请求，验证股票导入隔离
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
        StockPoolExcelImportReq req = request();
        req.setDirection("in");
        req.setAdjustReason(text);
        req.setAdjustAdvice(text);
        // 构造四列文件并上传，验证长文保存不使用 VARCHAR(200) 业务槽
        StockPoolExcelImportDto dto = service.uploadExcel(req,
                workbookFile(new String[][] {{"父池", "池10", "名称", "F1"}}), null);
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
            // 全部来源使用同一股票和池，不允许静默合并
            rows.add(row(index, "F1", 10L));
        }
        batch.setTotalCount(20);
        // 构造校验请求，验证大量冲突来源的定位摘要受到长度约束
        StockPoolExcelImportDto dto = service.checkImport(request());
        assertTrue(dto.getCheckItems().stream().noneMatch(StockPoolExcelImportCheckItemDto::isCanAdjust));
        assertTrue(rows.stream().allMatch(item -> item.getChkDscr().length() <= 500));
        assertTrue(rows.get(0).getChkDscr().contains("共 20 个来源组"));
        assertTrue(batch.getResultJson().length() < 30000);
    }

    /** 小写股票代码仍通过主档权威代码加入清空保留集合。 */
    @Test
    public void clearShouldReserveCanonicalCodeEvenWhenReportRequired() {
        batch.setOptionJson("{\"clearTarget\":true,\"allowLinkMutex\":false}");
        // 模拟 MySQL ai_ci 按原始代码查询成功后返回主档规范代码
        rows.add(row(1L, "STOCK001.sh", 10L));
        StockInfoBo stock = new StockInfoBo();
        stock.setStockCode("STOCK001.SH");
        stock.setStockName("权威股票名称");
        when(stockMapper.queryStockByCode("STOCK001.sh")).thenReturn(stock);
        when(mapper.queryPoolMemberList(10L)).thenReturn(Collections.singletonList(stock));
        // 构造校验请求，验证来源行按主档规范代码保留股票
        StockPoolExcelImportDto dto = service.checkImport(request());
        assertEquals(1, dto.getCheckItems().size());
        assertEquals("STOCK001.SH", dto.getCheckItems().get(0).getStockCode());
        assertEquals("manual", dto.getCheckItems().get(0).getItemTag());
        assertEquals("STOCK001.sh", rows.get(0).getFld001());
    }

    /** 原始小写代码可正常提交，日志请求使用主档规范代码。 */
    @Test
    public void submitShouldUseCanonicalStockCodeAndRetainRawSourceCode() {
        // 构造代码后缀小写的来源行，验证提交采用主档规范代码
        rows.add(row(1L, "STOCK001.sh", 10L));
        StockInfoBo stock = new StockInfoBo();
        stock.setStockCode("STOCK001.SH");
        stock.setStockName("权威股票名称");
        when(stockMapper.queryStockByCode("STOCK001.sh")).thenReturn(stock);
        // 构造校验请求，识别原始代码对应的股票主档
        service.checkImport(request());
        // 构造提交请求，验证规范代码与原始来源代码同时保留
        service.submitImport(request());
        // 构造列表捕获器，核对实际提交的规范股票代码
        ArgumentCaptor<List<StockPoolAdjustSubmitReq>> captured = listCaptor();
        verify(adjustService).addExcelImportAdjustLogList(captured.capture(), eq(true));
        assertEquals("STOCK001.SH", captured.getValue().get(0).getStockCode());
        assertEquals("STOCK001.sh", rows.get(0).getFld001());
    }

    /** 大小写不同的原始代码识别为同一主档，不能产生重复来源申请。 */
    @Test
    public void canonicalCodeShouldIdentifyCaseVariantSourceConflicts() {
        // 构造使用小写后缀的第一个原始来源行
        rows.add(row(1L, "STOCK001.sh", 10L));
        // 构造规范后缀的第二个来源行，验证大小写变体仍属同一股票
        rows.add(row(2L, "STOCK001.SH", 10L));
        batch.setTotalCount(2);
        StockInfoBo stock = new StockInfoBo();
        stock.setStockCode("STOCK001.SH");
        stock.setStockName("权威股票名称");
        when(stockMapper.queryStockByCode("STOCK001.sh")).thenReturn(stock);
        // 构造校验请求，验证规范代码识别跨来源组冲突
        StockPoolExcelImportDto dto = service.checkImport(request());
        assertTrue(dto.getCheckItems().stream().noneMatch(StockPoolExcelImportCheckItemDto::isCanAdjust));
        assertTrue(rows.get(0).getChkDscr().contains("第 3 行"));
    }

    /** 首个 sheet 超过 2000 个非空数据行时整个上传明确拒绝。 */
    @Test
    public void uploadShouldRejectMoreThanTwoThousandDataRows() throws Exception {
        String[][] data = new String[2001][4];
        for (int index = 0; index < data.length; index++) {
            data[index] = new String[] {"父池", "池10", "名称", "F" + index};
        }
        // 构造超过数据行上限的上传请求
        StockPoolExcelImportReq req = request();
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
        StockPoolExcelImportReq req = request();
        req.setDirection("in");
        // 验证 HSSF 与 XSSF 都走同一四列解析流程
        StockPoolExcelImportDto dto = service.uploadExcel(req,
                workbookFile(new String[][] {{"父池", "池10", "名称", "F1"}}, true), null);
        assertEquals(1, dto.getTotalCount().intValue());
        try {
            service.uploadExcel(req, new MockMultipartFile("file", "test.csv", "text/plain", new byte[] {1}), null);
            fail("csv 应被拒绝");
        } catch (BizException ex) {
            assertTrue(ex.getMessage().contains("xls / xlsx"));
        }
    }

    /** 构造标准批次请求。 */
    private StockPoolExcelImportReq request() {
        StockPoolExcelImportReq req = new StockPoolExcelImportReq();
        req.setImpId("IMPORT");
        req.setCurrentUserId("1");
        return req;
    }

    /**
     * 构造原始来源行。
     *
     * @param id 来源明细 ID，同时用于确定物理行号
     * @param code Excel 原始股票代码
     * @param poolId 通过子池名称关联的目标池 ID
     */
    private SysImpTmpDetlBo row(Long id, String code, Long poolId) {
        SysImpTmpDetlBo row = new SysImpTmpDetlBo();
        row.setId(id);
        row.setImpId("IMPORT");
        row.setRowNo(id.intValue() + 1);
        row.setFld001(code);
        row.setFld003("父池");
        row.setFld004("池" + poolId);
        row.setChkRslt("0");
        row.setSaveRslt("0");
        return row;
    }

    /**
     * 构造股票检查返回值。
     *
     * @param request 股票代码和主项目标池、方向的校验请求
     * @param failure 需要附加的主项阻断原因，为空时保持通过
     */
    private StockAdjustCheckDto result(StockAdjustCheckReq request, String failure) {
        StockAdjustCheckReq.CheckItem primary = request.getItems().get(0);
        // 映射调用参数为股票组件的单项结果
        StockAdjustCheckDto.CheckResultItem item = resultItem(request.getStockCode(), primary.getTargetPoolId(), primary.getAdjustMode(), "manual");
        if (failure != null) {
            item.setCanAdjust(false);
            item.getFailReasons().add(failure);
        }
        StockAdjustCheckDto dto = new StockAdjustCheckDto();
        dto.setItems(new ArrayList<>(Collections.singletonList(item)));
        return dto;
    }

    /**
     * 构造一般流程校验项。
     *
     * @param code 股票主档代码
     * @param poolId 调整项的目标池 ID
     * @param mode 股票内部调入或调出编码
     * @param tag 手工或关系项来源标签
     */
    private StockAdjustCheckDto.CheckResultItem resultItem(String code, Long poolId, String mode, String tag) {
        StockAdjustCheckDto.CheckResultItem item = new StockAdjustCheckDto.CheckResultItem();
        item.setStockCode(code);
        item.setStockShortName("权威简称");
        item.setTargetPoolId(poolId);
        item.setPoolName("父池/池" + poolId);
        item.setPoolType("stock");
        item.setAdjustMode(mode);
        item.setItemTag(tag);
        item.setCanAdjust(true);
        item.setFailReasons(new ArrayList<>());
        item.setWarnings(Collections.emptyList());
        StockAdjustCheckDto.FlowOption option = new StockAdjustCheckDto.FlowOption();
        option.setFlowId(AdjustMode.IN.getCode().equals(mode) ? 121L : 122L);
        option.setFlowKey(AdjustMode.IN.getCode().equals(mode) ? "stock:normal-in" : "stock:normal-out");
        option.setFlowType(AdjustMode.IN.getCode().equals(mode) ? "normalInbound" : "normalOutbound");
        option.setSelectable(true);
        item.setFlowOptions(Collections.singletonList(option));
        return item;
    }

    /**
     * 构造真实四列表头工作簿。
     *
     * @param data 按固定四列顺序排列的数据行
     */
    private MockMultipartFile workbookFile(String[][] data) throws Exception {
        // 缺省使用 xlsx 文件构造器
        return workbookFile(data, false);
    }

    /**
     * 构造指定 xls/xlsx 格式的真实四列工作簿。
     *
     * @param data 按固定四列顺序排列的数据行
     * @param xls 是否使用旧版 xls 格式，为否则使用 xlsx
     */
    private MockMultipartFile workbookFile(String[][] data, boolean xls) throws Exception {
        try (Workbook workbook = xls ? new HSSFWorkbook() : new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet();
            String[] headers = {"父池名称", "子池名称", "股票名称", "股票代码"};
            workbook.getSheetAt(0).createRow(0);
            for (int column = 0; column < headers.length; column++) {
                workbook.getSheetAt(0).getRow(0).createCell(column).setCellValue(headers[column]);
            }
            for (int index = 0; index < data.length; index++) {
                workbook.getSheetAt(0).createRow(index + 1);
                for (int column = 0; column < headers.length; column++) {
                    workbook.getSheetAt(0).getRow(index + 1).createCell(column).setCellValue(data[index][column]);
                }
            }
            workbook.write(output);
            return new MockMultipartFile("file", xls ? "股票.xls" : "股票.xlsx", "application/octet-stream", output.toByteArray());
        }
    }

    /** 为独立来源提交列表提供类型安全的捕获器。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<List<StockPoolAdjustSubmitReq>> listCaptor() {
        return ArgumentCaptor.forClass((Class) List.class);
    }
}
