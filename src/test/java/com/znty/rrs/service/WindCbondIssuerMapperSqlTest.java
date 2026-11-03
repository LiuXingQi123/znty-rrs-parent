package com.znty.rrs.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Wind 发行主体 Mapper SQL 口径测试 */
public class WindCbondIssuerMapperSqlTest {

    /** 原发行主体资料查询切换公司简介，不再依赖旧表。 */
    @Test
    public void windCbondIssuerQueriesShouldNotFilterUsed() throws Exception {
        List<String> mapperFiles = Arrays.asList(
                "ForbiddenPoolAdjustMapper.xml",
                "ForbiddenPoolHistoryMapper.xml",
                "ForbiddenPoolQueryMapper.xml",
                "TempSecurityCodeMapper.xml"
        );
        Pattern usedFilterPattern = Pattern.compile("(?i)\\b(?:\\w+\\.)?used\\s*=\\s*'?1'?");

        for (String mapperFile : mapperFiles) {
            Path mapperPath = Paths.get("src", "main", "resources", "mapper", mapperFile);
            String xml = new String(Files.readAllBytes(mapperPath), StandardCharsets.UTF_8);
            assertThat(xml).as(mapperFile + " 应查询公司简介")
                    .contains("ais_inv_ods.wind_compintroduction");
            if ("ForbiddenPoolAdjustMapper.xml".equals(mapperFile)) {
                // 主体资料继续查公司简介，ABS 旗下债关系允许查询 Wind 四类关系
                for (String queryId : Arrays.asList("queryCompanyPage", "queryCompanyDetail")) {
                    int start = xml.indexOf("<select id=\"" + queryId + "\"");
                    String query = xml.substring(start, xml.indexOf("</select>", start));
                    assertThat(query).contains("wind_compintroduction").doesNotContain("wind_cbondissuer");
                }
                assertThat(xml).contains("abs_issuer.s_info_typecode IN (115004000, 115203000, 115202000, 115201000)");
            } else {
                assertThat(xml).doesNotContain("ais_inv_ods.wind_cbondissuer");
            }
            assertThat(usedFilterPattern.matcher(xml).find())
                    .as(mapperFile + " 不应保留 used=1 过滤")
                    .isFalse();
        }
    }

    /** 业务快照已有名称的查询不应新增外部主体表关联。 */
    @Test
    public void snapshotQueriesShouldNotJoinCompanyMaster() throws Exception {
        for (String mapperFile : Arrays.asList("CompanyPoolQueryMapper.xml", "CompanyPoolAdjustHistoryMapper.xml")) {
            String xml = new String(Files.readAllBytes(Paths.get("src", "main", "resources", "mapper", mapperFile)),
                    StandardCharsets.UTF_8);
            assertThat(xml).as(mapperFile).contains("security_short_name").doesNotContain("ais_inv_ods.");
        }
        String xml = new String(Files.readAllBytes(Paths.get("src", "main", "resources", "mapper",
                "SecurityPoolExcelImportMapper.xml")), StandardCharsets.UTF_8);
        String memberQuery = xml.substring(xml.indexOf("<select id=\"queryPoolMemberList\""));
        assertThat(memberQuery).contains("ips.security_short_name").doesNotContain("ais_inv_ods.");
    }

    /** 证券与关系主体的关联仍由旧表提供，不增加公司简介关联。 */
    @Test
    public void securityRelationsShouldKeepIssuerSource() throws Exception {
        for (String mapperFile : Arrays.asList("CommonMapper.xml", "SecurityPoolAdjustMapper.xml")) {
            String xml = new String(Files.readAllBytes(Paths.get("src", "main", "resources", "mapper", mapperFile)),
                    StandardCharsets.UTF_8);
            assertThat(xml).as(mapperFile).contains("ais_inv_ods.wind_cbondissuer issuer")
                    .doesNotContain("wind_compintroduction");
        }
    }

    /** 校验禁投池查询和历史仅覆盖债券禁止库。 */
    @Test
    public void forbiddenPoolQueriesShouldOnlyQueryForbiddenPool() throws Exception {
        List<String> mapperFiles = Arrays.asList(
                "ForbiddenPoolHistoryMapper.xml",
                "ForbiddenPoolQueryMapper.xml"
        );

        for (String mapperFile : mapperFiles) {
            Path mapperPath = Paths.get("src", "main", "resources", "mapper", mapperFile);
            String xml = new String(Files.readAllBytes(mapperPath), StandardCharsets.UTF_8);
            assertThat(xml).as(mapperFile + " 应仅查询债券禁止库")
                    .contains("p.pool_type = 'forbidden'")
                    .doesNotContain("p.pool_type IN ('forbidden', 'observe', 'blacklist', 'restricted')");
        }
    }

    /** 校验禁投池查询和历史支持按主体或债券筛选。 */
    @Test
    public void forbiddenPoolQueriesShouldSupportCategoryTypeFilter() throws Exception {
        List<String> mapperFiles = Arrays.asList(
                "ForbiddenPoolHistoryMapper.xml",
                "ForbiddenPoolQueryMapper.xml"
        );

        for (String mapperFile : mapperFiles) {
            Path mapperPath = Paths.get("src", "main", "resources", "mapper", mapperFile);
            String xml = new String(Files.readAllBytes(mapperPath), StandardCharsets.UTF_8);
            assertThat(xml).as(mapperFile + " 应支持主体和债券对象类型筛选")
                    .contains("test=\"categoryType == 'company'\"")
                    .contains("test=\"categoryType == 'bond'\"")
                    .contains("security_type = 'company'")
                    .contains("dst.category_type = 'bond'");
        }
    }
}
