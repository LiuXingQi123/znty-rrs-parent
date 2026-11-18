package com.znty.rrs.service;

import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.schedule.CompanyObserverDto;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import com.znty.rrs.entity.securitypooladjust.BatchSecurityCheckData.ScheduledSecurity;
import com.znty.rrs.mapper.CompanyObserverMapper;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.BatchSecurityCheckMapper;
import com.znty.rrs.mapper.ExcelPoolExclusionMapper;
import com.znty.rrs.entity.schedule.PoolSecurityRowDto;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
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
    private AutoAdjustMapper autoAdjustMapper;
    private BatchSecurityCheckMapper batchSecurityCheckMapper;
    private ExcelPoolExclusionService exclusions;
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;
    /** 可配置实际目标池清单 */
    private InvestmentPoolMapper poolMapper;
    /** 模拟前端保存的任务扩展参数 */
    private SysScheduledTaskBo task;
    /** JDBC 实际准备的 SELECT，排除一级缓存命中对查询次数判断的影响 */
    private final List<String> selectSql = new ArrayList<>();

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
        jdbc.execute("CREATE SCHEMA ais_inv_ods");
        jdbc.execute("CREATE TABLE ais_inv_ods.wind_cbondissuer (s_info_windcode VARCHAR(32), s_info_compcode VARCHAR(32), s_info_typecode BIGINT)");
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
        configuration.addInterceptor(new SelectCounter(selectSql));
        for (String name : new String[]{"CompanyObserverMapper.xml", "AutoAdjustMapper.xml", "SecurityPoolAdjustMapper.xml",
                "BatchSecurityCheckMapper.xml", "ExcelPoolExclusionMapper.xml"}) {
            String resource = "mapper/" + name;
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        SqlSessionTemplate session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
        sourceMapper = session.getMapper(CompanyObserverMapper.class);
        autoAdjustMapper = session.getMapper(AutoAdjustMapper.class);
        batchSecurityCheckMapper = session.getMapper(BatchSecurityCheckMapper.class);
        securityPoolAdjustMapper = session.getMapper(SecurityPoolAdjustMapper.class);
        CompanyObserverPoolSyncServiceTest.Fixture fixture = new CompanyObserverPoolSyncServiceTest.Fixture();
        exclusions = AutoAdjustTestSupport.bindExcelExclusion(
                fixture.service, session.getMapper(ExcelPoolExclusionMapper.class));
        poolMapper = fixture.poolMapper;
        task = fixture.task;
        ReflectionTestUtils.setField(fixture.service, "companyObserverMapper", sourceMapper);
        ReflectionTestUtils.setField(fixture.service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ProxyFactory proxy = new ProxyFactory(fixture.service);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        service = (CompanyObserverPoolSyncService) proxy.getProxy();
    }

    /** 更换扩展参数后，真实SQL只扫描配置池，主体、普通债、CRMW 与联动池同批进出，ABS 不参与。 */
    @Test
    public void shouldPersistConfiguredTargetsWithoutTouchingUnconfiguredPools() {
        assertThat(service.execute().getAffectedCount()).isEqualTo(1);
        task.setParamJson("{\"observePoolId\":116,\"keyObservePoolId\":123}");
        when(poolMapper.queryPoolList()).thenReturn(Arrays.asList(pool(116L, "other"),
                pool(123L, "other"), pool(117L, "blacklist")));
        jdbc.update("INSERT INTO ip_pool_relation VALUES (123,'in_linked',117,0), (123,'out_linked',117,0)");
        assertThat(service.execute().getAffectedCount()).isEqualTo(1);
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='3'");
        ScheduledTaskResult switched = service.execute();
        assertThat(switched.isSuccess()).isTrue();
        assertThat(switched.getAffectedCount()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id=16", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id=116", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id IN (123,117)", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT adjust_batch_no) FROM ip_adjust_log WHERE target_pool_id IN (123,117)", Integer.class)).isEqualTo(1);
        // 名单移除后真实候选SQL仍能从配置池发现主体，未配置旧池及旗下债券保持原样
        jdbc.update("DELETE FROM ais_inv_analysis.company_observer_view");
        assertThat(service.execute().getAffectedCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id IN (123,117)", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0", Integer.class)).isEqualTo(1);
    }

    /** 普通债、CRMW 和当天到期债随主体切换，ABS 不参与；退市和已到期债排除；重复执行无日志。 */
    @Test
    public void shouldPersistRoundTripAndRemainIdempotent() {
        assertThat(service.execute().getAffectedCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id=16", Integer.class)).isEqualTo(1);
        assertThat(service.execute().getAffectedCount()).isZero();
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='3'");
        ScheduledTaskResult switched = service.execute();
        assertThat(switched.isSuccess()).isTrue();
        assertThat(switched.getAffectedCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id=23", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id=16", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_advice IS NOT NULL OR audit_status!='20'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status s JOIN ip_adjust_log l ON l.id=s.adjust_log_id"
                + " WHERE s.is_deleted=0 AND s.adjust_batch_no=l.adjust_batch_no", Integer.class)).isEqualTo(1);
    }

    /** 名单移除后退出两池；主体状态已不存在时仍能发现并清理孤立债券，ABS 池状态保持不动。 */
    @Test
    public void shouldExitRemovedObserversAndOrphanBonds() {
        assertThat(service.execute().isSuccess()).isTrue();
        jdbc.update("INSERT INTO ip_pool_status (security_code,security_short_name,security_type,target_pool_id,audit_status,is_deleted)"
                + " VALUES ('ABS1','ABS债','abs',16,'20',0)");
        jdbc.update("DELETE FROM ais_inv_analysis.company_observer_view");
        jdbc.update("INSERT INTO ip_pool_status (security_code,security_type,target_pool_id,audit_status,is_deleted) VALUES ('B1','mtn',16,'20',0)");
        ScheduledTaskResult result = service.execute();
        assertThat(result.isSuccess()).isTrue();
        // 只清理 B1、CRMW1、TODAY 三只非 ABS 债券
        assertThat(result.getAffectedCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND security_code='ABS1'", Integer.class)).isEqualTo(1);
    }

    /** 切换过程中实际写入失败时，已完成的主体/债券出池和日志全部回滚，可修复后重试。 */
    @Test
    public void shouldRollbackAllStatusAndLogsWhenBondWriteFails() {
        assertThat(service.execute().getAffectedCount()).isEqualTo(1);
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='3'");
        jdbc.execute("ALTER TABLE ip_pool_status ADD CONSTRAINT reject_observer_bond CHECK (security_code!='C1' OR target_pool_id!=23)");
        ScheduledTaskResult result = service.execute();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAffectedCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id=16", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE target_pool_id=23", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log", Integer.class)).isEqualTo(1);
        jdbc.execute("ALTER TABLE ip_pool_status DROP CONSTRAINT reject_observer_bond");
        assertThat(service.execute().getAffectedCount()).isEqualTo(2);
    }

    /** 实际关系配置驱动23和17的主体及普通债、CRMW同批进出，ABS 不参与，不依赖后序定时任务。 */
    @Test
    public void shouldPersistConfiguredLinkedPoolsAndBondsWithSameBatch() {
        jdbc.update("INSERT INTO ip_pool_relation VALUES (23,'in_linked',17,0), (23,'out_linked',17,0), (23,'in_linked',17,0)");
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='3'");
        ScheduledTaskResult inbound = service.execute();
        assertThat(inbound.isSuccess()).isTrue();
        assertThat(inbound.getAffectedCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id IN (23,17)", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT adjust_batch_no) FROM ip_adjust_log", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log WHERE target_pool_id=17 AND adjust_type='联动调整'", Integer.class)).isEqualTo(1);
        assertThat(service.execute().getAffectedCount()).isZero();
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='2'");
        ScheduledTaskResult outbound = service.execute();
        assertThat(outbound.isSuccess()).isTrue();
        assertThat(outbound.getAffectedCount()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id IN (23,17)", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id=16", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT adjust_batch_no) FROM ip_adjust_log WHERE target_pool_id IN (23,17)", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_advice IS NOT NULL", Integer.class)).isZero();
    }

    /** 联动17入池失败时，已完成的23和17主体及债券写入均回滚。 */
    @Test
    public void shouldRollbackSourceAndLinkedInboundWhenLinkedBondWriteFails() {
        jdbc.update("INSERT INTO ip_pool_relation VALUES (23,'in_linked',17,0)");
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='3'");
        jdbc.execute("ALTER TABLE ip_pool_status ADD CONSTRAINT reject_linked_in CHECK (security_code!='C1' OR target_pool_id!=17)");
        ScheduledTaskResult failed = service.execute();
        assertThat(failed.isSuccess()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log", Integer.class)).isZero();
        jdbc.execute("ALTER TABLE ip_pool_status DROP CONSTRAINT reject_linked_in");
        assertThat(service.execute().getAffectedCount()).isEqualTo(2);
    }

    /** 联动17调出失败时，已退出23的主体及债券也恢复，避免重点观察与联动池只退出一半。 */
    @Test
    public void shouldRollbackSourceAndLinkedOutboundWhenLinkedBondWriteFails() {
        jdbc.update("INSERT INTO ip_pool_relation VALUES (23,'in_linked',17,0), (23,'out_linked',17,0)");
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='3'");
        assertThat(service.execute().getAffectedCount()).isEqualTo(2);
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='1'");
        jdbc.execute("ALTER TABLE ip_pool_status ADD CONSTRAINT reject_linked_out CHECK (security_code!='C1' OR target_pool_id!=17 OR is_deleted=0)");
        ScheduledTaskResult failed = service.execute();
        assertThat(failed.isSuccess()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id IN (23,17)", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log", Integer.class)).isEqualTo(2);
        jdbc.execute("ALTER TABLE ip_pool_status DROP CONSTRAINT reject_linked_out");
        assertThat(service.execute().getAffectedCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0", Integer.class)).isZero();
    }

    /** 一轮实际处理九类关系：来源、双向限制/联动/互斥及弹性禁投，包含主体和全部适用债券（不含 ABS）。 */
    @Test
    public void shouldApplyAllNineRelationTypesForCompanyAndBonds() {
        when(poolMapper.queryPoolList()).thenReturn(Arrays.asList(pool(16L, "observe"), pool(23L, "restricted"),
                pool(17L, "blacklist"), pool(99L, "other")));
        assertThat(service.execute().getAffectedCount()).isEqualTo(1);
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
        assertThat(inbound.getAffectedCount()).isEqualTo(4);
        assertThat(inbound.getDetailLog()).contains("警告不阻断")
                .doesNotContain("B1", "ABS1", "CRMW1", "TODAY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id IN (23,17)", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id IN (16,99)", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log WHERE target_pool_id=99 AND adjust_type='互斥调整'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT adjust_batch_no) FROM ip_adjust_log WHERE target_pool_id IN (23,17,99)", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT adjust_reason FROM ip_adjust_log WHERE target_pool_id IN (23,17,99)", String.class))
                .hasSize(3)
                .allSatisfy(reason -> assertThat(reason).startsWith("【任务名称：主体观察名单同步（仅主体）】"));
        assertThat(service.execute().getAffectedCount()).isZero();
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='1'");
        ScheduledTaskResult outbound = service.execute();
        assertThat(outbound.isSuccess()).isTrue();
        assertThat(outbound.getAffectedCount()).isEqualTo(2);
        assertThat(outbound.getDetailLog()).contains("调出弹性禁投池（警告不阻断）");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id IN (23,17)", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id=97", Integer.class)).isEqualTo(1);
    }

    /** 互斥配套调出命中自身限制时，主入池、互斥退出及日志全部回滚。 */
    @Test
    public void shouldRollbackWhenGeneratedMutexOutHitsRestriction() {
        when(poolMapper.queryPoolList()).thenReturn(Arrays.asList(pool(16L, "observe"), pool(23L, "restricted"), pool(99L, "other")));
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='3'");
        jdbc.update("INSERT INTO ip_pool_status (security_code,security_short_name,security_type,target_pool_id,audit_status,is_deleted)"
                + " VALUES ('C1','主体一','company',99,'20',0), ('B1','普通债','mtn',99,'20',0), ('C1','主体一','company',15,'20',0)");
        jdbc.update("INSERT INTO ip_pool_relation VALUES (23,'in_mutex',99,0), (99,'out_restrict',15,0)");
        ScheduledTaskResult failed = service.execute();
        assertThat(failed.isSuccess()).isFalse();
        assertThat(failed.getMessage()).contains("C1", "池99", "调出限制池");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id=99", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE target_pool_id=23", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log", Integer.class)).isZero();
    }

    /** 债券命中调出互斥池时，先前已调出的主体状态及日志也整体回滚。 */
    @Test
    public void shouldRollbackCompanyOutWhenBondHitsOutboundMutex() {
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='3'");
        jdbc.update("INSERT INTO ip_pool_relation VALUES (23,'in_linked',17,0), (23,'out_linked',17,0)");
        assertThat(service.execute().getAffectedCount()).isEqualTo(2);
        jdbc.update("INSERT INTO ip_pool_status (security_code,security_short_name,security_type,target_pool_id,audit_status,is_deleted)"
                + " VALUES ('C1','主体一','company',98,'20',0)");
        jdbc.update("INSERT INTO ip_pool_relation VALUES (23,'out_mutex',98,0)");
        jdbc.update("UPDATE ais_inv_analysis.company_observer_view SET observer_type='1'");
        ScheduledTaskResult failed = service.execute();
        assertThat(failed.isSuccess()).isFalse();
        assertThat(failed.getMessage()).contains("C1", "调出互斥池");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND target_pool_id IN (23,17)", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log", Integer.class)).isEqualTo(2);
    }

    /** 实际演示数据可写入仅有四个来源字段的观察名单，并通过实际 Mapper 查询。 */
    @Test
    public void shouldLoadObserverDemoWithoutSyntheticColumns() throws Exception {
        String sql = new String(Files.readAllBytes(Paths.get("sql", "ais_inv_analysis_demo_data.sql")), StandardCharsets.UTF_8);
        int start = sql.indexOf("INSERT INTO `company_observer_view`");
        assertThat(start).isGreaterThanOrEqualTo(0);
        int end = sql.indexOf(";", start);
        assertThat(end).isGreaterThan(start);
        jdbc.execute("DELETE FROM ais_inv_analysis.company_observer_view");
        jdbc.execute(sql.substring(start, end).replace("INSERT INTO `company_observer_view`",
                "INSERT INTO ais_inv_analysis.`company_observer_view`"));
        assertThat(sourceMapper.queryCompanyObserverList()).extracting(CompanyObserverDto::getCompanyId)
                .containsExactly(1L);
        assertThat(sourceMapper.queryCompanyObserverList()).extracting(CompanyObserverDto::getObserverType)
                .containsExactly("1");
    }

    /** 查询全量观察名单，主体信息未匹配时任务失败。 */
    @Test
    public void shouldReadEveryIsHoValueAndRetainInvalidMasterMapping() {
        jdbc.update("INSERT INTO ais_inv_analysis.company_observer_view (company_id,company_code,observer_type,isHo)"
                + " VALUES (2,'C2','3','1')");
        assertThat(sourceMapper.queryCompanyObserverList()).extracting(CompanyObserverDto::getCompanyCode).containsExactly("C1");
        assertThat(service.execute().getAffectedCount()).isEqualTo(1);
    }

    /** 扩大主体和债券数量并启用联动后，数据库仍仅执行初始化及批量预读 SELECT。 */
    @Test
    public void shouldKeepJdbcSelectCountStableWithMoreCompaniesAndLinkedPools() {
        List<Object[]> companies = new ArrayList<>();
        List<Object[]> observers = new ArrayList<>();
        List<Object[]> bonds = new ArrayList<>();
        for (int index = 2; index <= 41; index++) {
            String companyCode = "C" + index;
            companies.add(new Object[]{index, companyCode, "主体" + index});
            observers.add(new Object[]{index, companyCode});
            bonds.add(new Object[]{"LOAD_B" + index, companyCode});
        }
        jdbc.batchUpdate("INSERT INTO ais_inv_analysis.t_inv_company VALUES (?,?,?)", companies);
        jdbc.batchUpdate("INSERT INTO ais_inv_analysis.company_observer_view (company_id,company_code,observer_type,isHo)"
                + " VALUES (?,?,'3','0')", observers);
        jdbc.batchUpdate("INSERT INTO rrs_securityinfo VALUES (?,'批量债券','mtn',?,'发行主体','L',NULL,0)", bonds);
        jdbc.update("INSERT INTO ip_pool_relation VALUES (23,'in_linked',17,0), (23,'out_linked',17,0)");
        selectSql.clear();
        ScheduledTaskResult result = service.execute();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(81);
        // 任务和投资池配置由桩提供，名单、当前主体、关系、Excel 保护及状态批次各查一次
        assertThat(selectSql).hasSize(5);
        assertThat(selectSql).filteredOn(query -> query.contains("FROM ip_adjust_log src")).hasSize(1);
        assertThat(result.getDetailLog()).contains("主体池状态查询1次");
        selectSql.clear();
        assertThat(service.execute().getAffectedCount()).isZero();
        assertThat(selectSql).hasSize(5);
    }

    /** 使用真实候选查询及 Java 决定撤销已失去所有主体依据的跟随状态 */
    @Test
    public void removesOnlyFollowedStatusAfterLastSourceLeaves() {
        jdbc.update("INSERT INTO ip_pool_status (id,security_code,security_type,target_pool_id,audit_status,is_deleted,"
                + "adjust_mode,adjust_type,adjust_batch_no,adjust_reason) VALUES"
                + " (100,'B1','mtn',16,'20',0,'调入','自动调整','BOND1','【任务名称：补债】【主体跟随】（依据池ID：23）'),"
                + " (101,'B1','mtn',16,'20',0,'调入','手工调整','BOND2','人工入池'),"
                + " (102,'CRMW1','crmw',16,'20',0,'调入','联动调整','COMP3','【任务名称：观察】（同池旗下债）'),"
                + " (103,'ABS1','abs',16,'20',0,'调入','自动调整','BOND4','【任务名称：补债】【主体跟随】（依据池ID：23）'),"
                + " (104,'TODAY','mtn',16,'20',0,'调入','自动调整','BOND5','来源不清'),"
                + " (105,'C1','company',23,'20',0,'调入','手工调整','COMP6',NULL)");
        LinkedHashSet<Long> companyPools = new LinkedHashSet<>(Arrays.asList(16L, 23L));

        assertThat(followedBondsForAutoOut(16L, companyPools)).isEmpty();
        jdbc.update("UPDATE ip_pool_status SET is_deleted=1 WHERE id=105");
        assertThat(followedBondsForAutoOut(16L, companyPools))
                .extracting(ScheduledAdjustCandidateDto::getPoolStatusId).containsExactly(100L, 102L);
        assertThat(autoAdjustMapper.deleteCompanyFollowedBondStatus(100L)).isEqualTo(1);
        assertThat(autoAdjustMapper.deleteCompanyFollowedBondStatus(102L)).isEqualTo(1);
        assertThat(followedBondsForAutoOut(16L, companyPools)).isEmpty();
        assertThat(jdbc.queryForList("SELECT id FROM ip_pool_status WHERE is_deleted=0 ORDER BY id", Long.class))
                .containsExactly(101L, 103L, 104L);
    }

    /** Excel 主体调入及同批联动触发补债，生效调出优先于普通主体状态 */
    @Test
    public void appliesLatestExcelCompanyDecisionToBothFollowPools() {
        jdbc.execute("CREATE TABLE rrs_temp_security_code (temp_security_code VARCHAR(32), security_code VARCHAR(32),"
                + " is_deleted INT, status VARCHAR(32))");
        List<ScheduledSecurity> masters = batchSecurityCheckMapper.queryScheduledSecurities(Arrays.asList("B1", "OLD", "ABS1"));
        assertThat(masters).filteredOn(ScheduledSecurity::isEligibleForFollow).extracting(ScheduledSecurity::getWindCode)
                .containsExactlyInAnyOrder("B1", "OLD");
        jdbc.update("INSERT INTO ip_pool_status (security_code,security_type,target_pool_id,audit_status,is_deleted,adjust_mode) VALUES"
                + " ('C1','company',23,'20',0,'调入'), ('C1','company',17,'20',0,'调入')");
        assertThat(bondsForAutoIn(23L, 23L, 0)).extracting(ScheduledAdjustCandidateDto::getSecurityCode)
                .containsExactly("B1", "CRMW1", "TODAY");
        assertThat(bondsForAutoIn(23L, 23L, 1)).extracting(ScheduledAdjustCandidateDto::getSecurityCode)
                .containsExactly("B1", "CRMW1");
        jdbc.update("INSERT INTO ip_adjust_log (id,security_code,security_type,target_pool_id,audit_status,is_deleted,"
                + "adjust_mode,adjust_type,adjust_batch_no) VALUES"
                + " (110,'C1','company',23,'20',0,'调入','Excel导入','COMP_IN'),"
                + " (111,'C1','company',17,'20',0,'调入','联动调整','COMP_IN'),"
                + " (112,'C1','company',23,'00',0,'调出','Excel导入','COMP_PENDING')");
        for (long poolId : new long[]{23L, 17L}) {
            List<ScheduledAdjustCandidateDto> candidates = bondsForAutoIn(poolId, poolId, 30);
            assertThat(candidates).extracting(ScheduledAdjustCandidateDto::getSecurityCode)
                    .containsExactly("B1", "CRMW1", "OLD", "TODAY");
            assertThat(candidates).allMatch(ScheduledAdjustCandidateDto::isExcelCompanyAdjustment);
        }
        jdbc.update("INSERT INTO ip_pool_status (id,security_code,security_type,target_pool_id,audit_status,is_deleted,"
                + "adjust_mode,adjust_type,adjust_batch_no,adjust_reason) VALUES"
                + " (120,'B1','mtn',23,'20',0,'调入','自动调整','BOND1','【任务名称：补债】【主体跟随】（【Excel主体跟随】）'),"
                + " (121,'B1','mtn',17,'20',0,'调入','自动调整','BOND2','【任务名称：补债】【主体跟随】（【Excel主体跟随】）'),"
                + " (122,'B1','mtn',17,'20',0,'调入','手工调整','BOND3','人工入池')");
        LinkedHashSet<Long> companyPools = new LinkedHashSet<>(Arrays.asList(15L, 17L, 23L));
        assertThat(followedBondsForAutoOut(17L, companyPools)).isEmpty();
        assertThat(securityPoolAdjustMapper.queryExcelProtectedPoolIds("B1")).containsExactlyInAnyOrder(17L, 23L);
        assertThat(exclusions.loadProtectedStatusIds(null, false)).isEmpty();
        assertThat(exclusions.loadProtectedStatusIds(Collections.singletonList(17L), true))
                .containsExactly(121L);
        assertThat(securityPoolAdjustMapper.deleteUnprotectedPoolStatusSoft("B1", 23L)).isZero();
        jdbc.update("INSERT INTO ip_adjust_log (id,security_code,security_type,target_pool_id,audit_status,is_deleted,"
                + "adjust_mode,adjust_type,adjust_batch_no) VALUES"
                + " (113,'C1','company',23,'20',0,'调出','Excel导入','COMP_OUT'),"
                + " (114,'C1','company',17,'20',0,'调出','联动调整','COMP_OUT')");
        jdbc.update("INSERT INTO ip_pool_status (security_code,security_type,target_pool_id,audit_status,is_deleted,adjust_mode) VALUES"
                + " ('C1','company',15,'20',0,'调入')");
        assertThat(exclusions.loadCompanyDecisions(null, Collections.singletonList("C1"))
                .outPools("C1")).containsExactlyInAnyOrder(17L, 23L);
        assertThat(bondsForAutoIn(15L, 15L, 30))
                .extracting(ScheduledAdjustCandidateDto::getSecurityCode).containsExactly("B1", "CRMW1");
        for (long poolId : new long[]{23L, 17L}) {
            assertThat(bondsForAutoIn(poolId, poolId, 0)).isEmpty();
            assertThat(bondsForAutoIn(15L, poolId, 0)).isEmpty();
            List<ScheduledAdjustCandidateDto> candidates = followedBondsForAutoOut(poolId, companyPools);
            assertThat(candidates).hasSize(1).allMatch(ScheduledAdjustCandidateDto::isExcelCompanyAdjustment);
            assertThat(autoAdjustMapper.deleteCompanyFollowedBondStatus(candidates.get(0).getPoolStatusId())).isEqualTo(1);
        }
        assertThat(jdbc.queryForList("SELECT id FROM ip_pool_status WHERE security_code='B1' AND is_deleted=0", Long.class))
                .containsExactly(122L);
        jdbc.update("INSERT INTO ip_adjust_log (id,security_code,security_type,target_pool_id,audit_status,is_deleted,"
                + "adjust_mode,adjust_type,adjust_batch_no,audit_time,crte_time) VALUES"
                + " (115,'C1','company',23,'20',0,'调入','Excel导入','COMP_RESUME',NULL,NOW()),"
                + " (116,'C1','company',17,'20',0,'调入','联动调整','COMP_RESUME',NULL,NOW()),"
                + " (117,'C1','company',23,'20',0,'调出','Excel导入','COMP_OLDER','2000-01-01 00:00:00',NOW())");
        assertThat(exclusions.loadCompanyDecisions(null, Collections.singletonList("C1"))
                .outPools("C1")).isEmpty();
        assertThat(bondsForAutoIn(17L, 17L, 30))
                .extracting(ScheduledAdjustCandidateDto::getSecurityCode).containsExactly("CRMW1", "OLD", "TODAY");
    }

    private List<ScheduledAdjustCandidateDto> bondsForAutoIn(Long from, Long target, int days) {
        String cutoff = autoAdjustMapper.queryDatabaseDate().plusDays(days).format(DateTimeFormatter.BASIC_ISO_DATE);
        return exclusions.loadCompanyDecisions(Arrays.asList(from, target), null).filterInbound(
                autoAdjustMapper.queryCompanyNewBondForAutoIn(from, target, CompanyBondSyncPolicy.newBondInboundScope()),
                from, target, cutoff);
    }

    private List<ScheduledAdjustCandidateDto> followedBondsForAutoOut(Long poolId, Set<Long> sources) {
        Map<String, Set<Long>> activePools = autoAdjustMapper.queryActiveCompanyPoolRows(sources).stream()
                .collect(Collectors.groupingBy(PoolSecurityRowDto::getSecurityCode,
                        Collectors.mapping(PoolSecurityRowDto::getPoolId, Collectors.toSet())));
        List<ScheduledAdjustCandidateDto> candidates = ExcelPoolExclusionService.excludeProtectedRows(
                autoAdjustMapper.queryCompanyFollowedBondsForAutoOut(poolId),
                exclusions.loadProtectedStatusIds(Collections.singletonList(poolId), false));
        return exclusions.loadCompanyDecisions(sources, null).filterOutbound(candidates, poolId, sources, activePools);
    }

    /** 在 JDBC 准备语句时计数，避免只统计 Mapper 调用而误把缓存命中当作数据库查询。 */
    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    private static class SelectCounter implements Interceptor {
        /** 本测试实际执行的查询列表 */
        private final List<String> queries;

        /** 绑定当前测试的查询记录。 */
        private SelectCounter(List<String> queries) {
            this.queries = queries;
        }

        /** 记录实际准备的 SELECT，写操作保持原样执行。 */
        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            String sql = ((StatementHandler) invocation.getTarget()).getBoundSql().getSql().trim();
            if (sql.startsWith("SELECT") || sql.startsWith("WITH")) {
                queries.add(sql);
            }
            return invocation.proceed();
        }
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
