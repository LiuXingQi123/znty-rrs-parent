package com.znty.rrs.service;

import com.znty.rrs.common.util.ExcelImportHelper;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.commonfile.CommonFileReq;
import com.znty.rrs.exception.BizException;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 公共文件服务测试（读取 classpath 模板）
 */
public class CommonFileServiceTest {

    private final CommonFileService service = new CommonFileService();

    @Test
    public void downloadTemplate_SecurityPoolImport_ReturnsBytes() {
        CommonFileReq req = new CommonFileReq();
        req.setTemplateCode("security_pool_import");
        CommonFileDto dto = service.downloadTemplate(req);
        assertNotNull(dto);
        assertNotNull(dto.getContentBase64());
        assertTrue(dto.getContentBase64().length() > 0);
        assertTrue(dto.getFileSize() != null && dto.getFileSize() > 0);
    }

    /** 验证 CRMW 池导入模板可从 classpath 正常读取。 */
    @Test
    public void downloadTemplateShouldReturnCrmwPoolImportBytes() {
        CommonFileReq req = new CommonFileReq();
        req.setTemplateCode("crmw_pool_import");
        CommonFileDto dto = service.downloadTemplate(req);
        assertNotNull(dto);
        assertNotNull(dto.getContentBase64());
        assertTrue(dto.getContentBase64().length() > 0);
        assertTrue(dto.getFileSize() != null && dto.getFileSize() > 0);
    }

    /** 基金模板须可下载且首表固定七列，说明位于第二表。 */
    @Test
    public void downloadFundTemplateShouldExposeSevenHeadersAndValidation() throws Exception {
        CommonFileReq req = new CommonFileReq();
        req.setTemplateCode("fund_pool_import");
        CommonFileDto dto = service.downloadTemplate(req);
        assertEquals("fund_pool_import.xlsx", dto.getFileName());
        byte[] bytes = Base64.getDecoder().decode(dto.getContentBase64());
        assertEquals(bytes.length, dto.getFileSize().longValue());
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            String[] headers = {"父池名称", "子池名称", "基金名称", "基金代码", "基金评分", "基金投资类型", "风管领导审批"};
            assertEquals(7, workbook.getSheetAt(0).getRow(0).getLastCellNum());
            for (int column = 0; column < headers.length; column++) {
                assertEquals(headers[column], workbook.getSheetAt(0).getRow(0).getCell(column).getStringCellValue());
            }
            assertEquals("填写说明", workbook.getSheetAt(1).getSheetName());
            assertTrue(workbook.getSheetAt(0).getDataValidations().size() >= 2);
        }
    }

    /** 股票模板固定四列、附说明页并保留文本股票代码格式。 */
    @Test public void downloadStockTemplateShouldExposeFourHeadersAndTextCodes() throws Exception {
        CommonFileReq req = new CommonFileReq(); req.setTemplateCode("stock_pool_import");
        CommonFileDto dto = service.downloadTemplate(req);
        assertEquals("stock_pool_import.xlsx", dto.getFileName());
        try (Workbook book = new XSSFWorkbook(new ByteArrayInputStream(Base64.getDecoder().decode(dto.getContentBase64())))) {
            String[] headers = {"父池名称", "子池名称", "股票名称", "股票代码"};
            assertEquals(4, book.getSheetAt(0).getRow(0).getLastCellNum());
            for (int column = 0; column < 4; column++) { assertEquals(headers[column], book.getSheetAt(0).getRow(0).getCell(column).getStringCellValue()); }
            assertEquals("@", book.getSheetAt(0).getRow(1).getCell(3).getCellStyle().getDataFormatString());
            assertEquals("填写说明", book.getSheetAt(1).getSheetName());
        }
    }

    @Test
    public void downloadStockTemplateShouldParseExactlyFiveDemoRows() {
        CommonFileReq req = new CommonFileReq();
        req.setTemplateCode("stock_pool_import");
        CommonFileDto dto = service.downloadTemplate(req);
        MockMultipartFile file = new MockMultipartFile("file", dto.getFileName(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Base64.getDecoder().decode(dto.getContentBase64()));
        List<Map<String, String>> rows = ExcelImportHelper.parseFirstSheet(file, 2000);
        assertEquals(5, rows.size());
        String[] codes = {"STOCK003.SH", "STOCK009.HK", "STOCK005.HK", "STOCK011.SZ", "STOCK013.SZ"};
        for (int index = 0; index < rows.size(); index++) {
            assertEquals(codes[index], rows.get(index).get("股票代码"));
            assertEquals(String.valueOf(index + 2), rows.get(index).get("__rowNo"));
            assertEquals(index == 1 ? "公司港股库" : "公司股票库", rows.get(index).get("父池名称"));
            assertEquals(index == 1 ? "港股基础库" : "基础库", rows.get(index).get("子池名称"));
            assertTrue(!rows.get(index).get("股票名称").isEmpty());
        }
    }

    @Test
    public void downloadTemplate_UnknownCode_Throws() {
        CommonFileReq req = new CommonFileReq();
        req.setTemplateCode("not_exists_tpl");
        try {
            service.downloadTemplate(req);
            fail();
        } catch (BizException e) {
            assertTrue(e.getMessage().contains("未知模板编码"));
        }
    }
}
