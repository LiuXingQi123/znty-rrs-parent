package com.znty.rrs.service;

import com.znty.rrs.entity.bo.CreditBondInnerRatingGradeBo;
import com.znty.rrs.entity.bo.IpCrmwRuleAlertBo;
import com.znty.rrs.entity.crmwrulealert.CrmwRuleAlertReq;
import com.znty.rrs.entity.schedule.CrmwRuleAlertScanDto;
import com.znty.rrs.mapper.CreditBondGradeRuleMapper;
import com.znty.rrs.mapper.CrmwRuleAlertMapper;
import com.znty.rrs.mapper.CrmwRuleAlertTaskMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
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
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 真实 XML、独立提醒表、审计以及扫描事务回归测试 */
public class CrmwRuleAlertMapperTest {

    /** 隔离H2数据库 */
    private EmbeddedDatabase database;
    /** 测试数据操作 */
    private JdbcTemplate jdbc;
    /** 使用真实XML的数据访问 */
    private CrmwRuleAlertMapper mapper;
    /** 独立任务扫描和待办维护Mapper */
    private CrmwRuleAlertTaskMapper taskMapper;
    /** 独立任务事务代理 */
    private CrmwRuleAlertTaskService taskService;
    /** 具有真实事务代理的页面服务 */
    private CrmwRuleAlertService service;

