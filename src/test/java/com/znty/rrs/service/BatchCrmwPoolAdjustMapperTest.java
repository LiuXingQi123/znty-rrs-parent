package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.github.pagehelper.PageInterceptor;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwCandidateDto;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwPoolAdjustReq;
import com.znty.rrs.entity.securitypooladjust.SecurityInfoDto;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustReq;
import com.znty.rrs.mapper.BatchCrmwPoolAdjustMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实 SQL 验证产品库内债券与 CRMW 的候选组合范围 */
public class BatchCrmwPoolAdjustMapperTest {

    /** 隔离测试数据库 */
    private EmbeddedDatabase database;
    /** 真实 Mapper 会话 */
    private SqlSession session;
    /** CRMW 批量候选查询接口 */
    private BatchCrmwPoolAdjustMapper mapper;
    /** 测试数据操作入口 */
    private JdbcTemplate jdbc;

    /** 准备债券产品库1的多级子库、其他产品库和各类无效在池记录 */
    @Before
    public void setUp() throws Exception {
        String version = System.getProperty("java.specification.version");
        int major = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 测试需 Java 11 以上运行时", major >= 11);
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2).build();
        jdbc = new JdbcTemplate(database);
        jdbc.execute("SET MODE MySQL");
        jdbc.execute("CREATE TABLE ip_investment_pool (id BIGINT, parent_id BIGINT, pool_code VARCHAR(50), is_deleted INT)");
        jdbc.execute("CREATE TABLE ip_pool_status (security_code VARCHAR(30), target_pool_id BIGINT, "
                + "audit_status VARCHAR(10), is_deleted INT)");
        jdbc.execute("CREATE TABLE ip_pool_status_crmw (security_code VARCHAR(30), crmw_scode VARCHAR(30), "
                + "crmw_stype VARCHAR(20), target_pool_id BIGINT, audit_status VARCHAR(10), is_deleted INT, "
                + "pool_type VARCHAR(20), crmw_name VARCHAR(100))");
        jdbc.execute("CREATE TABLE dict_security_type (security_type VARCHAR(20), security_type_name VARCHAR(100), "
                + "category_type VARCHAR(20), is_deleted INT, sort_order INT)");
        jdbc.execute("CREATE TABLE rrs_temp_security_code (temp_security_code VARCHAR(30), temp_security_market VARCHAR(20))");
        jdbc.execute("CREATE TABLE rrs_securityinfo (wind_code VARCHAR(30), short_name VARCHAR(100), "
                + "security_type VARCHAR(20), security_status VARCHAR(10), issuer VARCHAR(100), "
                + "rating_bond VARCHAR(20), rating_bondissuer VARCHAR(20), inner_issuer_rating VARCHAR(20), "
                + "abs_flag INT, guarant_flag INT, yx_flag INT, cj_flag INT, issue_type VARCHAR(20), inner_class VARCHAR(20), "
                + "inright_flag INT, std_credit_flag INT, maturity_date VARCHAR(20), date_exists DECIMAL(10,2), "
                + "date_exists_str VARCHAR(30), guarantor VARCHAR(100), guarantor_id VARCHAR(30), "
                + "security_source VARCHAR(20), market VARCHAR(20))");
        jdbc.update("INSERT INTO ip_investment_pool VALUES (101, NULL, 'bond_product_root', 0), "
                + "(102, 101, 'product_a', 0), (103, 102, 'nested_a', 0), "
                + "(201, NULL, 'bond_product1_root', 0), (202, 201, 'product_b', 0), "
                + "(203, 202, 'nested_b', 0), (204, 201, 'deleted_product', 1), (205, 201, 'product_c', 0), "
                + "(301, NULL, 'other_product_root', 0)");
        jdbc.update("INSERT INTO dict_security_type VALUES ('bond', '债券', 'bond', 0, 1), "
                + "('abs', 'ABS', 'bond', 0, 2), ('crmw', 'CRMW', 'bond', 0, 3), "
                + "('company', '主体', 'company', 0, 4), ('stock', '股票', 'stock', 0, 5), "
                + "('fund', '基金', 'fund', 0, 6)");
        jdbc.update("INSERT INTO rrs_securityinfo (wind_code, short_name, security_type, security_status, market, "
                + "std_credit_flag, abs_flag) VALUES "
                + "('B1', '产品库债券一', 'bond', 'L', 'NIB', 1, 0), "
                + "('B2', '产品库债券二', 'abs', 'L', 'SSE', 0, 1), "
                + "('C1', '产品库凭证一', 'crmw', 'L', 'NIB', 0, 0), "
                + "('C2', '产品库凭证二', 'crmw', 'L', 'NIB', 0, 0), "
                + "('OUT_B', '库外债券', 'bond', 'L', 'NIB', 1, 0), "
                + "('OUT_C', '库外凭证', 'crmw', 'L', 'NIB', 0, 0), "
                + "('DELETED_B', '已出库债券', 'bond', 'L', 'NIB', 1, 0), "
                + "('PENDING_C', '审批中凭证', 'crmw', 'L', 'NIB', 0, 0), "
                + "('DELETED_POOL_B', '已删除池债券', 'bond', 'L', 'NIB', 1, 0), "
                + "('D_B', '退市债券', 'bond', 'D', 'NIB', 1, 0), "
                + "('D_C', '退市凭证', 'crmw', 'D', 'NIB', 0, 0), "
                + "('COMPANY', '主体', 'company', 'L', 'NIB', 0, 0), "
                + "('STOCK', '股票', 'stock', 'L', 'SSE', 0, 0), "
                + "('FUND', '基金', 'fund', 'L', 'SSE', 0, 0)");
        jdbc.update("INSERT INTO ip_pool_status VALUES ('B1', 203, '20', 0), ('B1', 205, '20', 0), "
                + "('B2', 205, '20', 0), ('C1', 202, '20', 0), ('C1', 203, '20', 0), ('C2', 201, '20', 0), "
                + "('OUT_B', 301, '20', 0), ('OUT_C', 301, '20', 0), ('DELETED_B', 202, '20', 1), "
                + "('PENDING_C', 202, '00', 0), ('DELETED_POOL_B', 204, '20', 0), "
                + "('D_B', 202, '20', 0), ('D_C', 202, '20', 0), ('COMPANY', 202, '20', 0), "
                + "('STOCK', 202, '20', 0), ('FUND', 202, '20', 0)");
        Configuration configuration = new Configuration(new Environment("batch-crmw-test",
                new JdbcTransactionFactory(), database));
        configuration.setMapUnderscoreToCamelCase(true);
        PageInterceptor interceptor = new PageInterceptor();
        Properties properties = new Properties();
        properties.setProperty("helperDialect", "mysql");
        interceptor.setProperties(properties);
        configuration.addInterceptor(interceptor);
        for (String resource : new String[]{"mapper/BatchCrmwPoolAdjustMapper.xml", "mapper/SecurityPoolAdjustMapper.xml"}) {
            try (InputStream input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        session = new SqlSessionFactoryBuilder().build(configuration).openSession();
        mapper = session.getMapper(BatchCrmwPoolAdjustMapper.class);
    }

    /** 债券产品库1内跨子库组合，多级子库和多池持有均正确处理 */
    @Test
    public void shouldCombineAllProductBondsAndCertificatesWithoutDuplicates() {
        BatchCrmwPoolAdjustReq req = new BatchCrmwPoolAdjustReq();
        req.setPoolId(900L);
        assertThat(mapper.queryInboundCandidatePage(req))
                .extracting(row -> row.getCrmwScode() + "|" + row.getSecurityCode())
                .containsExactly("C1|B1", "C1|B2", "C2|B1", "C2|B2");
    }

    /** 仅在债券产品库及其下级池中的债券和凭证均不参与调入组合 */
    @Test
    public void shouldExcludeSecuritiesOnlyInOriginalProductPool() {
        jdbc.update("INSERT INTO rrs_securityinfo (wind_code, short_name, security_type, security_status) VALUES "
                + "('OLD_B', '债券产品库债券', 'bond', 'L'), ('OLD_C', '债券产品库凭证', 'crmw', 'L')");
        jdbc.update("INSERT INTO ip_pool_status VALUES ('OLD_B', 101, '20', 0), ('OLD_B', 103, '20', 0), "
                + "('OLD_C', 101, '20', 0), ('OLD_C', 102, '20', 0)");
        BatchCrmwPoolAdjustReq req = new BatchCrmwPoolAdjustReq();
        req.setPoolId(900L);
        assertThat(mapper.queryInboundCandidatePage(req))
                .extracting(row -> row.getCrmwScode() + "|" + row.getSecurityCode())
                .containsExactly("C1|B1", "C1|B2", "C2|B1", "C2|B2");
    }

    /** 只排除目标池中已生效的同凭证同标的组合 */
    @Test
    public void shouldExcludeOnlyEffectiveCombinationsInTargetPool() {
        jdbc.update("INSERT INTO ip_pool_status_crmw VALUES "
                + "('B1', 'C1', 'crmw', 900, '20', 0, 'crmw', '凭证一'), "
                + "('B2', 'C1', 'crmw', 901, '20', 0, 'crmw', '凭证一'), "
                + "('B1', 'C2', 'crmw', 900, '00', 0, 'crmw', '凭证二'), "
                + "('B2', 'C2', 'crmw', 900, '20', 1, 'crmw', '凭证二')");
        BatchCrmwPoolAdjustReq req = new BatchCrmwPoolAdjustReq();
        req.setPoolId(900L);
        assertThat(mapper.queryInboundCandidatePage(req))
                .extracting(row -> row.getCrmwScode() + "|" + row.getSecurityCode())
                .containsExactly("C1|B2", "C2|B1", "C2|B2");
    }

    /** 保留证券、凭证、市场及债券特征筛选，分页总数按去重组合计算 */
    @Test
    public void shouldFilterAndPageProductCombinations() {
        BatchCrmwPoolAdjustReq req = new BatchCrmwPoolAdjustReq();
        req.setPoolId(900L);
        PageHelper.startPage(2, 2);
        List<BatchCrmwCandidateDto> page = mapper.queryInboundCandidatePage(req);
        assertThat(new PageInfo<>(page).getTotal()).isEqualTo(4);
        assertThat(page).extracting(BatchCrmwCandidateDto::getCrmwScode).containsExactly("C2", "C2");
        req.setSecurityCode("B1");
        req.setSecurityShortName("债券一");
        req.setCrmwScode("C2");
        req.setCrmwName("凭证二");
        req.setMarketCodes(Collections.singletonList("NIB"));
        req.setBondYesFlags(Collections.singletonList("stdCredit"));
        assertThat(mapper.queryInboundCandidatePage(req)).extracting(BatchCrmwCandidateDto::getSecurityCode)
                .containsExactly("B1");
        req.setMarketCodes(Collections.singletonList("SSE"));
        assertThat(mapper.queryInboundCandidatePage(req)).isEmpty();
    }

    /** 调出仍查询目标池实际组合，不受本次调入来源范围限制 */
    @Test
    public void shouldKeepOutboundCombinationsOutsideProductPools() {
        jdbc.update("INSERT INTO ip_pool_status_crmw VALUES "
                + "('OUT_B', 'OUT_C', 'crmw', 900, '20', 0, 'crmw', '库外凭证')");
        BatchCrmwPoolAdjustReq req = new BatchCrmwPoolAdjustReq();
        req.setPoolId(900L);
        assertThat(mapper.queryOutboundCandidatePage(req))
                .extracting(row -> row.getCrmwScode() + "|" + row.getSecurityCode())
                .containsExactly("OUT_C|OUT_B");
    }

    /** 证券池调整列表和类型筛选包含 CRMW，同时排除主体、股票、基金和退市证券 */
    @Test
    public void shouldAllowCrmwInSecurityAdjustmentCandidatesAndTypes() {
        SecurityPoolAdjustMapper securityMapper = session.getMapper(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustReq req = new SecurityPoolAdjustReq();
        req.setSecurityType("crmw");
        assertThat(securityMapper.querySecurityPage(req))
                .extracting(SecurityInfoDto::getWindCode)
                .containsExactlyInAnyOrder("C1", "C2", "OUT_C", "PENDING_C");
        assertThat(securityMapper.querySecurityTypeList()).extracting(row -> row.getSecurityType())
                .containsExactly("bond", "abs", "crmw");
        req.setSecurityType(null);
        assertThat(securityMapper.querySecurityPage(req))
                .extracting(SecurityInfoDto::getWindCode)
                .doesNotContain("COMPANY", "STOCK", "FUND", "D_B", "D_C");
    }

    /** 释放测试会话、分页上下文和数据库 */
    @After
    public void tearDown() {
        PageHelper.clearPage();
        if (session != null) session.close();
        if (database != null) database.shutdown();
    }
}
