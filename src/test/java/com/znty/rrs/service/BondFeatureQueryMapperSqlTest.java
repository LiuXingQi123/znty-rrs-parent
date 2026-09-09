package com.znty.rrs.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/** 债券特征查询 Mapper SQL 口径测试。 */
public class BondFeatureQueryMapperSqlTest {

    /** 五类页面查询均应返回并支持筛选六项债券特征。 */
    @Test
    public void targetQueriesShouldReturnAndFilterBondFeatures() throws Exception {
        assertFeatureQuery("BatchSecurityPoolAdjustMapper.xml", "querySecurityPage", "si");
        assertFeatureQuery("StockSecurityBatchAdjustMapper.xml", "querySecurityPage", "si");
        assertFeatureQuery("BatchCrmwPoolAdjustMapper.xml", "queryOutboundCandidatePage", "si");
        assertFeatureQuery("BatchCrmwPoolAdjustMapper.xml", "queryInboundCandidatePage", "bond");
        assertFeatureQuery("SecurityPoolQueryMapper.xml", "querySecurityPoolPage", "bi");
        assertFeatureQuery("SecurityPoolAdjustHistoryMapper.xml", "querySecurityPoolAdjustHistoryPage", "sb");
    }

    /**
     * 校验指定查询返回特征原始字段，并按统一 bondYesFlags 口径追加过滤。
     *
     * @param mapperFile Mapper XML 文件名
     * @param queryId 查询 ID
     * @param securityAlias 证券主档别名
     */
    private void assertFeatureQuery(String mapperFile, String queryId, String securityAlias) throws Exception {
        Path mapperPath = Paths.get("src", "main", "resources", "mapper", mapperFile);
        String xml = new String(Files.readAllBytes(mapperPath), StandardCharsets.UTF_8);
        String select = selectBlock(xml, queryId);

        assertThat(select)
                .contains(securityAlias + ".abs_flag")
                .contains(securityAlias + ".guarant_flag")
                .contains(securityAlias + ".yx_flag")
                .contains(securityAlias + ".cj_flag")
                .contains(securityAlias + ".issue_type")
                .contains(securityAlias + ".inner_class")
                .contains(securityAlias + ".inright_flag")
                .contains("bondYesFlags.contains('abs')")
                .contains("bondYesFlags.contains('guarant')")
                .contains("bondYesFlags.contains('yx')")
                .contains("bondYesFlags.contains('cj')")
                .contains("bondYesFlags.contains('private')")
                .contains("bondYesFlags.contains('inright')");
    }

    /**
     * 截取指定 MyBatis select 标签内容。
     *
     * @param xml Mapper XML 全文
     * @param queryId 查询 ID
     * @return select 标签及其内容
     */
    private String selectBlock(String xml, String queryId) {
        String marker = "<select id=\"" + queryId + "\"";
        int start = xml.indexOf(marker);
        assertThat(start).as(queryId + " 应存在").isGreaterThanOrEqualTo(0);
        int end = xml.indexOf("</select>", start);
        assertThat(end).as(queryId + " 应闭合").isGreaterThan(start);
        return xml.substring(start, end + "</select>".length());
    }
}
