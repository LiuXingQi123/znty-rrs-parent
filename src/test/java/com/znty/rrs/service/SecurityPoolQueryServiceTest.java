package com.znty.rrs.service;

import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.securitypoolquery.SecurityPoolQueryDto;
import com.znty.rrs.entity.securitypoolquery.SecurityPoolQueryReq;
import com.znty.rrs.mapper.MySecurityPoolMapper;
import com.znty.rrs.mapper.SecurityPoolQueryMapper;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 证券池查询服务测试。 */
public class SecurityPoolQueryServiceTest {

    /** 验证导出使用模板并转换页面展示字段。 */
    @Test
    public void exportSecurityPoolExcelShouldFillTemplateWithDisplayValues() throws Exception {
        SecurityPoolQueryMapper queryMapper = mock(SecurityPoolQueryMapper.class);
        InvestmentPoolService investmentPoolService = mock(InvestmentPoolService.class);
        SecurityPoolQueryService service = new SecurityPoolQueryService();
        ReflectionTestUtils.setField(service, "securityPoolQueryMapper", queryMapper);
        ReflectionTestUtils.setField(service, "mySecurityPoolMapper", mock(MySecurityPoolMapper.class));
        ReflectionTestUtils.setField(service, "investmentPoolService", investmentPoolService);
        SecurityPoolQueryDto row = new SecurityPoolQueryDto();
        row.setSecurityShortName("测试债券"); row.setSecurityCode("100001"); row.setTargetPoolId(2L);
        row.setDateExists(new BigDecimal("9999")); row.setDateExistsStr("1年");
        row.setSecurityStatus("active"); row.setAbsFlag(1);
        row.setEntryTime(new Date());
        when(queryMapper.querySecurityPoolPage(org.mockito.ArgumentMatchers.any(SecurityPoolQueryReq.class)))
                .thenReturn(Arrays.asList(row));
        when(investmentPoolService.queryPoolFullNameMap()).thenReturn(Collections.singletonMap(2L, "一级池/二级池"));

        CommonFileDto file = service.exportSecurityPoolExcel(new SecurityPoolQueryReq());

        assertThat(file.getFileName()).startsWith("证券池查询_");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(Base64.getDecoder().decode(file.getContentBase64())))) {
            assertThat(workbook.getFontAt(workbook.getSheetAt(0).getRow(0).getCell(0).getCellStyle().getFontIndex()).getFontName()).isEqualTo("楷体");
            assertThat(workbook.getFontAt(workbook.getSheetAt(0).getRow(0).getCell(0).getCellStyle().getFontIndex()).getBold()).isTrue();
            assertThat(workbook.getFontAt(workbook.getSheetAt(0).getRow(1).getCell(0).getCellStyle().getFontIndex()).getFontName()).isEqualTo("楷体");
            assertThat(workbook.getFontAt(workbook.getSheetAt(0).getRow(1).getCell(0).getCellStyle().getFontIndex()).getBold()).isFalse();
            assertThat(workbook.getSheetAt(0).getRow(0).getHeightInPoints()).isEqualTo(20.0F);
            assertThat(workbook.getSheetAt(0).getRow(1).getHeightInPoints()).isEqualTo(20.0F);
            assertThat(workbook.getSheetAt(0).getColumnWidth(0)).isEqualTo(15 * 256);
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getCellStyle().getVerticalAlignment()).isEqualTo(VerticalAlignment.CENTER);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getCellStyle().getVerticalAlignment()).isEqualTo(VerticalAlignment.CENTER);
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getCellStyle().getBorderBottom()).isEqualTo(BorderStyle.NONE);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getCellStyle().getBorderBottom()).isEqualTo(BorderStyle.NONE);
            assertThat(workbook.getSheetAt(0).isDisplayGridlines()).isTrue();
            assertThat(workbook.getSheetAt(0).getSheetConditionalFormatting().getNumConditionalFormattings()).isEqualTo(6);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("测试债券");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(4).getStringCellValue()).isEqualTo("一级池/二级池");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(12).getStringCellValue()).isEqualTo("1.0000");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(13).getStringCellValue()).isEqualTo("存续");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(16).getStringCellValue()).isEqualTo("是");
        }
    }
}
