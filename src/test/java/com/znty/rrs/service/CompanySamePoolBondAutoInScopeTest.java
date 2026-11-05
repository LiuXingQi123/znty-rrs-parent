package com.znty.rrs.service;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「主体下债券自动入池」候选 SQL 的 ABS 排除口径测试。
 *
 * <p>该任务的 ABS 排除由 {@link CompanyBondSyncPolicy#samePoolBondScope()} 传入
 * {@code excludeAbs=true} 驱动，SQL 里只保留 {@code <if>} 分支。仅靠文本断言无法证明参数
 * 真的生效，也无法发现「调用点被改回 includeAll」这类回退，因此这里直接渲染 MyBatis 绑定后的
 * 最终语句做校验。</p>
 */
public class CompanySamePoolBondAutoInScopeTest {

    /** 同池候选 SQL 的完整 MappedStatement ID。 */
    private static final String SELECT_ID =
            "com.znty.rrs.mapper.AutoAdjustMapper.queryCompanyBondSamePoolForAutoIn";

    /**
     * 验证该任务的类型范围固定排除 ABS，且渲染后的 SQL 带有排除条件。
     *
     * @throws Exception 读取 Mapper 失败
     */
    @Test
    public void samePoolScopeShouldExcludeAbs() throws Exception {
        assertThat(CompanyBondSyncPolicy.samePoolBondScope().isExcludeAbs())
                .as("同池任务必须排除 ABS")
                .isTrue();

        String sql = render(CompanyBondSyncPolicy.samePoolBondScope());
        assertThat(sql)
                .as("最终语句必须带 ABS 排除条件")
                .contains("NOT (COALESCE(bond.abs_flag, 0) = 1 OR bond.security_type = 'abs')");
    }

    /**
     * 验证 ABS 的四类关系码匹配分支已从同池候选 SQL 移除。
     *
     * <p>关系表分支是原 SQL 无法去相关、跑不出结果的主因；它一旦被加回来，
     * 未确认 ABS 会重新进入候选并拖慢整轮任务。</p>
     *
     * @throws Exception 读取 Mapper 失败
     */
    @Test
    public void samePoolSqlShouldNotMatchAbsRelation() throws Exception {
        String sql = render(CompanyBondSyncPolicy.samePoolBondScope());
        assertThat(sql).doesNotContain("wind_cbondissuer");
        assertThat(sql).doesNotContain("trigger_status");
        assertThat(sql).doesNotContain("ORDER BY bond.wind_code");
    }

    /**
     * 反向校验：改用统一范围时 ABS 排除条件不再出现，说明排除只由本任务专用范围驱动。
     *
     * @throws Exception 读取 Mapper 失败
     */
    @Test
    public void includeAllScopeShouldNotExcludeAbs() throws Exception {
        String sql = render(CompanyBondSyncPolicy.currentTypeScope());
        assertThat(sql).doesNotContain("bond.security_type = 'abs'");
    }

    /**
     * 验证候选 SQL 不再显式判空发行主体。
     *
     * <p>非 ABS 债发行主体为必填；且 {@code EXISTS} 内的
     * {@code cps.security_code = bond.issuer_code} 与 NULL 比较恒不成立，
     * 天然排除主体为空的行。再叠加 {@code IS NOT NULL} 属于冗余条件，徒增阅读成本。</p>
     *
     * @throws Exception 读取 Mapper 失败
     */
    @Test
    public void samePoolSqlShouldNotCheckIssuerCodeNull() throws Exception {
        String sql = render(CompanyBondSyncPolicy.samePoolBondScope());
        assertThat(sql).doesNotContain("bond.issuer_code IS NOT NULL");
        assertThat(sql).doesNotContain("bond.issuer_code != ''");
    }

    /**
     * 渲染指定类型范围下的同池候选 SQL。
     *
     * @param scope 类型范围
     * @return 绑定参数后的 SQL 文本
     * @throws Exception 读取 Mapper 失败
     */
    private String render(com.znty.rrs.entity.bo.CompanyBondTypeScopeBo scope) throws Exception {
        Configuration cfg = new Configuration();
        String resource = "mapper/AutoAdjustMapper.xml";
        try (InputStream is = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(is, cfg, resource, cfg.getSqlFragments()).parse();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("poolId", 15L);
        params.put("bondTypeScope", scope);
        MappedStatement statement = cfg.getMappedStatement(SELECT_ID);
        return statement.getBoundSql(params).getSql();
    }
}
