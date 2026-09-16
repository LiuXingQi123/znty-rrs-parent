package com.znty.rrs.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/** 证券池调库发行主体财务 Mapper SQL 口径测试。 */
public class SecurityPoolAdjustFinancialMapperSqlTest {

    /** 最近三年查询应按有数据年份分组并取每年最新报告。 */
    @Test
    public void financialListShouldUseLatestReportOfThreeDataYears() throws Exception {
        String select = mapperBlock("select", "queryIssuerFinancialList");

        assertThat(select)
                .contains("PARTITION BY FLOOR(f.REPORTDATE / 10000)")
                .contains("ORDER BY f.REPORTDATE DESC")
                .contains("WHERE year_row_no = 1")
                .contains("LIMIT 3")
                .contains("ORDER BY REPORTDATE ASC")
                .doesNotContain("- 3");
    }

    /** 第四列切换应精确查询报告日期，提交应按主体和报告日期新增或更新。 */
    @Test
    public void editableFinancialReportShouldSupportExactQueryAndUpsert() throws Exception {
        String select = mapperBlock("select", "queryIssuerFinancialByReportDate");
        String insert = mapperBlock("insert", "saveIssuerFinancial");

        assertThat(select)
                .contains("f.REPORTDATE = #{reportDate}")
                .contains("si.issuer_code = f.COMPANYCODE");
        assertThat(insert)
                .contains("INSERT INTO ais_inv_ods.wind_companyfinancial")
                .contains("SELECT issuer_code")
                .contains("#{financial.reportDate}")
                .contains("ON DUPLICATE KEY UPDATE")
                .contains("TOT_ASSETS = VALUES(TOT_ASSETS)");
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
