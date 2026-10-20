package com.znty.rrs.service;

import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.schedule.CompanyObserverDto;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.CompanyObserverMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Arrays;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
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
import static org.mockito.Mockito.when;
import static com.znty.rrs.service.CompanyObserverPoolSyncServiceTest.pool;

/** 使用实际 Mapper、建表字段和 Spring 事务验证观察名单同步，不连接 MySQL。 */
public class CompanyObserverPoolSyncPersistenceTest {
    /** 内存数据库访问 */
    private JdbcTemplate jdbc;
    /** 真实事务代理 */
    private CompanyObserverPoolSyncService service;
    /** 真实名单 Mapper */
    private CompanyObserverMapper sourceMapper;
    /** 可配置实际目标池清单 */
    private InvestmentPoolMapper poolMapper;
    /** 模拟前端保存的任务扩展参数 */
    private SysScheduledTaskBo task;

    /** 建立独立 H2 库，加载实际 XML，并通过真实事务代理执行任务。 */
    @Before
    public void setUp() throws Exception {
        String version = System.getProperty("java.specification.version");
        int major = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 数据库测试需要 Java 11 以上运行时", major >= 11);
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:observer_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE ALIAS DATE_FORMAT FOR 'com.znty.rrs.service.CompanyObserverPoolSyncPersistenceTest.formatDate'");
        jdbc.execute("CREATE SCHEMA ais_inv_analysis");
        jdbc.execute("CREATE TABLE ais_inv_analysis.t_inv_company (id BIGINT PRIMARY KEY, wind_code VARCHAR(100), full_name VARCHAR(100))");
        // 直接复用生产观察名单 DDL 的字段定义，仅移除 H2 不支持的 MySQL 表选项
        createTableFromSchema("ais_inv_analysis_schema.sql", "company_observer_view");
        // 直接复用生产日志和状态表定义，验证实际 Mapper 写入字段及主键关联
        createTableFromSchema("rrs_security_pool_adjust_schema.sql", "ip_adjust_log");
        createTableFromSchema("rrs_security_pool_adjust_schema.sql", "ip_pool_status");
        jdbc.execute("CREATE TABLE ip_pool_relation (pool_id BIGINT, relation_type VARCHAR(32), relation_pool_id BIGINT, is_deleted INT)");
        jdbc.execute("CREATE TABLE dict_security_type (security_type VARCHAR(32), category_type VARCHAR(32), is_deleted INT)");
        jdbc.execute("CREATE TABLE rrs_securityinfo (wind_code VARCHAR(32), short_name VARCHAR(100), security_type VARCHAR(32),"
                + " issuer_code VARCHAR(100), issuer VARCHAR(100), security_status VARCHAR(1), maturity_date VARCHAR(8), abs_flag INT)");
        jdbc.update("INSERT INTO dict_security_type VALUES ('company','company',0), ('mtn','bond',0), ('abs','bond',0), ('crmw','bond',0)");
        jdbc.update("INSERT INTO ais_inv_analysis.t_inv_company VALUES (1,'C1','主体一')");
        jdbc.update("INSERT INTO ais_inv_analysis.company_observer_view (company_id,company_code,observer_type,isHo) VALUES (1,'C1','2','0')");
        jdbc.update("INSERT INTO rrs_securityinfo VALUES ('B1','普通债','mtn','C1','主体一','L',NULL,0),"
                + " ('ABS1','ABS债','abs','C1','主体一','N',NULL,1), ('CRMW1','凭证','crmw','C1','主体一','U',NULL,0),"
                + " ('DEL','退市债','mtn','C1','主体一','D',NULL,0)");
        jdbc.update("INSERT INTO rrs_securityinfo VALUES ('TODAY','当天到期','mtn','C1','主体一','L',?,0),"
                + " ('OLD','已到期','mtn','C1','主体一','L',?,0)",
                LocalDate.now().toString().replace("-", ""), LocalDate.now().minusDays(1).toString().replace("-", ""));

        Configuration configuration = new Configuration(new Environment("test", new SpringManagedTransactionFactory(), ds));
        configuration.setMapUnderscoreToCamelCase(true);
        for (String name : new String[]{"CompanyObserverMapper.xml", "AutoAdjustMapper.xml", "SecurityPoolAdjustMapper.xml"}) {
            String resource = "mapper/" + name;
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        SqlSessionTemplate session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
        sourceMapper = session.getMapper(CompanyObserverMapper.class);
        CompanyObserverPoolSyncServiceTest.Fixture fixture = new CompanyObserverPoolSyncServiceTest.Fixture();
        poolMapper = fixture.poolMapper;
        task = fixture.task;
        ReflectionTestUtils.setField(fixture.service, "companyObserverMapper", sourceMapper);
        ReflectionTestUtils.setField(fixture.service, "autoAdjustMapper", session.getMapper(AutoAdjustMapper.class));
        ReflectionTestUtils.setField(fixture.service, "securityPoolAdjustMapper", session.getMapper(SecurityPoolAdjustMapper.class));
        ProxyFactory proxy = new ProxyFactory(fixture.service);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        service = (CompanyObserverPoolSyncService) proxy.getProxy();
    }

    /** 更换扩展参数后，真实SQL只扫描配置池，主体、各类债券与联动池同批进出。 */
    @Test
    public void shouldPersistConfiguredTargetsWithoutTouchingUnconfiguredPools() {
        assertThat(service.execute().getAffectedCount()).isEqualTo(5);
        task.setParamJson("{\"observePoolId\":116,\"keyObservePoolId\":123}");
        when(poolMapper.queryPoolList()).thenReturn(Arrays.asList(pool(116L, "other"),
                pool(123L, "other"), pool(117L, "blacklist")));
        jdbc.update("INSERT INTO ip_pool_relation VALUES (123,'in_linked',117,0), (123,'out_linked',117,0)");
        assertThat(service.execute().getAffectedCount()).isEqualTo(5);
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='3'");
        ScheduledTaskResult switched = service.execute();
        assertThat(switched.isSuccess()).isTrue();
        assertThat(switched.getAffectedCount()).isEqualTo(15);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id=16", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id=116", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id IN (123,117)", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT adjust_batch_no) FROM ip_adjust_log WHERE target_pool_id IN (123,117)", Integer.class)).isEqualTo(1);
        // 名单移除后真实候选SQL仍能从配置池发现主体，未配置旧池及旗下债券保持原样
        jdbc.update("DELETE FROM ais_inv_analysis.company_observer_view");
        assertThat(service.execute().getAffectedCount()).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id IN (123,117)", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0", Integer.class)).isEqualTo(5);
    }

    /** 普通债、ABS、CRMW 和当天到期债随主体切换；退市和已到期债排除；重复执行无日志。 */
    @Test
    public void shouldPersistRoundTripAndRemainIdempotent() {
        assertThat(service.execute().getAffectedCount()).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id=16", Integer.class)).isEqualTo(5);
        assertThat(service.execute().getAffectedCount()).isZero();
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='3'");
        ScheduledTaskResult switched = service.execute();
        assertThat(switched.isSuccess()).isTrue();
        assertThat(switched.getAffectedCount()).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id=23", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id=16", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log", Integer.class)).isEqualTo(15);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_advice IS NOT NULL OR audit_status!='20'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status s JOIN ip_adjust_log l ON l.id=s.adjust_log_id"
                + " WHERE s.is_deleted=0 AND s.adjust_batch_no=l.adjust_batch_no", Integer.class)).isEqualTo(5);
    }

    /** 名单移除后退出两池；主体状态已不存在时仍能发现并清理孤立债券。 */
    @Test
    public void shouldExitRemovedObserversAndOrphanBonds() {
        assertThat(service.execute().isSuccess()).isTrue();
        jdbc.update("DELETE FROM ais_inv_analysis.company_observer_view");
        jdbc.update("DELETE FROM ip_pool_status WHERE security_code='C1'");
        ScheduledTaskResult result = service.execute();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0", Integer.class)).isZero();
    }

    /** 切换过程中实际写入失败时，已完成的主体/债券出池和日志全部回滚，可修复后重试。 */
    @Test
    public void shouldRollbackAllStatusAndLogsWhenBondWriteFails() {
        assertThat(service.execute().getAffectedCount()).isEqualTo(5);
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='3'");
        jdbc.execute("ALTER TABLE ip_pool_status ADD CONSTRAINT reject_observer_bond CHECK (security_code!='ABS1' OR target_pool_id!=23)");
        ScheduledTaskResult result = service.execute();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAffectedCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id=16", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE target_pool_id=23", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log", Integer.class)).isEqualTo(5);
        jdbc.execute("ALTER TABLE ip_pool_status DROP CONSTRAINT reject_observer_bond");
        assertThat(service.execute().getAffectedCount()).isEqualTo(10);
    }

    /** 实际关系配置驱动23和17的主体及普通债/ABS/CRMW同批进出，不依赖后序定时任务。 */
    @Test
    public void shouldPersistConfiguredLinkedPoolsAndBondsWithSameBatch() {
        jdbc.update("INSERT INTO ip_pool_relation VALUES (23,'in_linked',17,0), (23,'out_linked',17,0), (23,'in_linked',17,0)");
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='3'");
        ScheduledTaskResult inbound = service.execute();
        assertThat(inbound.isSuccess()).isTrue();
        assertThat(inbound.getAffectedCount()).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id IN (23,17)", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT adjust_batch_no) FROM ip_adjust_log", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log WHERE target_pool_id=17 AND adjust_type='联动调整'", Integer.class)).isEqualTo(5);
        assertThat(service.execute().getAffectedCount()).isZero();
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='2'");
        ScheduledTaskResult outbound = service.execute();
        assertThat(outbound.isSuccess()).isTrue();
        assertThat(outbound.getAffectedCount()).isEqualTo(15);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id IN (23,17)", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id=16", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT adjust_batch_no) FROM ip_adjust_log WHERE target_pool_id IN (23,17)", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_advice IS NOT NULL", Integer.class)).isZero();
    }

    /** 联动17入池失败时，已完成的23和17主体及债券写入均回滚。 */
    @Test
    public void shouldRollbackSourceAndLinkedInboundWhenLinkedBondWriteFails() {
        jdbc.update("INSERT INTO ip_pool_relation VALUES (23,'in_linked',17,0)");
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='3'");
        jdbc.execute("ALTER TABLE ip_pool_status ADD CONSTRAINT reject_linked_in CHECK (security_code!='ABS1' OR target_pool_id!=17)");
        ScheduledTaskResult failed = service.execute();
        assertThat(failed.isSuccess()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log", Integer.class)).isZero();
        jdbc.execute("ALTER TABLE ip_pool_status DROP CONSTRAINT reject_linked_in");
        assertThat(service.execute().getAffectedCount()).isEqualTo(10);
    }

    /** 联动17调出失败时，已退出23的主体及债券也恢复，避免重点观察与联动池只退出一半。 */
    @Test
    public void shouldRollbackSourceAndLinkedOutboundWhenLinkedBondWriteFails() {
        jdbc.update("INSERT INTO ip_pool_relation VALUES (23,'in_linked',17,0), (23,'out_linked',17,0)");
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='3'");
        assertThat(service.execute().getAffectedCount()).isEqualTo(10);
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='1'");
        jdbc.execute("ALTER TABLE ip_pool_status ADD CONSTRAINT reject_linked_out CHECK (security_code!='ABS1' OR target_pool_id!=17 OR is_deleted=0)");
        ScheduledTaskResult failed = service.execute();
        assertThat(failed.isSuccess()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id IN (23,17)", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log", Integer.class)).isEqualTo(10);
        jdbc.execute("ALTER TABLE ip_pool_status DROP CONSTRAINT reject_linked_out");
        assertThat(service.execute().getAffectedCount()).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0", Integer.class)).isZero();
    }

    /** 一轮实际处理九类关系：来源、双向限制/联动/互斥及弹性禁投，包含主体和全部适用债券。 */
    @Test
    public void shouldApplyAllNineRelationTypesForCompanyAndBonds() {
        when(poolMapper.queryPoolList()).thenReturn(Arrays.asList(pool(16L, "observe"), pool(23L, "restricted"),
                pool(17L, "blacklist"), pool(99L, "other")));
        assertThat(service.execute().getAffectedCount()).isEqualTo(5);
        jdbc.update("INSERT INTO ip_pool_status (security_code,security_short_name,security_type,target_pool_id,audit_status,is_deleted)"
                + " SELECT security_code,security_short_name,security_type,99,'20',0 FROM ip_pool_status WHERE target_pool_id=16 AND is_deleted=0");
        jdbc.update("INSERT INTO ip_pool_status (security_code,security_short_name,security_type,target_pool_id,audit_status,is_deleted)"
                + " SELECT security_code,security_short_name,security_type,97,'20',0 FROM ip_pool_status WHERE target_pool_id=16 AND is_deleted=0");
        jdbc.update("INSERT INTO ip_pool_relation VALUES (23,'source',16,0), (23,'in_restrict',15,0), (23,'out_restrict',15,0),"
                + " (23,'in_linked',17,0), (23,'out_linked',17,0), (23,'in_mutex',99,0), (23,'out_mutex',98,0),"
                + " (23,'in_soft_restrict',97,0), (23,'out_soft_restrict',97,0), (99,'out_soft_restrict',97,0)");
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='3'");
        ScheduledTaskResult inbound = service.execute();
        assertThat(inbound.isSuccess()).isTrue();
        assertThat(inbound.getAffectedCount()).isEqualTo(20);
        assertThat(inbound.getDetailLog()).contains("警告不阻断", "主体[C1]旗下债券：投资池=池23，调入4只，调出0只",
                "主体[C1]旗下债券：投资池=池99，调入0只，调出4只", "主体[C1]旗下债券：投资池=池17，调入4只，调出0只")
                .doesNotContain("B1", "ABS1", "CRMW1", "TODAY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id IN (23,17)", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id IN (16,99)", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log WHERE target_pool_id=99 AND adjust_type='互斥调整'", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT adjust_batch_no) FROM ip_adjust_log WHERE target_pool_id IN (23,17,99)", Integer.class)).isEqualTo(1);
        assertThat(service.execute().getAffectedCount()).isZero();
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='1'");
        ScheduledTaskResult outbound = service.execute();
        assertThat(outbound.isSuccess()).isTrue();
        assertThat(outbound.getAffectedCount()).isEqualTo(10);
        assertThat(outbound.getDetailLog()).contains("调出弹性禁投池（警告不阻断）");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id IN (23,17)", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id=97", Integer.class)).isEqualTo(5);
    }

    /** 互斥配套调出命中自身限制时，主入池、互斥退出及日志全部回滚。 */
    @Test
    public void shouldRollbackWhenGeneratedMutexOutHitsRestriction() {
        when(poolMapper.queryPoolList()).thenReturn(Arrays.asList(pool(16L, "observe"), pool(23L, "restricted"), pool(99L, "other")));
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='3'");
        jdbc.update("INSERT INTO ip_pool_status (security_code,security_short_name,security_type,target_pool_id,audit_status,is_deleted)"
                + " VALUES ('C1','主体一','company',99,'20',0), ('B1','普通债','mtn',99,'20',0), ('B1','普通债','mtn',15,'20',0)");
        jdbc.update("INSERT INTO ip_pool_relation VALUES (23,'in_mutex',99,0), (99,'out_restrict',15,0)");
        ScheduledTaskResult failed = service.execute();
        assertThat(failed.isSuccess()).isFalse();
        assertThat(failed.getMessage()).contains("B1", "池99", "调出限制池");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id=99", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE target_pool_id=23", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log", Integer.class)).isZero();
    }

    /** 债券命中调出互斥池时，先前已调出的主体状态及日志也整体回滚。 */
    @Test
    public void shouldRollbackCompanyOutWhenBondHitsOutboundMutex() {
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='3'");
        jdbc.update("INSERT INTO ip_pool_relation VALUES (23,'in_linked',17,0), (23,'out_linked',17,0)");
        assertThat(service.execute().getAffectedCount()).isEqualTo(10);
        jdbc.update("INSERT INTO ip_pool_status (security_code,security_short_name,security_type,target_pool_id,audit_status,is_deleted)"
                + " VALUES ('B1','普通债','mtn',98,'20',0)");
        jdbc.update("INSERT INTO ip_pool_relation VALUES (23,'out_mutex',98,0)");
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='1'");
        ScheduledTaskResult failed = service.execute();
        assertThat(failed.isSuccess()).isFalse();
        assertThat(failed.getMessage()).contains("B1", "调出互斥池");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id IN (23,17)", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log", Integer.class)).isEqualTo(10);
    }

    /** 查询全量观察名单，主体信息未匹配时任务失败。 */
    @Test
    public void shouldReadEveryIsHoValueAndRetainInvalidMasterMapping() {
        jdbc.update("INSERT INTO ais_inv_analysis.company_observer_view (company_id,company_code,observer_type,isHo)"
                + " VALUES (2,'C2','3','1')");
        assertThat(sourceMapper.queryCompanyObserverList()).extracting(CompanyObserverDto::getCompanyCode).containsExactly("C1", "C2");
        assertThat(sourceMapper.queryCompanyObserverList().get(1).getMasterCompanyCode()).isNull();
        ScheduledTaskResult result = service.execute();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("C2", "主数据不匹配");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log", Integer.class)).isZero();
    }

    /** 从生产脚本提取单张表的列定义，移除 MySQL ENGINE/CHARSET/COLLATE 表选项。 */
    private void createTableFromSchema(String fileName, String tableName) throws Exception {
        String sql = new String(Files.readAllBytes(Paths.get("sql", fileName)), StandardCharsets.UTF_8);
        int start = sql.indexOf("CREATE TABLE `" + tableName + "`");
        int end = sql.indexOf(" ENGINE", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        String ddl = sql.substring(start, end);
        if (fileName.startsWith("ais_inv_analysis")) {
            ddl = ddl.replace("CREATE TABLE `" + tableName + "`", "CREATE TABLE ais_inv_analysis.`" + tableName + "`");
        }
        jdbc.execute(ddl);
    }

    /** 为 H2 提供生产 SQL 的 MySQL 日期函数。 */
    public static String formatDate(Timestamp value, String pattern) {
        if (!"%Y%m%d".equals(pattern)) {
            throw new IllegalArgumentException("测试不支持日期格式：" + pattern);
        }
        return value == null ? null : new SimpleDateFormat("yyyyMMdd").format(value);
    }
}
