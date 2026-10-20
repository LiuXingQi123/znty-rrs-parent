package com.znty.rrs.service;

import com.znty.rrs.mapper.AutoAdjustMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.Assume;
import org.junit.Test;

import java.io.InputStream;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实 Mapper 和 H2 验证主体补债扫描池发现范围。 */
public class CompanyCurrentPoolMapperTest {

    /** 跨池发现不依赖固定 ID，并排除债券记录、未生效、删除、停用及 CRMW 组合池。 */
    @Test
    public void shouldDiscoverEveryEffectiveCompanyPool() throws Exception {
        String version = System.getProperty("java.specification.version");
        int major = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 持久化测试需 Java 11 以上运行时", major >= 11);
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:company_pool_scope;MODE=MySQL", "sa", "")));
        String resource = "mapper/AutoAdjustMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        try (SqlSession session = new SqlSessionFactoryBuilder().build(configuration).openSession(true)) {
            try (Statement statement = session.getConnection().createStatement()) {
                statement.execute("CREATE TABLE dict_security_type (security_type VARCHAR(32), category_type VARCHAR(32), is_deleted INT)");
                statement.execute("CREATE TABLE ip_investment_pool (id BIGINT, pool_type VARCHAR(32), status VARCHAR(32), is_deleted INT)");
                statement.execute("CREATE TABLE ip_pool_status (target_pool_id BIGINT, security_type VARCHAR(32), audit_status VARCHAR(2), is_deleted INT)");
                statement.execute("INSERT INTO dict_security_type VALUES ('company', 'company', 0), ('mtn', 'bond', 0)");
                statement.execute("INSERT INTO ip_investment_pool VALUES (16, 'observe', 'enabled', 0), (23, 'restricted', 'enabled', 0),"
                        + " (999, 'custom', 'enabled', 0), (18, 'crmw', 'enabled', 0), (30, 'custom', 'disabled', 0), (31, 'custom', 'enabled', 1)");
                statement.execute("INSERT INTO ip_pool_status VALUES (16, 'company', '20', 0), (16, 'company', '20', 0),"
                        + " (23, 'company', '20', 0), (999, 'company', '20', 0), (18, 'company', '20', 0),"
                        + " (30, 'company', '20', 0), (31, 'company', '20', 0), (40, 'company', '20', 0),"
                        + " (41, 'mtn', '20', 0), (42, 'company', '00', 0), (43, 'company', '20', 1)");
                statement.execute("INSERT INTO ip_investment_pool VALUES (41, 'custom', 'enabled', 0),"
                        + " (42, 'custom', 'enabled', 0), (43, 'custom', 'enabled', 0)");
            }
            assertThat(session.getMapper(AutoAdjustMapper.class).queryCompanyCurrentPoolIds())
                    .containsExactly(16L, 23L, 999L);
        }
    }
}
