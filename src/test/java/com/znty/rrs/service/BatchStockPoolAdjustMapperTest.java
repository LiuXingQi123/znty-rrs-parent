package com.znty.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.entity.batchstockpooladjust.BatchStockPoolAdjustReq;
import com.znty.rrs.entity.batchstockpooladjust.BatchStockPoolDto;
import com.znty.rrs.entity.stockpooladjust.StockInfoDto;
import com.znty.rrs.mapper.BatchStockPoolAdjustMapper;
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

/** 使用真实 MyBatis XML 和 H2 验证股票独立候选、筛选、评级及有效在池计数 */
public class BatchStockPoolAdjustMapperTest {
    /** 测试内存数据库 */
    private EmbeddedDatabase database;
    /** MyBatis 测试会话 */
    private SqlSession session;
    /** 真实批量查询 Mapper */
    private BatchStockPoolAdjustMapper mapper;

    /** 构造股票状态、市场、评级和池成员，无需真实数据库。 */
    @Before
    public void setUp() throws Exception {
        String javaVersion = System.getProperty("java.specification.version");
        int major = Integer.parseInt(javaVersion.startsWith("1.") ? javaVersion.substring(2) : javaVersion);
        Assume.assumeTrue("H2 2.3.232 持久化测试需 Java 11 以上运行时", major >= 11);
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        JdbcTemplate jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE ALIAS JSON_CONTAINS FOR 'com.znty.rrs.service.BatchStockPoolAdjustMapperTest.jsonContains'");
        jdbc.execute("CREATE TABLE ip_investment_pool (id BIGINT PRIMARY KEY, parent_id BIGINT, pool_name VARCHAR(100), "
                + "pool_type VARCHAR(20), market_codes VARCHAR(100), variety_codes VARCHAR(100), description VARCHAR(100), "
                + "max_capacity BIGINT, status VARCHAR(20), is_deleted INT, outer_sort INT, inner_sort INT)");
        jdbc.execute("CREATE TABLE ip_pool_status_stock (stock_code VARCHAR(30), target_pool_id BIGINT, audit_status VARCHAR(10), is_deleted INT)");
        jdbc.execute("CREATE TABLE dict_security_type (security_type VARCHAR(30), security_type_name VARCHAR(100), category_type VARCHAR(30), is_deleted INT)");
        jdbc.execute("CREATE TABLE rrs_stockinfo (stock_code VARCHAR(30), stock_name VARCHAR(100), stock_short_name VARCHAR(100), "
                + "security_type VARCHAR(30), security_status VARCHAR(10), market_code VARCHAR(30), industry_code VARCHAR(20), delist_date TIMESTAMP, is_deleted INT)");
        jdbc.execute("CREATE TABLE rrs_stock_rating (id BIGINT, stock_code VARCHAR(30), rating_code VARCHAR(20), rating_date TIMESTAMP, is_deleted INT)");
        jdbc.update("INSERT INTO dict_security_type VALUES ('stock_a', 'A股', 'stock', 0), ('fund', '基金', 'fund', 0)");
        jdbc.update("INSERT INTO ip_investment_pool VALUES "
                + "(1, NULL, '股票大库', 'stock', '[\"SSE\"]', '[\"stock\"]', '', 100, 'enabled', 0, 1, 1), "
                + "(10, 1, '股票池', 'stock', '[\"SSE\"]', '[\"stock\"]', '', 100, 'enabled', 0, 1, 2), "
                + "(20, NULL, '基金池', 'fund', '[\"SSE\"]', '[\"fund\"]', '', 100, 'enabled', 0, 2, 1), "
                + "(30, NULL, '停用股票池', 'stock', '[\"SSE\"]', '[\"stock\"]', '', 100, 'disabled', 0, 3, 1), "
                + "(40, NULL, '空股票池', 'stock', '[\"SSE\"]', '[\"stock\"]', '', 100, 'enabled', 0, 4, 1)");
        jdbc.update("INSERT INTO rrs_stockinfo VALUES "
                + "('S1', '在池股票', '在池简称', 'stock_a', 'L', 'SSE', 'A02', NULL, 0), "
                + "('S2', '候选股票', '候选简称', 'stock_a', 'L', 'SZSE', 'A02', NULL, 0), "
                + "('S3', '已出池股票', '港股简称', 'stock_a', 'L', 'HKEX', 'C39', NULL, 0), "
                + "('S4', '已退市股票', '', 'stock_a', 'D', 'SSE', 'A02', NULL, 0), "
                + "('S5', '已删除股票', '', 'stock_a', 'L', 'SSE', 'A02', NULL, 1), "
                + "('S6', '非股票', '', 'fund', 'L', 'SSE', 'A02', NULL, 0), "
                + "('S7', '未上市股票', '', 'stock_a', 'N', 'SSE', 'A02', NULL, 0), "
                + "('S8', '不支持市场', '', 'stock_a', 'L', 'BSE', 'A02', NULL, 0), "
                + "('S9', '到期退市股票', '', 'stock_a', 'L', 'SSE', 'A02', '2000-01-01', 0)");
        jdbc.update("INSERT INTO ip_pool_status_stock VALUES ('S1', 10, '20', 0), ('S1', 10, '20', 0), "
                + "('S2', 10, '00', 0), ('S3', 10, '20', 1)");
        jdbc.update("INSERT INTO rrs_stock_rating VALUES (1, 'S2', 'neutral', '2026-01-01', 0), "
                + "(2, 'S2', 'buy', '2026-02-01', 0), (3, 'S2', 'sell', '2026-03-01', 1)");
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("batch-stock-test", new JdbcTransactionFactory(), database));
        String resource = "mapper/BatchStockPoolAdjustMapper.xml";
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(configuration).openSession();
        mapper = session.getMapper(BatchStockPoolAdjustMapper.class);
    }

    /** 关闭测试会话和内存数据库。 */
    @After
    public void tearDown() {
        if (session != null) { session.close(); }
        if (database != null) { database.shutdown(); }
    }

    /** 调入候选排除在池、退市、删除、未上市、非股票及不支持市场。 */
    @Test
    public void inboundCandidatesShouldUseOnlyEligibleStocksAndLatestRatings() {
        // 以调入方向查询未有效在池的股票
        BatchStockPoolAdjustReq req = request("in");
        assertThat(mapper.queryStockPage(req)).extracting(StockInfoDto::getStockCode).containsExactly("S2", "S3");
        StockInfoDto candidate = mapper.queryStockPage(req).get(0);
        assertThat(candidate.getLatestRating()).isEqualTo("buy");
        assertThat(candidate.getPreviousRating()).isEqualTo("neutral");
    }

    /** 调出候选只认审批通过且未删除的股票成员。 */
    @Test
    public void outboundCandidatesShouldRequireEffectiveMembership() {
        // 以调出方向查询有效股票成员
        assertThat(mapper.queryStockPage(request("out"))).extracting(StockInfoDto::getStockCode).containsExactly("S1");
    }

    /** 股票代码、名称、简称、行业、市场及品种均作用于候选分页。 */
    @Test
    public void candidatesShouldApplyAllStockFilters() {
        // 验证筛选字段共同命中一只股票
        BatchStockPoolAdjustReq req = request("in");
        req.setStockCode("S2"); req.setStockName("候选"); req.setStockShortName("简称");
        req.setIndustryCode("A02"); req.setMarketCode("SZSE"); req.setSecurityType("stock_a");
        assertThat(mapper.queryStockPage(req)).extracting(StockInfoDto::getStockCode).containsExactly("S2");
        req.setIndustryCode("C39");
        assertThat(mapper.queryStockPage(req)).isEmpty();
    }

    /** 逐个验证筛选能排除不匹配候选，避免组合条件掩盖漏用字段。 */
    @Test
    public void candidatesShouldRejectEachNonMatchingFilter() {
        // 每次仅设置一个不匹配筛选，确保各条件独立生效
        BatchStockPoolAdjustReq req = request("in");
        req.setStockCode("missing");
        assertThat(mapper.queryStockPage(req)).isEmpty();
        req.setStockCode(null); req.setStockName("不存在的名称");
        assertThat(mapper.queryStockPage(req)).isEmpty();
        req.setStockName(null); req.setStockShortName("不存在的简称");
        assertThat(mapper.queryStockPage(req)).isEmpty();
        req.setStockShortName(null); req.setIndustryCode("missing");
        assertThat(mapper.queryStockPage(req)).isEmpty();
        req.setIndustryCode(null); req.setMarketCode("SSE");
        assertThat(mapper.queryStockPage(req)).isEmpty();
        req.setMarketCode(null); req.setSecurityType("fund");
        assertThat(mapper.queryStockPage(req)).isEmpty();
    }

    /** 池分页只包含启用股票叶子池，数量按独立有效成员去重。 */
    @Test
    public void poolPageShouldReturnEnabledStockLeavesAndDistinctStockCount() {
        BatchStockPoolAdjustReq req = new BatchStockPoolAdjustReq();
        assertThat(mapper.queryPoolPage(req)).extracting(BatchStockPoolDto::getId).containsExactly(10L, 40L);
        assertThat(mapper.queryPoolPage(req)).extracting(BatchStockPoolDto::getCurrentCount).containsExactly(1, 0);
        req.setPoolIds(Collections.singletonList(40L));
        assertThat(mapper.queryPoolPage(req)).extracting(BatchStockPoolDto::getId).containsExactly(40L);
        assertThat(mapper.queryEnabledStockLeafPoolCount(10L)).isEqualTo(1);
        assertThat(mapper.queryEnabledStockLeafPoolCount(1L)).isZero();
        assertThat(mapper.queryEnabledStockLeafPoolCount(20L)).isZero();
        assertThat(mapper.queryEnabledStockLeafPoolCount(30L)).isZero();
    }

    /** 构造目标池和方向查询。 */
    private BatchStockPoolAdjustReq request(String direction) {
        BatchStockPoolAdjustReq req = new BatchStockPoolAdjustReq();
        req.setPoolId(10L); req.setDirection(direction);
        return req;
    }

    /** 为 H2 提供数组包含函数以执行真实 MySQL JSON 筛选表达式。 */
    public static boolean jsonContains(String document, String candidate) throws IOException {
        if (document == null || candidate == null) { return false; }
        ObjectMapper json = new ObjectMapper();
        JsonNode target = json.readTree(candidate);
        for (JsonNode node : json.readTree(document)) {
            if (node.equals(target)) { return true; }
        }
        return false;
    }
}
