package com.znty.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** 债券特征查询 Mapper SQL 口径测试。 */
public class BondFeatureQueryMapperSqlTest {

    /** 八类页面的九条查询均应返回并支持筛选七项债券特征。 */
    @Test
    public void targetQueriesShouldReturnAndFilterBondFeatures() throws Exception {
        // 核对单笔证券、可绑定标的及禁投 ABS 债查询
        assertFeatureQuery("SecurityPoolAdjustMapper.xml", "querySecurityPage", "si");
        // 核对 CRMW 的标的证券字段，避免误筛凭证本身
        assertFeatureQuery("CrmwPoolAdjustMapper.xml", "queryBindableSecurityPage", "si");
        // 核对禁投 ABS 债查询的标准信用债过滤
        assertFeatureQuery("ForbiddenAbsPoolAdjustMapper.xml", "querySecurityPage", "si");
        // 核对批量候选、池查询和调整历史
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

        // SELECT * 的单笔查询由主档实体继承全部原始字段
        if (!select.contains(securityAlias + ".*")) {
            assertThat(select.substring(0, select.indexOf("FROM")))
                    .contains(securityAlias + ".std_credit_flag");
        }
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
                .contains("bondYesFlags.contains('inright')")
                .contains("bondYesFlags.contains('stdCredit')");

        Configuration configuration = new Configuration();
        XMLLanguageDriver driver = new XMLLanguageDriver();
        String script = select.replaceFirst("<select[^>]*>", "<script>")
                .replace("</select>", "</script>");
        Map<String, Object> params = new HashMap<>();
        for (Object flags : Arrays.asList(null, Collections.emptyList(), Collections.singletonList("guarant"))) {
            params.put("bondYesFlags", flags);
            BoundSql sql = driver.createSqlSource(configuration, script, Map.class).getBoundSql(params);
            assertThat(sql.getSql()).doesNotContain(securityAlias + ".std_credit_flag = 1");
        }
        params.put("bondYesFlags", Collections.singletonList("stdCredit"));
        BoundSql standard = driver.createSqlSource(configuration, script, Map.class).getBoundSql(params);
        assertThat(standard.getSql()).contains("AND " + securityAlias + ".std_credit_flag = 1");
        params.put("bondYesFlags", Arrays.asList("stdCredit", "guarant"));
        BoundSql combined = driver.createSqlSource(configuration, script, Map.class).getBoundSql(params);
        assertThat(combined.getSql()).contains("AND " + securityAlias + ".std_credit_flag = 1")
                .contains("AND COALESCE(" + securityAlias + ".guarant_flag, 0) = 1");

        Matcher resultType = Pattern.compile("resultType=\"([^\"]+)\"").matcher(select);
        assertThat(resultType.find()).isTrue();
        MetaClass dto = MetaClass.forClass(Class.forName(resultType.group(1)), configuration.getReflectorFactory());
        assertThat(dto.getSetterType("stdCreditFlag")).isEqualTo(Integer.class);
        assertThat(dto.getSetterType("stdCreditFlag")).isEqualTo(dto.getSetterType("inrightFlag"));
        Object response = Class.forName(resultType.group(1)).getDeclaredConstructor().newInstance();
        MetaObject properties = configuration.newMetaObject(response);
        ObjectMapper objectMapper = new ObjectMapper();
        for (Integer value : Arrays.asList(1, 0, null)) {
            properties.setValue("stdCreditFlag", value);
            properties.setValue("inrightFlag", value);
            JsonNode json = objectMapper.valueToTree(response);
            assertThat(json.get("stdCreditFlag")).isEqualTo(json.get("inrightFlag"));
            assertThat(json.get("stdCreditFlag").isNull()).isEqualTo(value == null);
            if (value != null) {
                assertThat(json.get("stdCreditFlag").isInt()).isTrue();
            }
        }
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
