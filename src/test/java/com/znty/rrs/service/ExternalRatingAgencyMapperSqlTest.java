package com.znty.rrs.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
