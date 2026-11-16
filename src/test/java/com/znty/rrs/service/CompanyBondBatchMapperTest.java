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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** 验证普通债同步的批量查询保留原有筛选条件和候选代码 */
public class CompanyBondBatchMapperTest {

    /** 查询使用库表的代码比较口径，返回候选代码并保留多条有效入池依据 */
    @Test
    public void shouldKeepCandidateCodesAndEffectiveRecords() throws Exception {
        String version = System.getProperty("java.specification.version");
        int major = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 持久化测试需 Java 11 以上运行时", major >= 11);
        Configuration config = new Configuration(new Environment("test", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:company_bond_batch;MODE=MySQL", "sa", "")));
        config.setMapUnderscoreToCamelCase(true);
        String resource = "mapper/AutoAdjustMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }

        try (SqlSession session = new SqlSessionFactoryBuilder().build(config).openSession(true)) {
            try (Statement statement = session.getConnection().createStatement()) {
                statement.execute("CREATE TABLE ip_pool_status (security_code VARCHAR_IGNORECASE(32), security_type VARCHAR(32),"
                        + " target_pool_id BIGINT, audit_status VARCHAR(2), is_deleted INT)");
                statement.execute("CREATE TABLE ip_adjust_log (security_code VARCHAR_IGNORECASE(32), adjust_batch_no VARCHAR(64), is_deleted INT)");
                statement.execute("CREATE TABLE ip_adjust_step (adjust_batch_no VARCHAR(64), step_status VARCHAR(32))");
                statement.execute("INSERT INTO ip_pool_status VALUES ('b1', 'mtn', 17, '20', 0), ('b1', 'mtn', 17, '20', 0),"
                        + " ('b1', 'mtn', 23, '00', 0), ('b1', 'mtn', 15, '20', 1), ('b2', 'mtn', 15, '20', 0),"
                        + " ('c1', 'company', 15, '20', 0), ('c2', 'company', 23, '20', 0), ('c3', 'company', 23, '20', 1)");
                statement.execute("INSERT INTO ip_adjust_log VALUES ('b1', 'BATCH1', 0), ('b2', 'BATCH2', 0), ('b2', 'BATCH1', 1)");
                statement.execute("INSERT INTO ip_adjust_step VALUES ('BATCH1', 'pending'), ('BATCH1', 'pending'), ('BATCH2', 'approved')");
            }

            AutoAdjustMapper mapper = session.getMapper(AutoAdjustMapper.class);
            List<String> candidates = Arrays.asList("B1", "B2", "B3");
            assertThat(mapper.queryActiveSecurityPoolRows(candidates)).extracting("securityCode", "poolId")
                    .containsExactlyInAnyOrder(tuple("B1", 17L), tuple("B1", 17L), tuple("B2", 15L));
            assertThat(mapper.queryPendingSecurityCodes(candidates)).containsExactly("B1");
            assertThat(mapper.queryScheduledBlacklistCompanyCodes(Arrays.asList("C1", "C2", "C3", "B2"),
                    Collections.emptyList(), 15L, 23L)).containsExactlyInAnyOrder("C1", "C2");
        }
    }
}
