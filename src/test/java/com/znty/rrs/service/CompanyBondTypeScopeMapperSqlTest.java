package com.znty.rrs.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 主体旗下债券候选 SQL 类型范围测试。 */
public class CompanyBondTypeScopeMapperSqlTest {

    /** 验证所有主体联动候选都使用统一范围参数，不再硬编码排除 ABS 或 CRMW。 */
    @Test
    public void companyBondQueriesShouldUseUnifiedTypeScope() throws Exception {
        // 分别校验定时任务与人工主体调库的全部候选查询
        assertMapperQueries("AutoAdjustMapper.xml", Arrays.asList(
                "queryCompanyNewBondForAutoIn",
                "queryCompanyBondSamePoolForAutoIn",
                "queryCompanyBondInSamePoolForAutoOut",
                "queryBondInPoolWhenCompanyNotIn"));
        assertMapperQueries("ForbiddenPoolAdjustMapper.xml", Arrays.asList(
                "queryCompanyInboundBondForAutoList",
                "queryCompanyBondMutexOutList",
                "queryCompanyOutboundBondForAutoList"));
    }

    /** 验证主体详情债券数量与实际联动范围一致，CRMW 也参与统计。 */
    @Test
    public void companyBondCountShouldIncludeCrmw() throws Exception {
        Path mapperPath = Paths.get("src", "main", "resources", "mapper", "ForbiddenPoolAdjustMapper.xml");
        String xml = new String(Files.readAllBytes(mapperPath), StandardCharsets.UTF_8);
        // 截取主体债券数量查询，单独校验展示口径
        String select = selectBlock(xml, "queryCompanyBondCountList");
        assertThat(select).doesNotContain("bond.security_type != 'crmw'");
    }

    /** 验证 CRMW 单笔调库的可绑定标的仅包含非 CRMW 债券。 */
    @Test
    public void crmwBindableSecurityQueryShouldOnlyIncludeBonds() throws Exception {
        Path mapperPath = Paths.get("src", "main", "resources", "mapper", "CrmwPoolAdjustMapper.xml");
        String xml = new String(Files.readAllBytes(mapperPath), StandardCharsets.UTF_8);
        // 候选查询必须依赖有效债券类型字典，避免主体、股票和基金进入列表
        String select = selectBlock(xml, "queryBindableSecurityPage");
        assertThat(select).contains("INNER JOIN dict_security_type dst")
                .contains("AND dst.is_deleted = 0")
                .contains("AND dst.category_type = 'bond'")
                .contains("AND si.security_type != 'crmw'")
                .doesNotContain("LEFT JOIN dict_security_type dst");
    }

    /**
     * 校验一个 Mapper 中指定主体债查询的类型过滤表达式。
     *
     * @param mapperFile Mapper XML 文件名
     * @param queryIds 查询 ID 列表
     */
    private void assertMapperQueries(String mapperFile, List<String> queryIds) throws Exception {
        Path mapperPath = Paths.get("src", "main", "resources", "mapper", mapperFile);
        String xml = new String(Files.readAllBytes(mapperPath), StandardCharsets.UTF_8);
        for (String queryId : queryIds) {
            // 只截取当前 select，避免其他非主体联动查询的过滤条件干扰断言
            String select = selectBlock(xml, queryId);
            assertThat(select).contains("bondTypeScope.excludeAbs");
            assertThat(select).contains("bondTypeScope.excludedSecurityTypes");
            assertThat(select).doesNotContain("COALESCE(bond.abs_flag, 0) != 1");
            assertThat(select).doesNotContain("bond.security_type != 'crmw'");
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
