package com.znty.rrs.service;

import com.znty.rrs.entity.bo.CompanyBondTypeScopeBo;
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
 * 「外评AA-及以下主体自动入池」旗下债候选 SQL 的 ABS 排除口径测试
 *
 * <p>该任务的 ABS 排除由 {@link CompanyBondSyncPolicy#outerRatingInboundScope()} 传入
 * {@code excludeAbs=true} 驱动，SQL 里只保留 {@code <if>} 分支，仅靠文本断言无法证明参数
 * 真的生效，也无法发现「调用点被改回 includeAll」这类回退，因此这里直接渲染 MyBatis 绑定后的
 * 最终语句做校验
 */
public class CompanyOuterRatingBondScopeTest {

    /** 外评入池旗下债候选 SQL 的完整 MappedStatement ID */
    private static final String SELECT_ID =
            "com.znty.rrs.mapper.AutoAdjustMapper.queryCompanyBondNotInSamePoolForAutoIn";

    /**
     * 验证该任务的类型范围固定排除 ABS，且渲染后的 SQL 带有排除条件
     *
     * @throws Exception 读取 Mapper 失败
     */
    @Test
    public void outerRatingInboundScopeShouldExcludeAbs() throws Exception {
        assertThat(CompanyBondSyncPolicy.outerRatingInboundScope().isExcludeAbs())
                .as("外评入池任务必须排除 ABS，否则会与 ABS 对账任务重复写池状态")
                .isTrue();

        String sql = render(CompanyBondSyncPolicy.outerRatingInboundScope());
        assertThat(sql)
                .as("最终语句必须带 ABS 排除条件")
                .contains("NOT (COALESCE(bond.abs_flag, 0) = 1 OR bond.security_type = 'abs')");
    }

    /**
     * 验证 ABS 的四类关系码匹配分支已从旗下债候选 SQL 移除
     *
     * <p>关系表分支原先只服务于「未确认主体的 ABS 按担保人、差额支付承诺人、债务主体、
     * 原始权益人找联动主体」，ABS 排除后不再需要，它一旦被加回来会重新引入跨库相关子查询
     *
     * @throws Exception 读取 Mapper 失败
     */
    @Test
    public void outerRatingBondSqlShouldNotMatchAbsRelation() throws Exception {
        String sql = render(CompanyBondSyncPolicy.outerRatingInboundScope());
        assertThat(sql).doesNotContain("wind_cbondissuer");
        assertThat(sql).doesNotContain("s_info_typecode");
    }

    /**
     * 验证旗下债候选只按发行主体关联
     *
     * @throws Exception 读取 Mapper 失败
     */
    @Test
    public void outerRatingBondSqlShouldOnlyUseIssuerCode() throws Exception {
        String sql = render(CompanyBondSyncPolicy.outerRatingInboundScope());
        assertThat(sql).contains("bond.issuer_code = ?");
    }

    /**
     * 验证 CRMW 未被排除，仍跟随主体入池
     *
     * @throws Exception 读取 Mapper 失败
     */
    @Test
    public void outerRatingBondSqlShouldKeepCrmw() throws Exception {
        String sql = render(CompanyBondSyncPolicy.outerRatingInboundScope());
        assertThat(sql).doesNotContain("bond.security_type != 'crmw'");
        assertThat(sql).doesNotContain("'crmw'");
    }

    /**
     * 反向校验：改用统一范围时 ABS 排除条件不再出现，说明排除只由本任务专用范围驱动
     *
     * @throws Exception 读取 Mapper 失败
     */
    @Test
    public void includeAllScopeShouldNotExcludeAbs() throws Exception {
        String sql = render(CompanyBondSyncPolicy.currentTypeScope());
        assertThat(sql).doesNotContain("bond.security_type = 'abs'");
    }

    /**
     * 渲染指定类型范围下的旗下债候选 SQL
     *
     * @param scope 类型范围
     * @return 绑定参数后的 SQL 文本
     * @throws Exception 读取 Mapper 失败
     */
    private String render(CompanyBondTypeScopeBo scope) throws Exception {
        Configuration cfg = new Configuration();
        String resource = "mapper/AutoAdjustMapper.xml";
        try (InputStream is = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(is, cfg, resource, cfg.getSqlFragments()).parse();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("companyCode", "C001");
        params.put("poolId", 17L);
        params.put("bondTypeScope", scope);
        MappedStatement statement = cfg.getMappedStatement(SELECT_ID);
        return statement.getBoundSql(params).getSql();
    }
}
