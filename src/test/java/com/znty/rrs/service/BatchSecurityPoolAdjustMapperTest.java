package com.znty.rrs.service;

import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityCandidateDto;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityPoolAdjustReq;
import com.znty.rrs.mapper.BatchSecurityPoolAdjustMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.io.InputStream;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实 MyBatis XML 验证批量调入调出的发行人筛选 */
public class BatchSecurityPoolAdjustMapperTest {

    /** 隔离测试数据库 */
    private EmbeddedDatabase database;
    /** 真实 Mapper 会话 */
    private SqlSession session;
    /** 候选证券查询接口 */
    private BatchSecurityPoolAdjustMapper mapper;

    /** 准备不同发行人、方向和证券状态的候选数据 */
    @Before
    public void setUp() throws Exception {
        String version = System.getProperty("java.specification.version");
        int major = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 测试需 Java 11 以上运行时", major >= 11);
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2).build();
        JdbcTemplate jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE TABLE rrs_securityinfo (wind_code VARCHAR(30), short_name VARCHAR(100), "
                + "security_type VARCHAR(20), security_status VARCHAR(10), issuer VARCHAR(100), issuer_code VARCHAR(30), "
                + "rating_bond VARCHAR(20), rating_bondissuer VARCHAR(20), inner_issuer_rating VARCHAR(20), "
                + "abs_flag INT, guarant_flag INT, yx_flag INT, cj_flag INT, issue_type VARCHAR(20), inner_class VARCHAR(20), "
                + "inright_flag INT, std_credit_flag INT, maturity_date VARCHAR(20), date_exists DECIMAL(10,2), "
                + "date_exists_str VARCHAR(30), guarantor VARCHAR(100), guarantor_id VARCHAR(30), "
                + "security_source VARCHAR(20), market VARCHAR(20))");
        jdbc.execute("CREATE TABLE ip_pool_status (security_code VARCHAR(30), target_pool_id BIGINT, "
                + "audit_status VARCHAR(10), is_deleted INT)");
        jdbc.execute("CREATE TABLE rrs_temp_security_code (temp_security_code VARCHAR(30), temp_security_market VARCHAR(20))");
        jdbc.update("INSERT INTO rrs_securityinfo (wind_code, short_name, security_type, security_status, "
                + "issuer, issuer_code, market, abs_flag) VALUES "
                + "('IN1', '测试债', 'bond', 'L', '测试发行人甲', 'ISSUER-A', 'NIB', 1), "
                + "('OUT1', '测试债', 'bond', 'L', '测试发行人甲', 'ISSUER-A', 'NIB', 1), "
                + "('IN2', '其他债', 'bond', 'L', '另一发行人', 'ISSUER-B', 'SSE', 0), "
                + "('OUT2', '其他债', 'bond', 'L', '另一发行人', 'ISSUER-B', 'SSE', 0), "
                + "('DELISTED', '测试债', 'bond', 'D', '测试发行人甲', 'ISSUER-A', 'NIB', 1), "
                + "('COMPANY', '测试债', 'company', 'L', '测试发行人甲', 'ISSUER-A', 'NIB', 1), "
                + "('CRMW', '测试债', 'crmw', 'L', '测试发行人甲', 'ISSUER-A', 'NIB', 1)");
        jdbc.update("INSERT INTO ip_pool_status VALUES ('OUT1', 2, '20', 0), ('OUT2', 2, '20', 0), "
                + "('IN1', 2, '00', 0), ('IN2', 2, '20', 1)");
        Configuration configuration = new Configuration(new Environment("batch-security-test",
                new JdbcTransactionFactory(), database));
        configuration.setMapUnderscoreToCamelCase(true);
        String resource = "mapper/BatchSecurityPoolAdjustMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(configuration).openSession();
        mapper = session.getMapper(BatchSecurityPoolAdjustMapper.class);
    }

    /** 调入调出均仅按发行人名称模糊匹配，且不绕过方向和证券状态限制 */
    @Test
    public void shouldFilterOnlyIssuerNameForBothDirections() {
        for (String direction : new String[]{"in", "out"}) {
            BatchSecurityPoolAdjustReq req = new BatchSecurityPoolAdjustReq();
            req.setPoolId(2L);
            req.setDirection(direction);
            String expectedCode = "in".equals(direction) ? "IN1" : "OUT1";
            req.setIssuer("发行人甲");
            assertThat(mapper.querySecurityPage(req)).extracting(BatchSecurityCandidateDto::getSecurityCode)
                    .containsExactly(expectedCode);
            req.setIssuer("UER-A");
            assertThat(mapper.querySecurityPage(req)).isEmpty();
            req.setIssuer("不存在的发行人");
            assertThat(mapper.querySecurityPage(req)).isEmpty();
            for (String issuer : new String[]{null, ""}) {
                req.setIssuer(issuer);
                assertThat(mapper.querySecurityPage(req)).extracting(BatchSecurityCandidateDto::getSecurityCode)
                        .containsExactlyInAnyOrder(expectedCode, "in".equals(direction) ? "IN2" : "OUT2");
            }
        }
    }

    /** 发行人筛选与证券代码、简称、市场和特征按 AND 组合 */
    @Test
    public void shouldCombineIssuerWithOtherCandidateFilters() {
        for (String direction : new String[]{"in", "out"}) {
            BatchSecurityPoolAdjustReq req = new BatchSecurityPoolAdjustReq();
            req.setPoolId(2L);
            req.setDirection(direction);
            req.setIssuer("发行人甲");
            req.setSecurityCode("1");
            req.setSecurityShortName("测试");
            req.setMarketCodes(Collections.singletonList("NIB"));
            req.setBondYesFlags(Collections.singletonList("abs"));
            assertThat(mapper.querySecurityPage(req)).extracting(BatchSecurityCandidateDto::getSecurityCode)
                    .containsExactly("in".equals(direction) ? "IN1" : "OUT1");
            req.setIssuer("另一发行人");
            assertThat(mapper.querySecurityPage(req)).isEmpty();
        }
    }

    /** 释放测试会话和数据库 */
    @After
    public void tearDown() {
        if (session != null) session.close();
        if (database != null) database.shutdown();
    }
}
