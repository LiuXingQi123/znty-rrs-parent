package com.znty.rrs.service;

import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.companypool.CompanyPoolQueryDto;
import com.znty.rrs.entity.companypool.CompanyPoolQueryReq;
import com.znty.rrs.mapper.CompanyPoolQueryMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 主体池查询导出服务测试。
 */
public class CompanyPoolQueryExportServiceTest {

    /** 验证导出字段与页面列一致。 */
    @Test
    public void exportCompanyPoolExcelShouldFillTemplateWithDisplayValues() throws Exception {
        CompanyPoolQueryMapper queryMapper = mock(CompanyPoolQueryMapper.class);
        InvestmentPoolService investmentPoolService = mock(InvestmentPoolService.class);
        CompanyPoolQueryService service = new CompanyPoolQueryService();
        ReflectionTestUtils.setField(service, "companyPoolQueryMapper", queryMapper);
        ReflectionTestUtils.setField(service, "investmentPoolService", investmentPoolService);

        CompanyPoolQueryDto row = new CompanyPoolQueryDto();
        row.setSecurityShortName("交投集团");
        row.setSecurityCode("C10001");
        row.setAdjusterName("管理员");
        row.setTargetPoolId(2L);
        row.setEntryTime(new Date());
        when(investmentPoolService.queryPermittedPoolIdsByUser(eq("1"), any(String.class)))
                .thenReturn(new HashSet<>(Collections.singletonList(2L)));
        when(queryMapper.queryCompanyPoolPage(any(CompanyPoolQueryReq.class)))
                .thenReturn(Arrays.asList(row));
        when(investmentPoolService.queryPoolFullNameMap())
                .thenReturn(Collections.singletonMap(2L, "主体池/白名单"));

        CompanyPoolQueryReq req = new CompanyPoolQueryReq();
        req.setCurrentUserId("1");
        CommonFileDto file = service.exportCompanyPoolExcel(req);

        assertThat(file.getFileName()).startsWith("主体池查询_");
        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(Base64.getDecoder().decode(file.getContentBase64())))) {
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("主体名称");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("交投集团");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(4).getStringCellValue()).isEqualTo("主体池/白名单");
        }
    }
}
