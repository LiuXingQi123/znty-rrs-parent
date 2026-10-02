package com.znty.rrs.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/** 证券池调库发行主体财务 Mapper SQL 口径测试。 */
public class SecurityPoolAdjustFinancialMapperSqlTest {

    /** 四期查询精确匹配报告日期，缺失年报不得用季报替代。 */
    @Test
    public void financialListShouldQueryExactReportDates() throws Exception {
        String select = mapperBlock("select", "queryIssuerFinancialList");

        assertThat(select)
                .contains("f.REPORTDATE IN")
                .contains("collection=\"reportDates\"")
                .contains("ORDER BY f.REPORTDATE ASC")
                .doesNotContain("ROW_NUMBER()", "LIMIT 3");
    }

    /** 报告切换精确查询，批量保存覆盖完整指标。 */
    @Test
    public void editableFinancialReportShouldOverwriteCompleteMetrics() throws Exception {
        String select = mapperBlock("select", "queryIssuerFinancialByReportDate");
        String upsert = mapperBlock("insert", "saveIssuerFinancial");

        assertThat(select)
                .contains("f.REPORTDATE = #{reportDate}")
                .contains("si.issuer_code = f.COMPANYCODE");
        assertThat(upsert)
                .contains("INSERT INTO ais_inv_ods.wind_companyfinancial")
                .contains("#{issuerCode}")
                .contains("#{financial.reportDate}")
                .contains("ON DUPLICATE KEY UPDATE")
                .contains("TOT_ASSETS = VALUES(TOT_ASSETS)")
                .contains("ROE = VALUES(ROE)")
                .contains("GRP = VALUES(GRP)")
                .contains("EBITDA_TO_DEBT = VALUES(EBITDA_TO_DEBT)")
                .doesNotContain("${", "FROM rrs_securityinfo", "changedFields", "<if");
    }

    /** 读取并截取指定 Mapper 标签。 */
    private String mapperBlock(String tag, String queryId) throws Exception {
        Path path = Paths.get("src", "main", "resources", "mapper", "SecurityPoolAdjustMapper.xml");
        String xml = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        String marker = "<" + tag + " id=\"" + queryId + "\"";
        int start = xml.indexOf(marker);
        assertThat(start).as(queryId + " 应存在").isGreaterThanOrEqualTo(0);
        String endTag = "</" + tag + ">";
        int end = xml.indexOf(endTag, start);
        assertThat(end).as(queryId + " 应闭合").isGreaterThan(start);
        return xml.substring(start, end + endTag.length());
    }
}
