package com.znty.rrs.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/** 证券池调库相关主体 Mapper SQL 口径测试。 */
public class SecurityPoolAdjustRelatedSubjectMapperSqlTest {

    /** 担保人、权益人下拉均应查询当前证券的四类关系主体。 */
    @Test
    public void relatedSubjectQueryShouldUseFourRelationTypesForAllBonds() throws Exception {
        String xml = readMapper();
        String select = selectBlock(xml, "queryRelatedRatingSubjectList");

        assertThat(select)
                .contains("issuer.s_info_typecode IN (115200000, 115004000, 115203000, 115201000)")
                .contains("LEFT JOIN ranked_grade")
                .doesNotContain("security.abs_flag")
                .doesNotContain("security.guarant_flag")
                .doesNotContain("issuer.used = 1");
    }

    /** 自选权益人分页和提交防伪都必须从投资分析主体表读取。 */
    @Test
    public void selfSelectedRightsHolderQueriesShouldUseCompanyTable() throws Exception {
        String xml = readMapper();

        for (String queryId : new String[]{
                "querySelfSelectedRightsHolderPage", "querySelfSelectedRightsHolderByCode"}) {
            String select = selectBlock(xml, queryId);
            assertThat(select)
                    .contains("FROM ais_inv_analysis.t_inv_company company")
                    .contains("LEFT JOIN ranked_grade")
                    .doesNotContain("wind_cbondissuer");
        }
    }

    /** 读取证券池调库 Mapper XML。 */
    private String readMapper() throws Exception {
        Path path = Paths.get("src", "main", "resources", "mapper", "SecurityPoolAdjustMapper.xml");
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
