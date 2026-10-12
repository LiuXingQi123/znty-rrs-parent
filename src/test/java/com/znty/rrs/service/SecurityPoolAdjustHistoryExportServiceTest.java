package com.znty.rrs.service;

import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.securitypooladjusthistory.SecurityPoolAdjustHistoryDto;
import com.znty.rrs.entity.securitypooladjusthistory.SecurityPoolAdjustHistoryReq;
import com.znty.rrs.mapper.SecurityPoolAdjustHistoryMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 证券池调整历史导出样式与字段测试。
 */
public class SecurityPoolAdjustHistoryExportServiceTest {

    /** 验证表头与是否列条件格式对齐证券池查询模板。 */
    @Test
    public void exportShouldAlignHeaderAndYesNoConditionalFormat() throws Exception {
        SecurityPoolAdjustHistoryMapper mapper = mock(SecurityPoolAdjustHistoryMapper.class);
        InvestmentPoolService investmentPoolService = mock(InvestmentPoolService.class);
        SecurityPoolAdjustHistoryService service = new SecurityPoolAdjustHistoryService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustHistoryMapper", mapper);
        ReflectionTestUtils.setField(service, "investmentPoolService", investmentPoolService);

        SecurityPoolAdjustHistoryDto row = new SecurityPoolAdjustHistoryDto();
        row.setAdjusterName("管理员");
        row.setSubmitTime(new Date());
        row.setSecurityShortName("测试债");
        row.setSecurityCode("100001");
        row.setSecurityTypeName("信用债");
        row.setIssuer("测试主体");
        row.setAdjustType("手工调整");
        row.setAdjustMode("调入");
        row.setAdjustReason("测试");
        row.setTargetPoolId(2L);
        row.setAuditStatus("20");
        row.setAbsFlag(1);
        row.setGuarantFlag(0);
        when(mapper.querySecurityPoolAdjustHistoryPage(any(SecurityPoolAdjustHistoryReq.class)))
                .thenReturn(Arrays.asList(row));
        when(investmentPoolService.queryPoolFullNameMap())
                .thenReturn(Collections.singletonMap(2L, "一级池/二级池"));

        CommonFileDto file = service.exportSecurityPoolAdjustHistoryExcel(new SecurityPoolAdjustHistoryReq());
        assertThat(file.getFileName()).startsWith("证券池调整历史_");
        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(Base64.getDecoder().decode(file.getContentBase64())))) {
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("调整人");
            // 是否ABS~是否含权：6 组条件格式（与证券池查询模板一致）
            assertThat(workbook.getSheetAt(0).getSheetConditionalFormatting().getNumConditionalFormattings())
                    .isEqualTo(6);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(11).getStringCellValue()).isEqualTo("是");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(12).getStringCellValue()).isEqualTo("否");
        }
    }
}
