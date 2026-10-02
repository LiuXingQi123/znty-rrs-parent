package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.fundpoolquery.FundPoolQueryDto;
import com.znty.rrs.entity.fundpoolquery.FundPoolQueryReq;
import com.znty.rrs.mapper.FundPoolQueryMapper;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Collections;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 基金池查询服务测试。 */
public class FundPoolQueryServiceTest {
    /** 确认分页结果保留打开基金调库详情所需的日志上下文。 */
    @Test
    public void queryFundPoolPageShouldKeepAdjustContextForDetailNavigation() {
        FundPoolQueryMapper queryMapper = mock(FundPoolQueryMapper.class);
        InvestmentPoolService investmentPoolService = mock(InvestmentPoolService.class);
        FundPoolQueryService service = new FundPoolQueryService();
        ReflectionTestUtils.setField(service, "fundPoolQueryMapper", queryMapper);
        ReflectionTestUtils.setField(service, "investmentPoolService", investmentPoolService);
        FundPoolQueryDto row = new FundPoolQueryDto();
        row.setFundCode("FUND001");
        row.setAdjustLogId(12L);
        row.setAdjustBatchNo("FUND202609230001");
        row.setTargetPoolId(2L);
        when(queryMapper.queryFundPoolPage(org.mockito.ArgumentMatchers.any(FundPoolQueryReq.class)))
                .thenReturn(Collections.singletonList(row));
        when(investmentPoolService.queryPoolFullNameMap())
                .thenReturn(Collections.singletonMap(2L, "一级池/基金池"));

        try {
            PageResult<FundPoolQueryDto> result = service.queryFundPoolPage(new FundPoolQueryReq());

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).getAdjustLogId()).isEqualTo(12L);
            assertThat(result.getRecords().get(0).getAdjustBatchNo()).isEqualTo("FUND202609230001");
        } finally {
            PageHelper.clearPage();
        }
    }

    /** 验证导出字段与页面表格字段一致。 */
    @Test
    public void exportFundPoolExcelShouldWriteFundColumns() throws Exception {
        FundPoolQueryMapper queryMapper = mock(FundPoolQueryMapper.class);
        InvestmentPoolService investmentPoolService = mock(InvestmentPoolService.class);
        FundPoolQueryService service = new FundPoolQueryService();
        ReflectionTestUtils.setField(service, "fundPoolQueryMapper", queryMapper);
        ReflectionTestUtils.setField(service, "investmentPoolService", investmentPoolService);
        FundPoolQueryDto row = new FundPoolQueryDto();
        row.setFundName("测试基金");
        row.setFundCode("FUND001");
        row.setTargetPoolId(2L);
        when(queryMapper.queryFundPoolPage(org.mockito.ArgumentMatchers.any(FundPoolQueryReq.class)))
                .thenReturn(Collections.singletonList(row));
        when(investmentPoolService.queryPoolFullNameMap())
                .thenReturn(Collections.singletonMap(2L, "一级池/基金池"));

        CommonFileDto file = service.exportFundPoolExcel(new FundPoolQueryReq());

        assertThat(file.getFileName()).startsWith("基金池查询_");
        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(Base64.getDecoder().decode(file.getContentBase64())))) {
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("基金名称");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("测试基金");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(2).getStringCellValue()).isEqualTo("一级池/基金池");
            // 格式与证券池查询模板一致：楷体、带底色表头、20 磅行高和 15 字符列宽。
            assertThat(workbook.getFontAt(workbook.getSheetAt(0).getRow(0).getCell(0)
                    .getCellStyle().getFontIndex()).getFontName()).isEqualTo("楷体");
            assertThat(workbook.getFontAt(workbook.getSheetAt(0).getRow(0).getCell(0)
                    .getCellStyle().getFontIndex()).getBold()).isTrue();
            assertThat(workbook.getFontAt(workbook.getSheetAt(0).getRow(1).getCell(0)
                    .getCellStyle().getFontIndex()).getFontName()).isEqualTo("楷体");
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getCellStyle()
                    .getFillForegroundColor()).isEqualTo((short) 44);
            assertThat(workbook.getSheetAt(0).getRow(0).getHeightInPoints()).isEqualTo(20.0F);
            assertThat(workbook.getSheetAt(0).getRow(1).getHeightInPoints()).isEqualTo(20.0F);
            assertThat(workbook.getSheetAt(0).getColumnWidth(0)).isEqualTo(15 * 256);
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getCellStyle()
                    .getVerticalAlignment()).isEqualTo(VerticalAlignment.CENTER);
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getCellStyle()
                    .getBorderBottom()).isEqualTo(BorderStyle.NONE);
            assertThat(workbook.getSheetAt(0).isDisplayGridlines()).isTrue();
        }
    }
}
