package com.znty.rrs.service;

import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.forbiddenpoolquery.ForbiddenPoolQueryDto;
import com.znty.rrs.entity.forbiddenpoolquery.ForbiddenPoolQueryReq;
import com.znty.rrs.mapper.ForbiddenPoolQueryMapper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 禁投池查询的 Excel 导出测试 */
public class ForbiddenPoolQueryServiceTest {

    /** 到期日列读取到期日字段，单条、多条和无结果导出均使用相同模板 */
    @Test
    public void exportShouldUseMaturityDate() throws Exception {
        ForbiddenPoolQueryMapper mapper = mock(ForbiddenPoolQueryMapper.class);
        ForbiddenPoolQueryService service = new ForbiddenPoolQueryService();
        ReflectionTestUtils.setField(service, "forbiddenPoolQueryMapper", mapper);
        ReflectionTestUtils.setField(service, "investmentPoolService", mock(InvestmentPoolService.class));

        ForbiddenPoolQueryDto bond = new ForbiddenPoolQueryDto();
        bond.setSecurityCode("BOND1.IB");
        bond.setMaturityDate("20310101");
        bond.setDelistDate("20290318");
        ForbiddenPoolQueryDto bondWithoutMaturityDate = new ForbiddenPoolQueryDto();
        bondWithoutMaturityDate.setSecurityCode("BOND2.IB");
        bondWithoutMaturityDate.setDelistDate("20290318");
        ForbiddenPoolQueryReq req = new ForbiddenPoolQueryReq();
        when(mapper.queryForbiddenPoolPage(req))
                .thenReturn(Collections.emptyList(), Collections.singletonList(bond),
                        Arrays.asList(bond, bondWithoutMaturityDate));

        try (XSSFWorkbook workbook = openExport(service.exportForbiddenPoolExcel(req))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(dateColumnValue(sheet, 0)).isEqualTo("到期日");
            assertThat(dateColumnValue(sheet, 1)).isEmpty();
        }
        try (XSSFWorkbook workbook = openExport(service.exportForbiddenPoolExcel(req))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(dateColumnValue(sheet, 1)).isEqualTo("20310101");
        }
        try (XSSFWorkbook workbook = openExport(service.exportForbiddenPoolExcel(req))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(dateColumnValue(sheet, 1)).isEqualTo("20310101");
            assertThat(dateColumnValue(sheet, 2)).isEmpty();
        }
    }

    private XSSFWorkbook openExport(CommonFileDto file) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(Base64.getDecoder().decode(file.getContentBase64())));
    }

    private String dateColumnValue(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        return row == null ? "" : new DataFormatter().formatCellValue(row.getCell(8));
    }
}
