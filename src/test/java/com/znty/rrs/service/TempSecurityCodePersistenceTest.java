package com.znty.rrs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInterceptor;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeDto;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeReq;
import com.znty.rrs.mapper.TempSecurityCodeMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 使用生产 DDL、真实 Mapper 和 Spring 事务验证临时证券 ID，不连接业务数据库。 */
public class TempSecurityCodePersistenceTest {

    /** 独立内存库查询组件 */
    private JdbcTemplate jdbc;
    /** 生产临时代码 Mapper */
    private TempSecurityCodeMapper mapper;
    /** 带事务的临时代码服务 */
    private TempSecurityCodeService service;

    /** 创建生产表结构及真实 Mapper，市场查询使用固定正式证券的市场。 */
    @Before
    public void setUp() throws Exception {
        PageHelper.clearPage();
        String version = System.getProperty("java.specification.version");
        int major = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 持久化测试需 Java 11 以上运行时", major >= 11);
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("org.h2.Driver");
        source.setUrl("jdbc:h2:mem:temp_security_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        jdbc = new JdbcTemplate(source);
        for (String file : Arrays.asList("rrs_temp_security_code_schema.sql", "rrs_external_import_schema.sql",
                "rrs_security_pool_adjust_schema.sql", "rrs_crmw_pool_status_schema.sql")) {
            // 在隔离内存库使用生产列定义
            createSchema(file);
        }
        jdbc.execute("CREATE ALIAS DATE_FORMAT FOR 'com.znty.rrs.service.TempSecurityCodePersistenceTest.formatDate'");
        jdbc.execute("CREATE SCHEMA ais_inv_ods");
        jdbc.execute("CREATE TABLE ais_inv_ods.wind_cbondissuer (s_info_compcode VARCHAR(32),s_info_compname VARCHAR(200))");
        jdbc.update("INSERT INTO ais_inv_ods.wind_cbondissuer VALUES ('C10001','测试发行主体')");
        jdbc.execute("CREATE TABLE dict_security_type (security_type VARCHAR(64),security_type_name VARCHAR(100)"
                + ",category_type VARCHAR(32),is_deleted INT)");
        jdbc.update("INSERT INTO dict_security_type VALUES ('mtn','中期票据','bond',0)");
        jdbc.update("INSERT INTO rrs_securityinfo(wind_code,sec_id,short_name,security_type,security_status,security_source)"
                + " VALUES ('FORMAL.IB','OFFICIAL_ID','正式证券','mtn','L','official')");
        Configuration config = new Configuration();
        config.setMapUnderscoreToCamelCase(true);
        config.addInterceptor(new PageInterceptor());
        config.setEnvironment(new Environment("test", new SpringManagedTransactionFactory(), source));
        try (InputStream xml = getClass().getResourceAsStream("/mapper/TempSecurityCodeMapper.xml")) {
            new XMLMapperBuilder(xml, config, "TempSecurityCodeMapper.xml", config.getSqlFragments()).parse();
        }
        SqlSessionTemplate session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
        mapper = session.getMapper(TempSecurityCodeMapper.class);
        SecurityMarketService markets = mock(SecurityMarketService.class);
        when(markets.queryMarketCode("FORMAL.IB")).thenReturn("NIB");
        TempSecurityCodeService target = new TempSecurityCodeService();
        ReflectionTestUtils.setField(target, "tempSecurityCodeMapper", mapper);
        ReflectionTestUtils.setField(target, "securityMarketService", markets);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        service = (TempSecurityCodeService) proxy.getProxy();
    }

    /** 释放隔离内存库并清理分页线程状态。 */
    @After
    public void tearDown() {
        PageHelper.clearPage();
        if (jdbc != null) {
            jdbc.execute("SET DB_CLOSE_DELAY 0");
        }
    }

    /** 新增额外写入双表 ID，原证券代码和接口返回字段保持不变。 */
    @Test
    public void addShouldPersistSameIdInRegistrationAndMaster() throws Exception {
        // 通过实际服务新增，证券代码本身不拼前缀
        TempSecurityCodeDto added = service.addTempSecurityCode(addReq("001.IB"));
        assertThat(added.getTempSecurityCode()).isEqualTo("001.IB");
        assertThat(new ObjectMapper().valueToTree(added).has("secId")).isFalse();
        assertThat(jdbc.queryForObject("SELECT sec_id FROM rrs_temp_security_code WHERE id=?", String.class, added.getId()))
                .isEqualTo("TEMP_001.IB");
        assertThat(jdbc.queryForObject("SELECT sec_id FROM rrs_securityinfo WHERE wind_code='001.IB'", String.class))
                .isEqualTo("TEMP_001.IB");
    }

    /** 实际占位 INSERT 失败时，已经插入的登记记录由事务回滚。 */
    @Test
    public void addShouldRollbackRegistrationWhenMasterInsertFails() throws Exception {
        jdbc.execute("ALTER TABLE rrs_securityinfo ADD CONSTRAINT fail_test_code CHECK (wind_code <> 'FAIL.IB')");
        // 触发内存库的实际占位写入失败
        TempSecurityCodeReq failed = addReq("FAIL.IB");
        assertThatThrownBy(() -> service.addTempSecurityCode(failed)).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_temp_security_code", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_securityinfo WHERE wind_code='FAIL.IB'", Integer.class))
                .isZero();
    }

    /** 人工转正保留临时双表 ID，且不会覆盖正式证券的独立 ID。 */
    @Test
    public void manualConversionShouldPreserveTemporaryAndFormalIds() throws Exception {
        // 创建临时登记并通过正式选券进行转正
        TempSecurityCodeDto added = service.addTempSecurityCode(addReq("001.IB"));
        // 构造仅含主键和正式代码的状态请求
        TempSecurityCodeDto updated = service.editTempSecurityCodeToUpdated(operation(added.getId(), "FORMAL.IB"));
        assertThat(updated.getStatus()).isEqualTo("updated");
        // 验证主档禁用不改 ID，正式证券不被覆盖
        assertMasterIds();
    }

    /** 定时转正使用外部补齐的正式代码，并保留临时和正式 ID。 */
    @Test
    public void jobConversionShouldPreserveTemporaryAndFormalIds() throws Exception {
        // 建立等待外部补齐正式代码的临时记录
        TempSecurityCodeDto added = service.addTempSecurityCode(addReq("001.IB"));
        jdbc.update("UPDATE rrs_temp_security_code SET security_code='FORMAL.IB' WHERE sec_id='TEMP_001.IB'");
        assertThat(mapper.queryJobReadyTempSecurityCodeList()).extracting("id").containsExactly(added.getId());
        TempSecurityCodeDto updated = service.editTempSecurityCodeToUpdatedByJob(added.getId());
        assertThat(updated.getStatus()).isEqualTo("updated");
        assertThat(updated.getOprtSource()).isEqualTo("job");
        // 验证定时入口同样不改两类主档 ID
        assertMasterIds();
    }

    /** 取消发行及软删除仅改变状态，保留外部使用的证券 ID。 */
    @Test
    public void cancelAndDeleteShouldPreserveIds() throws Exception {
        // 创建待取消的临时代码
        TempSecurityCodeDto cancelled = service.addTempSecurityCode(addReq("CANCEL.IB"));
        // 取消后读取实际 ID
        TempSecurityCodeDto result = service.editTempSecurityCodeToCancelled(operation(cancelled.getId(), null));
        assertThat(jdbc.queryForObject("SELECT sec_id FROM rrs_temp_security_code WHERE id=?", String.class, cancelled.getId()))
                .isEqualTo("TEMP_CANCEL.IB");
        assertThat(result.getStatus()).isEqualTo("cancelled");
        assertThat(jdbc.queryForObject("SELECT sec_id FROM rrs_securityinfo WHERE wind_code='CANCEL.IB'", String.class))
                .isEqualTo("TEMP_CANCEL.IB");
        // 创建另一条可软删除的临时代码
        TempSecurityCodeDto deleted = service.addTempSecurityCode(addReq("DELETE.IB"));
        // 执行真实软删除并读取已删除列表记录
        service.deleteTempSecurityCode(operation(deleted.getId(), null));
        assertThat(jdbc.queryForObject("SELECT sec_id FROM rrs_temp_security_code WHERE id=?", String.class, deleted.getId()))
                .isEqualTo("TEMP_DELETE.IB");
        assertThat(jdbc.queryForObject("SELECT sec_id FROM rrs_securityinfo WHERE wind_code='DELETE.IB'", String.class))
                .isEqualTo("TEMP_DELETE.IB");
    }

    /** 原始 Demo 的临时登记、占位主档 ID 一致，已转正示例仍保留临时 ID。 */
    @Test
    public void demoShouldKeepRegistrationAndPlaceholderIdsConsistent() throws Exception {
        // 在隔离内存库执行两份 Demo 的原始 INSERT，不执行清空或业务库初始化
        executeDemoInsert("rrs_temp_security_code_demo_data.sql", "rrs_temp_security_code");
        // 读取实际证券主档样例
        executeDemoInsert("rrs_external_import_demo_data.sql", "rrs_securityinfo");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_temp_security_code"
                + " WHERE sec_id IS NULL OR sec_id <> CONCAT('TEMP_',temp_security_code)", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_securityinfo s"
                + " JOIN rrs_temp_security_code t ON t.temp_security_code=s.wind_code"
                + " WHERE s.security_source='temporary' AND s.sec_id=t.sec_id", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT sec_id FROM rrs_temp_security_code WHERE status='updated'", String.class))
                .isEqualTo("TEMP_TMP002.IB");
        assertThat(jdbc.queryForObject("SELECT sec_id FROM rrs_securityinfo WHERE wind_code='ABN002.IB'", String.class))
                .doesNotStartWith("TEMP_");
    }

    /** 从生产 Demo 提取指定表的完整 INSERT，保持字段和值的真实对应关系。 */
    private void executeDemoInsert(String file, String table) throws Exception {
        String sql = new String(Files.readAllBytes(Paths.get("sql", file)), StandardCharsets.UTF_8);
        Matcher insert = Pattern.compile("INSERT INTO `" + table + "`\\s*\\(.*?;", Pattern.DOTALL).matcher(sql);
        assertThat(insert.find()).as("Demo INSERT %s", table).isTrue();
        jdbc.execute(insert.group());
    }

    /** 验证转正后两类主档的 ID 及临时禁用状态。 */
    private void assertMasterIds() {
        assertThat(jdbc.queryForObject("SELECT sec_id FROM rrs_temp_security_code WHERE temp_security_code='001.IB'", String.class))
                .isEqualTo("TEMP_001.IB");
        assertThat(jdbc.queryForObject("SELECT sec_id FROM rrs_securityinfo WHERE wind_code='001.IB'", String.class))
                .isEqualTo("TEMP_001.IB");
        assertThat(jdbc.queryForObject("SELECT security_status FROM rrs_securityinfo WHERE wind_code='001.IB'", String.class))
                .isEqualTo("D");
        assertThat(jdbc.queryForObject("SELECT sec_id FROM rrs_securityinfo WHERE wind_code='FORMAL.IB'", String.class))
                .isEqualTo("OFFICIAL_ID");
    }

    /** 提取原始建表列定义，仅移除 H2 不支持的 MySQL 表选项。 */
    private void createSchema(String file) throws Exception {
        String sql = new String(Files.readAllBytes(Paths.get("sql", file)), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("CREATE TABLE\\s+`?\\w+`?\\s*\\(.*?\\)\\s*ENGINE\\s*=.*?;", Pattern.DOTALL)
                .matcher(sql);
        int count = 0;
        while (matcher.find()) {
            jdbc.execute(matcher.group().replaceFirst("(?s)\\)\\s*ENGINE.*$", ")"));
            count++;
        }
        assertThat(count).as("生产表结构 %s", file).isPositive();
    }

    /** 构造具有完整业务必填项的新增请求。 */
    private TempSecurityCodeReq addReq(String code) throws Exception {
        TempSecurityCodeReq req = new TempSecurityCodeReq();
        req.setTempSecurityCode(code);
        req.setTempSecurityName("测试临时证券");
        req.setTempSecurityMarket("NIB");
        req.setTempSecurityType("mtn");
        req.setTempCompanyCode("C10001");
        SimpleDateFormat dates = new SimpleDateFormat("yyyy-MM-dd");
        req.setTempIssueDate(dates.parse("2026-01-01"));
        req.setTempMaturityDate(dates.parse("2031-01-01"));
        return req;
    }

    /** 构造状态操作请求，临时字段及 ID 不由客户端传入。 */
    private TempSecurityCodeReq operation(Long id, String formalCode) {
        TempSecurityCodeReq req = new TempSecurityCodeReq();
        req.setId(id);
        req.setSecurityCode(formalCode);
        return req;
    }

    /** 为内存库提供生产 Mapper 使用的 MySQL 紧凑日期函数。 */
    public static String formatDate(Timestamp value, String pattern) {
        if (!"%Y%m%d".equals(pattern)) {
            throw new IllegalArgumentException("测试未支持的日期格式：" + pattern);
        }
        return value == null ? null : new SimpleDateFormat("yyyyMMdd").format(value);
    }
}
