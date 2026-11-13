package com.znty.rrs.service;

import org.junit.Assume;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 外部评级机构参数化 SQL 测试。 */
public class ExternalRatingAgencyMapperSqlTest {

    /** 所有外评查询都应使用参数集合，不再包含固定机构编码。 */
    @Test
    public void ratingQueries_ShouldUseConfiguredAgencyCodes() throws Exception {
        assertParameterizedQueries("AutoAdjustMapper.xml", Arrays.asList(
                "queryCompanyHasLowOuterRating",
                "queryCompanyByLowOuterRatingNotInPool",
                "queryCompanyByNotLowOuterRatingInPool"));
        assertParameterizedQueries("WindRatingMapper.xml", Arrays.asList("queryLatestRating"));
    }

    /** 自动进出池按归一后的评级分值判断，无认可外评时不出池 */
    @Test
    public void autoAdjustRatingQueries_ShouldFilterByEffectiveRatingValue() throws Exception {
        String xml = readMapper("AutoAdjustMapper.xml");

        assertThat(selectBlock(xml, "queryCompanyByLowOuterRatingNotInPool"))
                .contains("WHERE e.rating_value &lt;= 17");
        assertThat(selectBlock(xml, "queryCompanyByNotLowOuterRatingInPool"))
                .contains("AND e.rating_value > 17")
                .doesNotContain("COALESCE(e.rating_value");
    }

    /** 黑名单质押库校验和定时任务使用相同的评级提取规则 */
    @Test
    public void pledgeRatingQueries_ShouldNormalizeRatingSuffixes() throws Exception {
        String xml = readMapper("AutoAdjustMapper.xml");
        String normalization = "REGEXP_SUBSTR(TRIM(r.b_info_creditrating), '^(A+|B+|C+|D)[+-]?', 1, 1, 'c')";
        assertThat(selectBlock(xml, "queryCompanyHasLowOuterRating")).contains(normalization + " IN (");
        String autoIn = selectBlock(xml, "queryCompanyByLowOuterRatingNotInPool");
        int caseStart = autoIn.indexOf("CASE " + normalization);
        assertThat(caseStart).isGreaterThanOrEqualTo(0);
        String ratingCase = autoIn.substring(caseStart, autoIn.indexOf(" AS rating_value", caseStart));
        assertThat(selectBlock(xml, "queryCompanyByNotLowOuterRatingInPool")).contains(ratingCase);

        String version = System.getProperty("java.specification.version");
        int major = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 SQL 测试需 Java 11 以上运行时", major >= 11);
        String sql = "SELECT " + ratingCase.replace("r.b_info_creditrating", "CAST(? AS VARCHAR)");
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:");
             PreparedStatement statement = connection.prepareStatement(sql)) {
            assertRatingValue(statement, "AA-", 17);
            assertRatingValue(statement, "AA-xxx", 17);
            assertRatingValue(statement, "A-xxx", 14);
            assertRatingValue(statement, "AA+xxx", 19);
            assertRatingValue(statement, "AAxxx", 18);
            assertRatingValue(statement, "  BBB+（观察）  ", 13);
            assertRatingValue(statement, "AAA-xxx", 999);
            assertRatingValue(statement, "未知", 999);
        }
    }

    /** 执行 Mapper 中的评级转换表达式并检查分值 */
    private void assertRatingValue(PreparedStatement statement, String rating, int expected) throws Exception {
        statement.setString(1, rating);
        try (ResultSet result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).as(rating).isEqualTo(expected);
        }
    }

    /** 近一年外评直接按评级日期筛选，同档按评级日期取最新记录 */
    @Test
    public void pledgeRatingQueries_ShouldUseRatingDate() throws Exception {
        String xml = readMapper("AutoAdjustMapper.xml");
        String ratingDateFilter = "AND r.ann_dt >= DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 YEAR), '%Y%m%d')";

        assertThat(selectBlock(xml, "queryCompanyHasLowOuterRating"))
                .contains(ratingDateFilter)
                .doesNotContain("ann_dt2");
        for (String queryId : Arrays.asList("queryCompanyByLowOuterRatingNotInPool",
                "queryCompanyByNotLowOuterRatingInPool")) {
            assertThat(selectBlock(xml, queryId)).as(queryId)
                    .contains(ratingDateFilter, ",ann_dt DESC")
                    .doesNotContain("ann_dt2");
        }
    }

    /** 两个任务都应将非标准评级排在标准评级之后，并排除空评级 */
    @Test
    public void autoAdjustRatingQueries_ShouldRankNonstandardRatingsAfterStandardRatings() throws Exception {
        String xml = readMapper("AutoAdjustMapper.xml");
        for (String queryId : Arrays.asList("queryCompanyByLowOuterRatingNotInPool",
                "queryCompanyByNotLowOuterRatingInPool")) {
            assertThat(selectBlock(xml, queryId)).as(queryId)
                    .contains("WHEN 'AAA+' THEN 21", "WHEN 'AA-' THEN 17", "ELSE 999")
                    .contains("ORDER BY rating_value ASC", "TRIM(r.b_info_creditrating) != ''")
                    .doesNotContain("WHERE rating_value");
        }
    }

    /** 配置查询应只返回未删除、非空机构并按数值编码升序。 */
    @Test
    public void agencyQuery_ShouldFilterAndSortActiveCodes() throws Exception {
        String xml = readMapper("ExternalRatingAgencyMapper.xml");

        assertThat(xml).contains("FROM dict_external_rating_agency")
                .contains("WHERE is_deleted = 0")
                .contains("TRIM(b_info_creditratingagency) != ''")
                .contains("GROUP BY TRIM(b_info_creditratingagency)")
                .contains("ORDER BY CAST(TRIM(b_info_creditratingagency) AS UNSIGNED) ASC");
    }

    /** 校验指定 Mapper 查询使用 agencyCodes foreach，并具有空集合保护。 */
    private void assertParameterizedQueries(String mapperFile, List<String> queryIds) throws Exception {
        String xml = readMapper(mapperFile);
        for (String queryId : queryIds) {
            String select = selectBlock(xml, queryId);
            assertThat(select).contains("collection=\"agencyCodes\"")
                    .contains("AND 1 = 0")
                    .doesNotContain("'2', '3', '4', '5', '6', '7', '13', '14', '19', '20'");
        }
    }

    /** 读取 Mapper XML。 */
    private String readMapper(String mapperFile) throws Exception {
        Path path = Paths.get("src", "main", "resources", "mapper", mapperFile);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** 截取指定 select 标签内容。 */
    private String selectBlock(String xml, String queryId) {
        String marker = "<select id=\"" + queryId + "\"";
        int start = xml.indexOf(marker);
        assertThat(start).as(queryId + " 应存在").isGreaterThanOrEqualTo(0);
        int end = xml.indexOf("</select>", start);
        assertThat(end).as(queryId + " 应闭合").isGreaterThan(start);
        return xml.substring(start, end + "</select>".length());
    }
}