    /** 读取实际建表列定义，准备两张独立提醒表及扫描依赖 */
    @Before
    public void setUp() throws Exception {
        String version = System.getProperty("java.specification.version");
        int major = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 需Java11以上运行时", major >= 11);
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        jdbc = new JdbcTemplate(database);
        String ddl = new String(Files.readAllBytes(Paths.get("sql/rrs_crmw_rule_alert_schema.sql")), StandardCharsets.UTF_8);
        Matcher tables = Pattern.compile("CREATE TABLE `ip_crmw_rule_alert(?:_evt)?` \\(.*?\\) ENGINE[^;]*;", Pattern.DOTALL).matcher(ddl);
        while (tables.find()) {
            jdbc.execute(tables.group().replaceAll("\\) ENGINE[^;]*;", ");").replace("TINYINT(1)", "TINYINT"));
        }
        jdbc.execute("CREATE TABLE ip_investment_pool(id BIGINT, pool_name VARCHAR(100), pool_type VARCHAR(30), is_deleted INT)");
        jdbc.execute("CREATE TABLE ip_pool_status_crmw(crmw_scode VARCHAR(64), crmw_name VARCHAR(100), security_code VARCHAR(64),"
                + "security_short_name VARCHAR(100), target_pool_id BIGINT, adjust_log_id BIGINT, audit_status VARCHAR(2), is_deleted INT)");
        jdbc.execute("CREATE TABLE ip_pool_status(security_code VARCHAR(64), security_type VARCHAR(30), target_pool_id BIGINT, audit_status VARCHAR(2), is_deleted INT)");
        jdbc.execute("CREATE TABLE rrs_securityinfo(wind_code VARCHAR(64), security_type VARCHAR(30), issuer_code VARCHAR(64), issuer VARCHAR(100))");
        jdbc.execute("CREATE SCHEMA ais_inv_analysis");
        jdbc.execute("CREATE TABLE ais_inv_analysis.v_inv_grade_result(windcode VARCHAR(64), total_score VARCHAR(16), ts TIMESTAMP)");
        jdbc.execute("INSERT INTO ip_investment_pool VALUES (19,'CRMW库','crmw',0),(20,'CRMW库2','crmw',0),(16,'观察池','observe',0),(23,'重点观察','restricted',0),(2,'信用债库','credit_bond',0)");
        jdbc.execute("INSERT INTO rrs_securityinfo VALUES ('C1','crmw','CREATOR','创设方'),('C2','crmw','OTHER','其他创设方'),('B1','bond','BOND_ISSUER','标的债发行人')");
        jdbc.execute("INSERT INTO ais_inv_analysis.v_inv_grade_result VALUES ('CREATOR','1',TIMESTAMP '2026-01-01 00:00:00'),"
                + "('CREATOR','4',TIMESTAMP '2026-11-01 00:00:00'),('OTHER','1',TIMESTAMP '2026-11-01 00:00:00'),('BOND_ISSUER','1',TIMESTAMP '2026-11-01 00:00:00')");
        jdbc.execute("INSERT INTO ip_pool_status_crmw VALUES ('C1','凭证1','B1','标的债',19,100,'20',0)");
        Configuration config = new Configuration(new Environment("test", new SpringManagedTransactionFactory(), database));
        config.setMapUnderscoreToCamelCase(true);
        for (String resource : new String[]{"mapper/CrmwRuleAlertMapper.xml", "mapper/CrmwRuleAlertTaskMapper.xml"}) {
            try (InputStream input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
            }
        }
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(config);
        SqlSessionTemplate session = new SqlSessionTemplate(factory);
        mapper = session.getMapper(CrmwRuleAlertMapper.class);
        taskMapper = session.getMapper(CrmwRuleAlertTaskMapper.class);
        CreditBondGradeRuleMapper grades = mock(CreditBondGradeRuleMapper.class);
        CreditBondInnerRatingGradeBo three = new CreditBondInnerRatingGradeBo();
        three.setGradeCode("3"); three.setSortNo(6);
        CreditBondInnerRatingGradeBo four = new CreditBondInnerRatingGradeBo();
        four.setGradeCode("4"); four.setSortNo(8);
        CreditBondInnerRatingGradeBo one = new CreditBondInnerRatingGradeBo();
        one.setGradeCode("1"); one.setSortNo(1);
        when(grades.queryEnabledRatingGradeList()).thenReturn(Arrays.asList(one, three, four));
        CrmwRuleAlertService target = new CrmwRuleAlertService();
        ReflectionTestUtils.setField(target, "crmwRuleAlertMapper", mapper);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(database), new AnnotationTransactionAttributeSource()));
        service = (CrmwRuleAlertService) proxy.getProxy();
        CrmwRuleAlertTaskService taskTarget = new CrmwRuleAlertTaskService();
        ReflectionTestUtils.setField(taskTarget, "crmwRuleAlertTaskMapper", taskMapper);
        ReflectionTestUtils.setField(taskTarget, "creditBondGradeRuleMapper", grades);
        ProxyFactory taskProxy = new ProxyFactory(taskTarget);
        taskProxy.setProxyTargetClass(true);
        taskProxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(database), new AnnotationTransactionAttributeSource()));
        taskService = (CrmwRuleAlertTaskService) taskProxy.getProxy();
    }

    /** 凭证主体最新评级优先，标的发行人高评级不能掩盖创设方风险，名单按主体判断 */
    @Test
    public void shouldReadCreatorLatestRatingAndIndependentWatchLists() {
        jdbc.execute("INSERT INTO ip_pool_status VALUES ('CREATOR','company',23,'20',0),('BOND_ISSUER','company',16,'20',0)");
        CrmwRuleAlertScanDto security = taskMapper.queryCrmwSecurityList(Arrays.asList("C1")).get(0);
        assertThat(security.getIssuerCode()).isEqualTo("CREATOR");
        assertThat(taskMapper.queryLatestIssuerRatingList(Arrays.asList("CREATOR")).get(0).getInnerIssuerRating()).isEqualTo("4");
        CrmwRuleAlertScanDto watch = taskMapper.queryIssuerWatchList(Arrays.asList("CREATOR")).get(0);
        assertThat(watch.isInRestrictedPool()).isTrue();
        assertThat(watch.isInObservePool()).isFalse();
        assertThat(taskService.execute().isSuccess()).isTrue();
        CrmwRuleAlertReq query = new CrmwRuleAlertReq();
        assertThat(mapper.queryAlertPage(query).get(0).getFailReason()).contains("低于3档", "重点观察").doesNotContain("已在观察名单");
        jdbc.execute("INSERT INTO ais_inv_analysis.v_inv_grade_result VALUES ('CREATOR',NULL,TIMESTAMP '2026-11-09 00:00:00')");
        assertThat(taskMapper.queryLatestIssuerRatingList(Arrays.asList("CREATOR")).get(0).getInnerIssuerRating()).isNull();
    }

    /** 同标的不同凭证及同凭证不同池各自扫描，审批中、删除及普通池记录不参与 */
    @Test
    public void shouldKeepCombinationAndPoolIdentity() {
        jdbc.execute("INSERT INTO ip_pool_status_crmw VALUES ('C1','凭证1','B1','标的债',20,101,'20',0),"
                + "('C2','凭证2','B1','标的债',19,102,'20',0),('C1','审批中','B1','债',19,103,'00',0),"
                + "('C1','已删除','B1','债',19,104,'20',1),('C1','普通池','B1','债',2,105,'20',0)");
        List<CrmwRuleAlertScanDto> rows = taskMapper.queryCrmwInPoolList();
        assertThat(rows).extracting(CrmwRuleAlertScanDto::getCrmwScode, CrmwRuleAlertScanDto::getCurrentPoolId,
                CrmwRuleAlertScanDto::getAdjustLogId).containsExactlyInAnyOrder(tuple("C1",19L,100L),tuple("C1",20L,101L),tuple("C2",19L,102L));
        assertThat(taskService.execute().isSuccess()).isTrue();
        assertThat(taskMapper.queryOpenAlertCount()).isEqualTo(2);
    }

    /** 新增、刷新、人工处理和同依据复用均落独立表，旧待办失效有审计 */
    @Test
    public void shouldPersistLifecycleAndAuditWithoutChangingPools() {
        assertThat(taskService.execute().isSuccess()).isTrue();
        assertThat(taskService.execute().isSuccess()).isTrue();
        CrmwRuleAlertReq query = new CrmwRuleAlertReq();
        query.setCrmwScode("C1"); query.setSecurityCode("B1"); query.setAlertStatus("00");
        IpCrmwRuleAlertBo row = mapper.queryAlertPage(query).get(0);
        assertThat(taskMapper.queryOpenAlertCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_crmw_rule_alert_evt", Integer.class)).isEqualTo(2);
        query.setId(row.getId()); query.setCurrentUserId("2"); query.setCurrentUserName("研究员");
        service.editAlertProcessed(query);
        assertThat(taskService.execute().isSuccess()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_crmw_rule_alert", Integer.class)).isEqualTo(1);
        assertThat(taskMapper.queryOpenAlertCount()).isZero();
        // 改变来源日志是新一轮入库，原处理结论不能压住新提醒
        jdbc.execute("UPDATE ip_pool_status_crmw SET adjust_log_id=200");
        assertThat(taskService.execute().isSuccess()).isTrue();
        assertThat(taskMapper.queryOpenAlertCount()).isEqualTo(1);
        jdbc.execute("UPDATE ip_crmw_rule_alert SET last_scan_time=TIMESTAMP '2020-01-01 00:00:00' WHERE alert_status='00'");
        jdbc.execute("INSERT INTO ais_inv_analysis.v_inv_grade_result VALUES ('CREATOR','3',TIMESTAMP '2026-11-10 00:00:00')");
        assertThat(taskService.execute().isSuccess()).isTrue();
        assertThat(taskMapper.queryOpenAlertCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_crmw_rule_alert WHERE alert_status='99'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status_crmw WHERE audit_status='20' AND is_deleted=0", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_crmw_rule_alert_evt WHERE alert_status='99'", Integer.class)).isEqualTo(1);
    }

    /** 第二条组合数据错误时，第一条已写提醒及审计全部回滚，旧待办保持原样 */
    @Test
    public void shouldRollbackWholeScanOnMissingCreatorRating() {
        jdbc.execute("DELETE FROM ais_inv_analysis.v_inv_grade_result WHERE windcode='OTHER'");
        jdbc.execute("INSERT INTO ip_pool_status_crmw VALUES ('C2','凭证2','B1','债',19,102,'20',0)");
        IpCrmwRuleAlertBo old = new IpCrmwRuleAlertBo();
        old.setCrmwScode("OLD"); old.setSecurityCode("OLD"); old.setCurrentPoolId(19L);
        old.setAlertStatus("00"); old.setIsDeleted(0); old.setLastScanTime(new Date(0));
        taskMapper.addAlert(old);
        ScheduledTaskResult result = taskService.execute();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("OTHER");
        assertThat(result.getDetailLog()).contains("任务开始", "扫描组合 2 条", "整轮回滚");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_crmw_rule_alert", Integer.class)).isEqualTo(1);
        assertThat(mapper.queryAlertById(old.getId()).getAlertStatus()).isEqualTo("00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_crmw_rule_alert_evt", Integer.class)).isZero();
    }


    /** 批量最近提醒仍按最大有效主键取值，已删除的更大主键不覆盖人工结论 */
    @Test
    public void shouldReadLatestAlertPerCombinationWithoutOrdering() {
        // 准备同组合的待处理、已处理和已删除历史
        addAlertHistory("C1", "B1", 19L, "00", 0);
        // 新增较晚的有效人工结论
        addAlertHistory("C1", "B1", 19L, "20", 0);
        // 删除记录不能成为最近有效提醒
        addAlertHistory("C1", "B1", 19L, "00", 1);
        // 另一个池应独立匹配最近记录
        addAlertHistory("C1", "B1", 20L, "99", 0);
        // 查询范围外的凭证不得混入结果
        addAlertHistory("C2", "B1", 19L, "00", 0);
        List<IpCrmwRuleAlertBo> latest = taskMapper.queryLatestAlertList(Arrays.asList("C1"));
        assertThat(latest).extracting(IpCrmwRuleAlertBo::getCrmwScode, IpCrmwRuleAlertBo::getCurrentPoolId,
                IpCrmwRuleAlertBo::getAlertStatus).containsExactlyInAnyOrder(tuple("C1", 19L, "20"), tuple("C1", 20L, "99"));
    }

    /** 新增指定组合的提醒历史，供最近记录口径验证 */
    private void addAlertHistory(String crmwCode, String bondCode, Long poolId, String status, int deleted) {
        IpCrmwRuleAlertBo row = new IpCrmwRuleAlertBo();
        row.setCrmwScode(crmwCode);
        row.setSecurityCode(bondCode);
        row.setCurrentPoolId(poolId);
        row.setAlertStatus(status);
        row.setIsDeleted(deleted);
        taskMapper.addAlert(row);
    }

    /** 关闭隔离测试数据库 */
    @After
    public void tearDown() {
        if (database != null) database.shutdown();
    }
}
