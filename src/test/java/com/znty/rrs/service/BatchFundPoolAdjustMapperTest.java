package com.znty.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.entity.batchfundpooladjust.BatchFundPoolAdjustReq;
import com.znty.rrs.entity.batchfundpooladjust.BatchFundPoolDto;
import com.znty.rrs.entity.fundpooladjust.FundInfoDto;
import com.znty.rrs.mapper.BatchFundPoolAdjustMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
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

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实 MyBatis XML 和 H2 验证基金独立表、方向、筛选及在池计数 */
public class BatchFundPoolAdjustMapperTest {
    /** 测试内存数据库 */
    private EmbeddedDatabase database;
    /** MyBatis 测试会话 */
    private SqlSession session;
    /** 真实批量查询 Mapper */
    private BatchFundPoolAdjustMapper mapper;

    /** 构造独立基金主档及不同状态的池成员，不连接外部数据库。 */
    @Before
    public void setUp() throws Exception {
        String javaVersion = System.getProperty("java.specification.version");
        int major = Integer.parseInt(javaVersion.startsWith("1.") ? javaVersion.substring(2) : javaVersion);
        Assume.assumeTrue("H2 2.3.232 持久化测试需 Java 11 以上运行时", major >= 11);
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2).build();
        JdbcTemplate jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE ALIAS JSON_CONTAINS FOR 'com.znty.rrs.service.BatchFundPoolAdjustMapperTest.jsonContains'");
        jdbc.execute("CREATE TABLE ip_investment_pool (id BIGINT PRIMARY KEY, parent_id BIGINT, pool_name VARCHAR(100), "
                + "pool_type VARCHAR(20), market_codes VARCHAR(100), variety_codes VARCHAR(100), description VARCHAR(100), "
                + "max_capacity BIGINT, status VARCHAR(20), is_deleted INT, outer_sort INT, inner_sort INT)");
        jdbc.execute("CREATE TABLE ip_pool_status_fund (fund_code VARCHAR(30), target_pool_id BIGINT, audit_status VARCHAR(10), is_deleted INT)");
        jdbc.execute("CREATE TABLE dict_security_type (security_type VARCHAR(30), security_type_name VARCHAR(100), category_type VARCHAR(30), is_deleted INT)");
        jdbc.execute("CREATE TABLE rrs_fundinfo (fund_code VARCHAR(30), fund_name VARCHAR(100), fund_short_name VARCHAR(100), "
                + "security_type VARCHAR(30), security_status VARCHAR(10), market_code VARCHAR(30), currency_code VARCHAR(10), "
                + "latest_nav DECIMAL(10,4), establishment_date DATE, fund_manager_names VARCHAR(100), fund_administrator VARCHAR(100), "
                + "fund_custodian VARCHAR(100), accumulated_nav DECIMAL(10,4), investment_style VARCHAR(100), "
                + "trade_price DECIMAL(10,4), issue_scale DECIMAL(10,4), latest_scale DECIMAL(10,4), data_date DATE, updt_time TIMESTAMP, is_deleted INT)");
        jdbc.update("INSERT INTO dict_security_type VALUES ('bond_fund', '债券基金', 'fund', 0), ('stock', '股票', 'stock', 0)");
        jdbc.update("INSERT INTO ip_investment_pool VALUES "
                + "(1, NULL, '基金大库', 'fund', '[\"SH\"]', '[\"fund\"]', '', 100, 'enabled', 0, 1, 1), "
                + "(10, 1, '基金池', 'fund', '[\"SH\"]', '[\"fund\"]', '', 100, 'enabled', 0, 1, 2), "
                + "(20, NULL, '债券池', 'bond', '[\"SH\"]', '[\"bond\"]', '', 100, 'enabled', 0, 2, 1), "
                + "(30, NULL, '停用基金池', 'fund', '[\"SH\"]', '[\"fund\"]', '', 100, 'disabled', 0, 3, 1), "
                + "(40, NULL, '空基金池', 'fund', '[\"SH\"]', '[\"fund\"]', '', 100, 'enabled', 0, 4, 1)");
        jdbc.update("INSERT INTO rrs_fundinfo (fund_code, fund_short_name, security_type, security_status, fund_administrator, is_deleted) VALUES "
                + "('F1', '在池基金', 'bond_fund', 'A', '管理人甲', 0), "
                + "('F2', '候选基金', 'bond_fund', 'L', '管理人乙', 0), "
                + "('F3', '已出池基金', 'bond_fund', 'A', '管理人甲', 0), "
                + "('F4', '已终止基金', 'bond_fund', 'D', '管理人甲', 0), "
                + "('F5', '已删除基金', 'bond_fund', 'A', '管理人甲', 1), "
                + "('F6', '非基金', 'stock', 'A', '管理人甲', 0)");
        jdbc.update("INSERT INTO ip_pool_status_fund VALUES ('F1', 10, '20', 0), ('F1', 10, '20', 0), "
                + "('F2', 10, '00', 0), ('F3', 10, '20', 1)");
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("batch-fund-test", new JdbcTransactionFactory(), database));
        String resource = "mapper/BatchFundPoolAdjustMapper.xml";
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(configuration).openSession();
        mapper = session.getMapper(BatchFundPoolAdjustMapper.class);
    }

    /** 关闭会话及测试内存数据库。 */
    @After
    public void tearDown() {
        if (session != null) {
            session.close();
        }
        if (database != null) {
            database.shutdown();
        }
    }

    /** 调入候选排除有效在池、终止、删除和非基金，保留有效临时基金。 */
    @Test
    public void inboundCandidatesShouldUseOnlyActiveFundMasterAndEffectiveMembership() {
        BatchFundPoolAdjustReq req = new BatchFundPoolAdjustReq();
        req.setPoolId(10L);
        req.setDirection("in");
        assertThat(mapper.queryFundPage(req)).extracting(FundInfoDto::getFundCode).containsExactly("F2", "F3");
    }

    /** 调出候选仅包含审批通过且未删除的基金成员。 */
    @Test
    public void outboundCandidatesShouldRequireEffectiveFundMembership() {
        BatchFundPoolAdjustReq req = new BatchFundPoolAdjustReq();
        req.setPoolId(10L);
        req.setDirection("out");
        assertThat(mapper.queryFundPage(req)).extracting(FundInfoDto::getFundCode).containsExactly("F1");
    }

    /** 基金代码、简称、产品类型和管理人筛选均作用于候选基金。 */
    @Test
    public void candidatesShouldApplyAllFundFilters() {
        BatchFundPoolAdjustReq req = new BatchFundPoolAdjustReq();
        req.setPoolId(10L);
        req.setDirection("in");
        req.setFundCode("F2");
        req.setFundShortName("候选");
        req.setSecurityType("bond_fund");
        req.setFundAdministrator("乙");
        assertThat(mapper.queryFundPage(req)).extracting(FundInfoDto::getFundCode).containsExactly("F2");
        req.setFundAdministrator("甲");
        assertThat(mapper.queryFundPage(req)).isEmpty();
    }

    /** 叶子池列表不含停用池或债券池，当前数量按基金独立成员去重。 */
    @Test
    public void poolPageShouldReturnEnabledFundLeavesAndDistinctFundCount() {
        BatchFundPoolAdjustReq req = new BatchFundPoolAdjustReq();
        assertThat(mapper.queryPoolPage(req)).extracting(BatchFundPoolDto::getId).containsExactly(10L, 40L);
        assertThat(mapper.queryPoolPage(req)).extracting(BatchFundPoolDto::getCurrentCount).containsExactly(1, 0);
        req.setPoolIds(Collections.singletonList(40L));
        assertThat(mapper.queryPoolPage(req)).extracting(BatchFundPoolDto::getId).containsExactly(40L);
        assertThat(mapper.queryEnabledFundLeafPoolCount(10L)).isEqualTo(1);
        assertThat(mapper.queryEnabledFundLeafPoolCount(1L)).isZero();
        assertThat(mapper.queryEnabledFundLeafPoolCount(20L)).isZero();
        assertThat(mapper.queryEnabledFundLeafPoolCount(30L)).isZero();
    }

    /** H2 测试库提供等价的数组包含函数，以执行现有 MySQL JSON 筛选表达式。 */
    public static boolean jsonContains(String document, String candidate) throws IOException {
        if (document == null || candidate == null) {
            return false;
        }
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode target = objectMapper.readTree(candidate);
        JsonNode array = objectMapper.readTree(document);
        for (JsonNode node : array) {
            if (node.equals(target)) {
                return true;
            }
        }
        return false;
    }
}
