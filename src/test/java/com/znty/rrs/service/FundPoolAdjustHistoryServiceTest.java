package com.znty.rrs.service;

import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.fundpooladjusthistory.FundPoolAdjustHistoryDto;
import com.znty.rrs.entity.fundpooladjusthistory.FundPoolAdjustHistoryReq;
import com.znty.rrs.mapper.FundPoolAdjustHistoryMapper;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 基金池调整历史服务测试。 */
public class FundPoolAdjustHistoryServiceTest {
    /** 验证导出字段顺序、投资池路径和审核状态中文转换。 */
    @Test
    public void exportShouldWriteHistoryColumnsAndLabels() throws Exception {
        FundPoolAdjustHistoryMapper mapper = mock(FundPoolAdjustHistoryMapper.class);
        InvestmentPoolService investmentPoolService = mock(InvestmentPoolService.class);
        FundPoolAdjustHistoryService service = new FundPoolAdjustHistoryService();
        ReflectionTestUtils.setField(service, "fundPoolAdjustHistoryMapper", mapper);
        ReflectionTestUtils.setField(service, "investmentPoolService", investmentPoolService);

        // 构造包含审核状态和投资池路径的历史记录
        FundPoolAdjustHistoryDto row = new FundPoolAdjustHistoryDto();
        row.setAdjusterName("研究员1");
        row.setSubmitTime(new Date());
        row.setFundName("测试基金");
        row.setFundCode("FUND001");
        row.setAdjustType("手工调整");
        row.setAdjustMode("调入");
        row.setTargetPoolId(145L);
        row.setAuditStatus("20");
        when(mapper.queryFundPoolAdjustHistoryPage(any(FundPoolAdjustHistoryReq.class)))
                .thenReturn(Collections.singletonList(row));
        when(investmentPoolService.queryPoolFullNameMap())
                .thenReturn(Collections.singletonMap(145L, "基金池/基础基金池"));

        // 执行模板导出并核对生成的文件名
        CommonFileDto file = service.exportFundPoolAdjustHistoryExcel(new FundPoolAdjustHistoryReq());

        assertThat(file.getFileName()).startsWith("基金池调整历史_");
        // 核对表头、数据行及模板格式
        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(Base64.getDecoder().decode(file.getContentBase64())))) {
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("调整人");
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(7).getStringCellValue()).isEqualTo("审核状态");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(2).getStringCellValue()).isEqualTo("测试基金");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(6).getStringCellValue())
                    .isEqualTo("基金池/基础基金池");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(7).getStringCellValue()).isEqualTo("审批通过");
            // 格式与证券池调整历史模板一致：楷体、带底色表头、20 磅行高和 15 字符列宽。
            for (int columnIndex = 0; columnIndex < 8; columnIndex++) {
                CellStyle headerStyle = workbook.getSheetAt(0).getRow(0).getCell(columnIndex).getCellStyle();
                CellStyle dataStyle = workbook.getSheetAt(0).getRow(1).getCell(columnIndex).getCellStyle();
                assertThat(workbook.getFontAt(headerStyle.getFontIndex()).getFontName()).isEqualTo("楷体");
                assertThat(workbook.getFontAt(headerStyle.getFontIndex()).getBold()).isTrue();
                assertThat(workbook.getFontAt(dataStyle.getFontIndex()).getFontName()).isEqualTo("楷体");
                assertThat(workbook.getFontAt(dataStyle.getFontIndex()).getBold()).isFalse();
                assertThat(headerStyle.getFillForegroundColor()).isEqualTo((short) 44);
                assertThat(workbook.getSheetAt(0).getColumnWidth(columnIndex)).isEqualTo(15 * 256);
                assertThat(headerStyle.getVerticalAlignment()).isEqualTo(VerticalAlignment.CENTER);
                assertThat(dataStyle.getVerticalAlignment()).isEqualTo(VerticalAlignment.CENTER);
                assertThat(headerStyle.getBorderBottom()).isEqualTo(BorderStyle.NONE);
                assertThat(dataStyle.getBorderBottom()).isEqualTo(BorderStyle.NONE);
            }
            assertThat(workbook.getSheetAt(0).getRow(0).getHeightInPoints()).isEqualTo(20.0F);
            assertThat(workbook.getSheetAt(0).getRow(1).getHeightInPoints()).isEqualTo(20.0F);
            assertThat(workbook.getSheetAt(0).isDisplayGridlines()).isTrue();
        }
    }
}
