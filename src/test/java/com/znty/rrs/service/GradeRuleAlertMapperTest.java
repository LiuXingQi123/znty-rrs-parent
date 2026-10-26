package com.znty.rrs.service;

import com.znty.rrs.entity.graderulealert.GradeRuleAlertScanDto;
import com.znty.rrs.entity.securitypooladjust.RelatedRatingCompanyDto;
import com.znty.rrs.mapper.GradeRuleAlertMapper;
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
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** 用真实 Mapper 验证扫描来源日志映射及按实际主体取最新内评 */
public class GradeRuleAlertMapperTest {

    /** 隔离测试数据库 */
    private EmbeddedDatabase database;
    /** 执行真实 XML 的会话 */
    private SqlSession session;

    /** 创建隔离表结构并加载真实 Mapper */
    @Before
    public void setUp() throws Exception {
        String version = System.getProperty("java.specification.version");
        int major = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 持久化测试需 Java 11 以上运行时", major >= 11);
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2).build();
        Configuration configuration = new Configuration(new Environment("test",
                new JdbcTransactionFactory(), database));
        configuration.setMapUnderscoreToCamelCase(true);
        for (String resource : new String[]{"mapper/GradeRuleAlertMapper.xml", "mapper/SecurityPoolAdjustMapper.xml"}) {
            try (InputStream input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        session = new SqlSessionFactoryBuilder().build(configuration).openSession(true);
        try (Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE TABLE ip_pool_status (security_code VARCHAR(40), security_short_name VARCHAR(100),"
                    + " target_pool_id BIGINT, target_pool_name VARCHAR(100), adjust_log_id BIGINT,"
                    + " is_deleted INT, audit_status VARCHAR(2))");
            statement.execute("CREATE TABLE ip_investment_pool (id BIGINT, inner_sort INT, pool_type VARCHAR(40),"
                    + " is_deleted INT, pool_level INT)");
            statement.execute("CREATE TABLE rrs_securityinfo (wind_code VARCHAR(40), issuer_code VARCHAR(40), issuer VARCHAR(100))");
            statement.execute("CREATE SCHEMA ais_inv_analysis");
            // 模拟评级视图的输出，验证查询只取同主体最新一条，最新空评分也不能回退旧值
            statement.execute("CREATE TABLE ais_inv_analysis.v_inv_grade_result (windcode VARCHAR(40),"
                    + " windname VARCHAR(100), total_score VARCHAR(20), ts TIMESTAMP)");
            statement.execute("INSERT INTO ip_investment_pool VALUES"
                    + " (2, 1, 'credit_bond', 0, 2), (3, 2, 'credit_bond', 0, 2),"
                    + " (9, 1, 'overseas_bond', 0, 2)");
            statement.execute("INSERT INTO rrs_securityinfo VALUES ('ABS.SH', 'ISSUER', '发行主体')");
            statement.execute("INSERT INTO ip_pool_status VALUES"
                    + " ('ABS.SH', '测试ABS', 2, '一级库', 101, 0, '20'),"
                    + " ('ABS.SH', '测试ABS', 3, '二级库', 102, 0, '20'),"
                    + " ('ABS.SH', '旧记录', 2, '一级库', 99, 1, '20'),"
                    + " ('ABS.SH', '审批中', 2, '一级库', 103, 0, '00'),"
                    + " ('ABS.SH', '境外债', 9, '境外库', 104, 0, '20')");
            statement.execute("INSERT INTO ais_inv_analysis.v_inv_grade_result VALUES"
                    + " ('A', '权益人A旧名', '1', TIMESTAMP '2026-01-01 00:00:00'),"
                    + " ('A', '权益人A', '2', TIMESTAMP '2026-10-25 00:00:00'),"
                    + " ('B', '权益人B', '1', TIMESTAMP '2026-10-25 00:00:00'),"
                    + " ('EMPTY', '旧有评分', '1', TIMESTAMP '2026-01-01 00:00:00'),"
                    + " ('EMPTY', '最新无评分', NULL, TIMESTAMP '2026-10-25 00:00:00')");
        }
    }

    /** 同券不同池保留各自来源日志，扫描范围与原来保持一致 */
    @Test
    public void shouldMapExactEntryLogOfEachGradedPool() {
        List<GradeRuleAlertScanDto> rows = session.getMapper(GradeRuleAlertMapper.class).queryGradedBondInPoolList();
        assertThat(rows).extracting(GradeRuleAlertScanDto::getSecurityCode,
                GradeRuleAlertScanDto::getCurrentPoolId, GradeRuleAlertScanDto::getAdjustLogId)
                .containsExactlyInAnyOrder(tuple("ABS.SH", 2L, 101L), tuple("ABS.SH", 3L, 102L));
        assertThat(rows).extracting(GradeRuleAlertScanDto::getIssuerCode).containsOnly("ISSUER");
    }

    /** 按精确主体代码查询最新评分，其他主体的高评分不能影响结果 */
    @Test
    public void shouldReadLatestRatingOfExactCompany() {
        RelatedRatingCompanyDto rating = session.getMapper(SecurityPoolAdjustMapper.class)
                .queryLatestRatingCompanyByCode("A");
        assertThat(rating.getCompanyCode()).isEqualTo("A");
        assertThat(rating.getCompanyName()).isEqualTo("权益人A");
        assertThat(rating.getInnerRating()).isEqualTo("2");
        assertThat(session.getMapper(SecurityPoolAdjustMapper.class).queryLatestRatingCompanyByCode("UNKNOWN")).isNull();
    }

    /** 最新记录为空评分时仍返回该条，不偷偷寻找以前有评分的记录 */
    @Test
    public void shouldKeepMissingRatingOfLatestRecord() {
        RelatedRatingCompanyDto rating = session.getMapper(SecurityPoolAdjustMapper.class)
                .queryLatestRatingCompanyByCode("EMPTY");
        assertThat(rating.getCompanyName()).isEqualTo("最新无评分");
        assertThat(rating.getInnerRating()).isNull();
    }

    /** 释放会话及隔离测试数据库 */
    @After
    public void tearDown() {
        if (session != null) {
            session.close();
        }
        if (database != null) {
            database.shutdown();
        }
    }
}
