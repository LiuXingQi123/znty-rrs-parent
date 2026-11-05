package com.znty.rrs.service;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「主体下 ABS 债券自动入库」对账 SQL 的结构测试。
 *
 * <p>对账改成四条无关联的批量查询，若有人把主体或跨库关系重新压回单条 SQL，
 * 相关子查询会让任务重新变慢，因此这里直接渲染绑定后的语句做断言。</p>
 */
public class AbsPoolReconcileMapperSqlTest {

    /** Mapper 资源路径。 */
    private static final String RESOURCE = "mapper/AbsPoolReconcileMapper.xml";
    /** Mapper 命名空间。 */
    private static final String NAMESPACE = "com.znty.rrs.mapper.AbsPoolReconcileMapper";

    /**
     * 四条对账查询都要存在，且都是简单单表或单次连接读。
     *
     * @throws Exception 读取 Mapper 失败
     */
    @Test
    public void reconcileStatementsShouldBeSimpleBulkReads() throws Exception {
        Configuration configuration = parse();
        String[] ids = {"queryAbsBondList", "queryAbsCompanyRelationList",
                "queryEffectiveCompanyPoolRows", "queryEffectiveAbsPoolRows"};
        for (String id : ids) {
            MappedStatement statement = configuration.getMappedStatement(NAMESPACE + "." + id);
            String sql = statement.getBoundSql(null).getSql();
            assertThat(sql).as(id + " 不应包含相关子查询").doesNotContainIgnoringCase("exists (");
            assertThat(sql).as(id + " 不应排序").doesNotContain("ORDER BY");
        }
    }

    /**
     * ABS 主档查询：按字典 bond 大类取数，并排除退市。
     *
     * @throws Exception 读取 Mapper 失败
     */
    @Test
    public void absBondQueryShouldFilterDelisted() throws Exception {
        String sql = render("queryAbsBondList");
        assertThat(sql).contains("dst.category_type = 'bond'")
                .contains("(COALESCE(bond.abs_flag, 0) = 1 OR bond.security_type = 'abs')")
                .contains("bond.security_status != 'D'");
    }

    /**
     * 关系查询：只取四类关系码，跨库读 ODS 关系表。
     *
     * @throws Exception 读取 Mapper 失败
     */
    @Test
    public void relationQueryShouldUseFourTypeCodes() throws Exception {
        String sql = render("queryAbsCompanyRelationList");
        assertThat(sql).contains("ais_inv_ods.wind_cbondissuer")
                .contains("115004000").contains("115203000").contains("115200000").contains("115201000");
    }

    /**
     * 池状态查询：只认审批通过且未软删的落地状态。
     *
     * @throws Exception 读取 Mapper 失败
     */
    @Test
    public void poolRowQueriesShouldOnlyTakeEffectiveRows() throws Exception {
        for (String id : new String[]{"queryEffectiveCompanyPoolRows", "queryEffectiveAbsPoolRows"}) {
            String sql = render(id);
            assertThat(sql).as(id).contains("status.audit_status = '20'").contains("status.is_deleted = 0");
        }
    }

    /** 渲染指定语句。 */
    private String render(String id) throws Exception {
        return parse().getMappedStatement(NAMESPACE + "." + id).getBoundSql(null).getSql();
    }

    /** 解析 Mapper XML。 */
    private Configuration parse() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }
}
