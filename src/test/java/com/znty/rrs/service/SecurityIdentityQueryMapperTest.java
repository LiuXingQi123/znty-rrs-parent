package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInterceptor;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityPoolAdjustReq;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustReq;
import com.znty.rrs.entity.securitypooladjust.SecurityInfoDto;
import com.znty.rrs.entity.securitypooladjusthistory.SecurityPoolAdjustHistoryReq;
import com.znty.rrs.entity.securitypoolquery.SecurityPoolQueryReq;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchAdjustReq;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.mapper.BatchSecurityPoolAdjustMapper;
import com.znty.rrs.mapper.SecurityPoolQueryMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustHistoryMapper;
import com.znty.rrs.mapper.StockSecurityBatchAdjustMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.reflection.MetaObject;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证五类证券列表的主体编码与证券全称筛选及返回字段 */
public class SecurityIdentityQueryMapperTest {

    /** 隔离测试数据库 */
    private EmbeddedDatabase database;
    /** 真实 Mapper 会话 */
    private SqlSession session;
    /** MyBatis 字段映射配置 */
    private Configuration configuration;

    /** 准备同名不同编码、同编码不同全称和空字段的证券 */
    @Before
    public void setUp() throws Exception {
        String version = System.getProperty("java.specification.version");
        int major = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 测试需 Java 11 以上运行时", major >= 11);
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2).build();
        JdbcTemplate jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE ALIAS DATE_FORMAT FOR \"com.znty.rrs.service.SecurityIdentityQueryMapperTest.formatDate\"");
        jdbc.execute("CREATE TABLE rrs_securityinfo (wind_code VARCHAR(30), short_name VARCHAR(100), "
                + "full_name VARCHAR(200), issuer_code VARCHAR(30), issuer VARCHAR(100), "
                + "security_type VARCHAR(20), security_status VARCHAR(10), market VARCHAR(20), security_source VARCHAR(20), "
                + "rating_bond VARCHAR(20), rating_bondissuer VARCHAR(20), inner_issuer_rating VARCHAR(20), "
                + "coupon_rate VARCHAR(20), firstissue_date VARCHAR(20), carry_date VARCHAR(20), maturity_date VARCHAR(20), "
                + "delist_date VARCHAR(20), repurchase_date VARCHAR(20), date_exists DECIMAL(10,2), date_exists_str VARCHAR(30), "
                + "abs_flag INT, guarant_flag INT, yx_flag INT, cj_flag INT, inright_flag INT, std_credit_flag INT, "
                + "issue_type VARCHAR(30), inner_class VARCHAR(30), guarantor VARCHAR(100), guarantor_id VARCHAR(30))");
        jdbc.execute("CREATE TABLE dict_security_type (security_type VARCHAR(20), security_type_name VARCHAR(50), "
                + "category_type VARCHAR(20), is_deleted INT, sort_order INT)");
        jdbc.execute("CREATE TABLE ip_investment_pool (id BIGINT, pool_name VARCHAR(100), is_deleted INT)");
        jdbc.execute("CREATE TABLE my_security_pool (id BIGINT, security_code VARCHAR(30), user_id VARCHAR(20), status VARCHAR(10))");
        jdbc.execute("CREATE TABLE ip_pool_status (id BIGINT, security_code VARCHAR(30), security_short_name VARCHAR(100), "
                + "security_type VARCHAR(20), adjuster_name VARCHAR(50), entry_time TIMESTAMP, target_pool_id BIGINT, "
                + "adjust_log_id BIGINT, adjust_batch_no VARCHAR(50), audit_status VARCHAR(10), is_deleted INT)");
        jdbc.execute("CREATE TABLE ip_adjust_log (id BIGINT, security_code VARCHAR(30), security_short_name VARCHAR(100), "
                + "security_type VARCHAR(20), target_pool_id BIGINT, adjust_type VARCHAR(30), adjust_mode VARCHAR(10), "
                + "adjust_batch_no VARCHAR(50), pool_type VARCHAR(20), audit_status VARCHAR(10), adjuster_name VARCHAR(50), "
                + "adjust_reason VARCHAR(100), submit_time TIMESTAMP, is_deleted INT)");
        jdbc.update("INSERT INTO dict_security_type VALUES ('bond', '债券', 'bond', 0, 1)");
        jdbc.update("INSERT INTO rrs_securityinfo (wind_code, short_name, full_name, issuer_code, issuer, security_type, security_status) "
                + "VALUES ('A', '同名债', '测试绿色公司债券', '001234', '主体甲', 'bond', 'L'), "
                + "('B', '同名债', '测试绿色公司债券', '009999', '主体甲', 'bond', 'L'), "
                + "('C', '同名债', '测试普通公司债券', '001234', '主体乙', 'bond', 'L'), "
                + "('D', '空值债', NULL, NULL, NULL, 'bond', 'L')");
        jdbc.update("INSERT INTO ip_pool_status (id, security_code, security_short_name, security_type, target_pool_id, "
                + "audit_status, is_deleted) SELECT 1, wind_code, short_name, security_type, 2, '20', 0 FROM rrs_securityinfo");
        jdbc.update("INSERT INTO ip_adjust_log (id, security_code, security_short_name, security_type, target_pool_id, "
                + "audit_status, is_deleted) SELECT 1, wind_code, short_name, security_type, 2, '20', 0 FROM rrs_securityinfo");
        configuration = new Configuration(new Environment("security-identity-test", new JdbcTransactionFactory(), database));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new PageInterceptor());
        for (String name : Arrays.asList("SecurityPoolAdjust", "SecurityPoolQuery", "SecurityPoolAdjustHistory",
                "BatchSecurityPoolAdjust", "StockSecurityBatchAdjust")) {
            String resource = "mapper/" + name + "Mapper.xml";
            try (InputStream input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        session = new SqlSessionFactoryBuilder().build(configuration).openSession();
    }

    /** 五类列表按独立条件模糊查询，多个条件按 AND 组合且原始字段正常返回 */
    @Test
    public void shouldFilterAndReturnSecurityIdentityForAllLists() {
        Object[][] cases = {
                {"SecurityPoolAdjustMapper.querySecurityPage", new SecurityPoolAdjustReq()},
                {"SecurityPoolQueryMapper.querySecurityPoolPage", new SecurityPoolQueryReq()},
                {"SecurityPoolAdjustHistoryMapper.querySecurityPoolAdjustHistoryPage", new SecurityPoolAdjustHistoryReq()},
                {"BatchSecurityPoolAdjustMapper.querySecurityPage", new BatchSecurityPoolAdjustReq()},
                {"StockSecurityBatchAdjustMapper.querySecurityPage", new StockSecurityBatchAdjustReq()}
        };
        for (Object[] queryCase : cases) {
            String statement = "com.znty.rrs.mapper." + queryCase[0];
            Object req = queryCase[1];
            MetaObject request = configuration.newMetaObject(req);
            if (request.hasSetter("direction")) {
                request.setValue("direction", "out");
                request.setValue("poolId", 2L);
            }
            for (String empty : Arrays.asList(null, "")) {
                request.setValue("issuerCode", empty);
                request.setValue("securityFullName", empty);
                assertThat(querySecurityList(req)).as(statement).hasSize(4);
            }
            request.setValue("issuerCode", "0123");
            assertThat(querySecurityList(req)).as(statement).hasSize(2);
            request.setValue("issuerCode", null);
            request.setValue("securityFullName", "绿色公司");
            assertThat(querySecurityList(req)).as(statement).hasSize(2);
            request.setValue("issuerCode", "0123");
            List<?> matched = querySecurityList(req);
            assertThat(matched).as(statement).hasSize(1);
            MetaObject response = configuration.newMetaObject(matched.get(0));
            assertThat(response.getValue("issuerCode")).isEqualTo("001234");
            assertThat(response.getValue("fullName")).isEqualTo("测试绿色公司债券");
            request.setValue("issuer", "主体乙");
            assertThat(querySecurityList(req)).as(statement).isEmpty();
            request.setValue("issuer", "主体甲");
            assertThat(querySecurityList(req)).as(statement).hasSize(1);
            request.setValue("securityFullName", "不存在的全称");
            assertThat(querySecurityList(req)).as(statement).isEmpty();
            request.setValue("securityFullName", "绿色公司");
            request.setValue("issuerCode", "不存在的编码");
            assertThat(querySecurityList(req)).as(statement).isEmpty();
        }
    }

    /** 新增查询条件仍遵守目标池方向及存量调入来源池范围 */
    @Test
    public void shouldKeepDirectionAndSourceScopeWithIdentityFilters() {
        BatchSecurityPoolAdjustReq batch = new BatchSecurityPoolAdjustReq();
        batch.setIssuerCode("0123");
        batch.setSecurityFullName("绿色公司");
        batch.setDirection("in");
        batch.setPoolId(3L);
        assertThat(querySecurityList(batch)).hasSize(1);
        batch.setPoolId(2L);
        assertThat(querySecurityList(batch)).isEmpty();
        batch.setDirection("out");
        assertThat(querySecurityList(batch)).hasSize(1);
        batch.setPoolId(3L);
        assertThat(querySecurityList(batch)).isEmpty();

        StockSecurityBatchAdjustReq stock = new StockSecurityBatchAdjustReq();
        stock.setIssuerCode("0123");
        stock.setSecurityFullName("绿色公司");
        stock.setDirection("in");
        stock.setPoolId(3L);
        stock.setSourcePoolIds(Collections.singletonList(2L));
        assertThat(querySecurityList(stock)).hasSize(1);
        stock.setSourcePoolIds(Collections.singletonList(9L));
        assertThat(querySecurityList(stock)).isEmpty();
        stock.setDirection("out");
        stock.setPoolId(2L);
        assertThat(querySecurityList(stock)).hasSize(1);
    }

    /** Service 必须把新增条件完整传到真实 Mapper，分页与空条件也正常工作 */
    @Test
    public void shouldBindIdentityParametersThroughMapperInterface() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", session.getMapper(SecurityPoolAdjustMapper.class));
        SecurityPoolAdjustReq req = new SecurityPoolAdjustReq();
        req.setPageSize(2);
        PageResult<SecurityInfoDto> all = service.querySecurityPage(req);
        assertThat(all.getTotal()).isEqualTo(4);
        assertThat(all.getRecords()).hasSize(2);
        req.setSecurityFullName("绿色公司");
        req.setIssuerCode("0123");
        PageResult<SecurityInfoDto> matched = service.querySecurityPage(req);
        assertThat(matched.getTotal()).isEqualTo(1);
        assertThat(matched.getRecords()).extracting(SecurityInfoDto::getWindCode).containsExactly("A");
        req.setIssuerCode("不存在的编码");
        assertThat(service.querySecurityPage(req).getRecords()).isEmpty();
    }

    /** 通过真实 Mapper 接口查询，覆盖 Java 参数绑定与 SQL 执行两层 */
    private List<?> querySecurityList(Object req) {
        if (req instanceof SecurityPoolAdjustReq) {
            return session.getMapper(SecurityPoolAdjustMapper.class).querySecurityPage((SecurityPoolAdjustReq) req);
        }
        if (req instanceof SecurityPoolQueryReq) {
            return session.getMapper(SecurityPoolQueryMapper.class).querySecurityPoolPage((SecurityPoolQueryReq) req);
        }
        if (req instanceof SecurityPoolAdjustHistoryReq) {
            return session.getMapper(SecurityPoolAdjustHistoryMapper.class)
                    .querySecurityPoolAdjustHistoryPage((SecurityPoolAdjustHistoryReq) req);
        }
        if (req instanceof BatchSecurityPoolAdjustReq) {
            return session.getMapper(BatchSecurityPoolAdjustMapper.class).querySecurityPage((BatchSecurityPoolAdjustReq) req);
        }
        return session.getMapper(StockSecurityBatchAdjustMapper.class).querySecurityPage((StockSecurityBatchAdjustReq) req);
    }

    /** 为 H2 提供列表已有的 MySQL 紧凑日期格式函数 */
    public static String formatDate(Date value, String pattern) {
        return value == null ? null : new SimpleDateFormat("yyyyMMdd").format(value);
    }

    /** 释放测试会话与隔离数据库 */
    @After
    public void tearDown() {
        PageHelper.clearPage();
        if (session != null) session.close();
        if (database != null) database.shutdown();
    }
}
