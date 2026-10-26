package com.znty.rrs.service;

import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实 XML 验证改判仅更新指定活动信用债调入记录 */
public class SecurityPoolAdjustRedirectMapperTest {

    /** 隔离测试数据库 */
    private EmbeddedDatabase database;
    /** 真实 Mapper 会话 */
    private SqlSession session;

    /** 准备同批主申请、配套项及各类不可改判的记录 */
    @Before
    public void setUp() throws Exception {
        String version = System.getProperty("java.specification.version");
        int major = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 测试需 Java 11 以上运行时", major >= 11);
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2).build();
        Configuration configuration = new Configuration(new Environment("test",
                new JdbcTransactionFactory(), database));
        try (InputStream input = Resources.getResourceAsStream("mapper/SecurityPoolAdjustMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "mapper/SecurityPoolAdjustMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(configuration).openSession(true);
        try (Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE TABLE ip_adjust_log (id BIGINT PRIMARY KEY, adjust_batch_no VARCHAR(40),"
                    + " adjust_mode VARCHAR(20), pool_type VARCHAR(40), audit_status VARCHAR(2), is_deleted INT,"
                    + " target_pool_id BIGINT, target_pool_name VARCHAR(100), updt_time TIMESTAMP)");
            statement.execute("INSERT INTO ip_adjust_log VALUES"
                    + " (1, 'BATCH', '调入', 'credit_bond', '00', 0, 3, '二级库', NULL),"
                    + " (2, 'BATCH', '调入', 'observe', '00', 0, 15, '观察池', NULL),"
                    + " (3, 'BATCH', '调出', 'credit_bond', '00', 0, 3, '二级库', NULL),"
                    + " (4, 'BATCH', '调入', 'credit_bond', '20', 0, 3, '二级库', NULL),"
                    + " (5, 'BATCH', '调入', 'credit_bond', '00', 1, 3, '二级库', NULL),"
                    + " (6, 'BATCH', '调入', 'credit_bond', '11', 0, 3, '二级库', NULL),"
                    + " (7, NULL, '调入', 'credit_bond', '00', 0, 3, '二级库', NULL)");
        }
    }

    /** 同批次仅指定 ID 变化，其他信用债记录和配套项都保留 */
    @Test
    public void shouldUpdateOnlySelectedActiveInboundLog() throws Exception {
        assertThat(session.getMapper(SecurityPoolAdjustMapper.class)
                .editAdjustLogTargetPool(1L, 2L, "一级库")).isEqualTo(1);
        try (Statement statement = session.getConnection().createStatement();
             ResultSet rows = statement.executeQuery("SELECT id, target_pool_id FROM ip_adjust_log ORDER BY id")) {
            while (rows.next()) {
                long id = rows.getLong("id");
                assertThat(rows.getLong("target_pool_id")).isEqualTo(id == 1 ? 2L : id == 2 ? 15L : 3L);
            }
        }
    }

    /** 非信用债、调出、已结束及删除记录均不能通过写入接口改判 */
    @Test
    public void shouldRejectNonCreditOutboundFinishedAndDeletedLogs() {
        SecurityPoolAdjustMapper mapper = session.getMapper(SecurityPoolAdjustMapper.class);
        for (long id = 2; id <= 5; id++) {
            assertThat(mapper.editAdjustLogTargetPool(id, 2L, "一级库")).isZero();
        }
        assertThat(mapper.editAdjustLogTargetPool(null, 2L, "一级库")).isZero();
    }

    /** 驳回修改与无批次历史申请也按精确 ID 更新 */
    @Test
    public void shouldSupportModifyAndLegacyLogWithoutBatch() {
        SecurityPoolAdjustMapper mapper = session.getMapper(SecurityPoolAdjustMapper.class);
        assertThat(mapper.editAdjustLogTargetPool(6L, 2L, "一级库")).isEqualTo(1);
        assertThat(mapper.editAdjustLogTargetPool(7L, 2L, "一级库")).isEqualTo(1);
    }

    /** 释放测试资源 */
    @After
    public void tearDown() {
        if (session != null) session.close();
        if (database != null) database.shutdown();
    }
}
