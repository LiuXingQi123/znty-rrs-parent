package com.znty.rrs.service;

import com.znty.rrs.entity.schedule.CrmwForbiddenOutCandidateDto;
import com.znty.rrs.mapper.CrmwForbiddenAutoOutMapper;
import com.znty.rrs.mapper.CrmwPoolAdjustMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** 真实MyBatis SQL、原有状态表和日志表、四端隔离及单组合事务测试 */
public class CrmwForbiddenAutoOutMapperTest {

    /** 隔离测试数据库 */
    private EmbeddedDatabase database;
    /** 初始化及结果核查 */
    private JdbcTemplate jdbc;
    /** 真实扫描和更新Mapper */
    private CrmwForbiddenAutoOutMapper mapper;
    /** 注入真实Mapper和事务管理器的任务 */
    private CrmwForbiddenAutoOutService service;
    /** 实际注入任务代理的Spring容器，不允许循环依赖 */
    private AnnotationConfigApplicationContext context;

    /** 从实际DDL创建日志和组合状态表，加载真实XML及事务管理器 */
    @Before
    public void setUp() throws Exception {
        String version = System.getProperty("java.specification.version");
        int major = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232需Java11以上运行时", major >= 11);
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        jdbc = new JdbcTemplate(database);
        // 1. 使用实际业务列定义，避免测试自己发明日志或组合字段
        createTable("rrs_security_pool_adjust_schema.sql", "ip_adjust_log");
        createTable("rrs_crmw_pool_status_schema.sql", "ip_pool_status_crmw");
        jdbc.execute("CREATE TABLE ip_investment_pool(id BIGINT PRIMARY KEY, pool_name VARCHAR(100), pool_type VARCHAR(30), is_deleted INT)");
        jdbc.execute("CREATE TABLE ip_pool_status(id BIGINT AUTO_INCREMENT PRIMARY KEY, security_code VARCHAR(64), target_pool_id BIGINT, audit_status VARCHAR(2), is_deleted INT)");
        jdbc.execute("CREATE TABLE rrs_securityinfo(wind_code VARCHAR(64), security_type VARCHAR(30), issuer_code VARCHAR(64), issuer VARCHAR(100))");
        jdbc.execute("INSERT INTO ip_investment_pool VALUES (18,'CRMW库','crmw',0),(19,'CRMW库2','crmw',0),"
                + "(15,'禁止库','forbidden',0),(2,'信用债库','credit_bond',0),(16,'观察池','observe',0),"
                + "(17,'黑名单质押库','blacklist',0),(23,'重点观察','restricted',0),(40,'已删除禁止库','forbidden',1),"
                + "(41,'已删除CRMW库','crmw',1)");
        jdbc.execute("INSERT INTO rrs_securityinfo VALUES ('C1','crmw','B','B机构'),('C2','crmw','OTHER_B','其他机构'),"
                + "('D1','bond','A','A公司'),('D2','bond','OTHER_A','其他公司')");
        // 2. 任务扫描和日志写入共同使用Spring管理的数据库连接
        Configuration config = new Configuration(new Environment("test", new SpringManagedTransactionFactory(), database));
        config.setMapUnderscoreToCamelCase(true);
        for (String resource : new String[]{"mapper/CrmwForbiddenAutoOutMapper.xml", "mapper/CrmwPoolAdjustMapper.xml"}) {
            try (InputStream input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
            }
        }
        SqlSessionTemplate session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
        mapper = session.getMapper(CrmwForbiddenAutoOutMapper.class);
        // 3. 使用实际容器注入ObjectProvider，验证不依赖循环引用或手工注入自身
        context = new AnnotationConfigApplicationContext();
        context.getDefaultListableBeanFactory().setAllowCircularReferences(false);
        context.getBeanFactory().registerSingleton("crmwForbiddenAutoOutMapper", mapper);
        context.getBeanFactory().registerSingleton("crmwPoolAdjustMapper", session.getMapper(CrmwPoolAdjustMapper.class));
        context.getBeanFactory().registerSingleton("transactionManager", new DataSourceTransactionManager(database));
        context.register(TransactionConfig.class, CrmwForbiddenAutoOutService.class);
        context.refresh();
        service = context.getBean(CrmwForbiddenAutoOutService.class);
        assertThat(AopUtils.isAopProxy(service)).isTrue();
    }

    /** 释放隔离数据库 */
    @After
    public void tearDown() {
        if (context != null) {
            context.close();
        }
        if (database != null) {
            database.shutdown();
        }
    }

    /** 一次查询全部未删除、已通过且当前位于有效CRMW池的组合，不依赖返回顺序 */
    @Test
    public void scanShouldReadAllValidCombinationsWithoutOrdering() {
        for (int i = 1; i <= 6; i++) {
            // 准备不同状态的原组合
            addCombination(i, "C1", "D1", i == 4 ? 19 : i == 5 ? 41 : i == 6 ? 2 : 18);
        }
        jdbc.update("UPDATE ip_pool_status_crmw SET is_deleted=1 WHERE id=2");
        jdbc.update("UPDATE ip_pool_status_crmw SET audit_status='00' WHERE id=3");

        assertThat(mapper.queryCrmwInPoolList()).extracting(CrmwForbiddenOutCandidateDto::getPoolStatusId)
                .containsExactlyInAnyOrder(1L, 4L);
    }

    /** 只认有效禁止库，排除未通过、已删除及观察、重点观察和黑名单质押库 */
    @Test
    public void forbiddenQueryShouldExcludeOtherRiskPoolsAndInactiveRows() {
        jdbc.execute("INSERT INTO ip_pool_status(security_code,target_pool_id,audit_status,is_deleted) VALUES "
                + "('D1',15,'20',0),('D1',15,'20',0),('A',17,'20',0),('C1',16,'20',0),('B',23,'20',0),"
                + "('A',15,'00',0),('C1',15,'20',1),('B',40,'20',0)");

        assertThat(mapper.queryForbiddenCodeList(Arrays.asList("D1", "A", "C1", "B"))).containsExactly("D1");
        assertThat(mapper.queryForbiddenCodeList(Arrays.asList("OUTSIDE"))).isEmpty();
    }

    /** 四类对象逐一验证，一对多绑定只清退关联组合，重复执行不再写日志 */
    @Test
    public void fourEndpointsShouldExitOnlyRelatedCombinationsAndRemainIdempotent() {
        for (String code : Arrays.asList("D1", "A", "C1", "B")) {
            jdbc.update("DELETE FROM ip_pool_status_crmw");
            jdbc.update("DELETE FROM ip_pool_status");
            jdbc.update("DELETE FROM ip_adjust_log");
            // 1. 两张凭证与两只债券交叉绑定，验证另一端不会被连带禁投
            addCombination(1, "C1", "D1", 18);
            addCombination(2, "C1", "D2", 18);
            addCombination(3, "C2", "D1", 18);
            addCombination(4, "C2", "D2", 18);
            jdbc.update("INSERT INTO ip_pool_status(security_code,target_pool_id,audit_status,is_deleted) VALUES (?,15,'20',0)", code);

            ScheduledTaskResult first = service.execute();
            ScheduledTaskResult second = service.execute();

            assertThat(first.isSuccess()).isTrue();
            assertThat(first.getAffectedCount()).isEqualTo(2);
            assertThat(second.isSuccess()).isTrue();
            assertThat(second.getAffectedCount()).isZero();
            List<Long> exited = jdbc.queryForList("SELECT id FROM ip_pool_status_crmw WHERE is_deleted=1", Long.class);
            assertThat(exited).containsExactlyInAnyOrderElementsOf(
                    Arrays.asList("D1", "A").contains(code) ? Arrays.asList(1L, 3L) : Arrays.asList(1L, 2L));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log", Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status", Integer.class)).isEqualTo(1);
        }
    }

    /** 同一组合命中全部依据只生成一次系统自动出库，保留其他池和所有禁止库记录 */
    @Test
    public void mergedReasonsAndSystemLogShouldUseExistingBusinessTables() {
        // 同一组合分处两个CRMW池，两个实际在池记录都需要退出
        addCombination(1, "C1", "D1", 18);
        addCombination(2, "C1", "D1", 19);
        jdbc.execute("INSERT INTO ip_pool_status(security_code,target_pool_id,audit_status,is_deleted) VALUES "
                + "('D1',15,'20',0),('A',15,'20',0),('C1',15,'20',0),('B',15,'20',0)");

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT adjust_reason FROM ip_adjust_log", String.class))
                .allSatisfy(reason -> assertThat(reason).contains("标的债券[D1]", "标的发行主体[A]", "CRMW凭证[C1]", "创设方／保护方[B]"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_mode='调出' AND adjust_type='自动调整' "
                + "AND audit_status='20' AND adjuster_id='0' AND adjuster_name='系统' AND pool_type='crmw' "
                + "AND adjust_advice IS NULL AND adjust_batch_no LIKE 'CRMW%'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status", Integer.class)).isEqualTo(4);
    }

    /** 仅退出原状态主键，原组合退出又重新入库时不误删新记录 */
    @Test
    public void oldCandidateShouldNotDeleteReenteredCombination() {
        // 1. 读取旧组合后模拟其他处理先退出并重新入库
        addCombination(1, "C1", "D1", 18);
        CrmwForbiddenOutCandidateDto old = mapper.queryCrmwInPoolList().get(0);
        jdbc.update("UPDATE ip_pool_status_crmw SET is_deleted=1 WHERE id=1");
        addCombination(2, "C1", "D1", 18);

        assertThat(mapper.editCrmwPoolStatusOut(old)).isZero();
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM ip_pool_status_crmw WHERE id=2", Integer.class)).isZero();
    }

    /** 中间日志失败只回滚该组合，前后正常组合仍出库并保留日志 */
    @Test
    public void failedLogShouldRollbackOnlyCurrentCombinationAndContinue() {
        // 1. 通过数据库约束让D2失败，D1和D3无论返回顺序如何都必须成功
        addCombination(1, "C1", "D1", 18);
        addCombination(2, "C1", "D2", 18);
        jdbc.execute("INSERT INTO rrs_securityinfo VALUES ('D3','bond','A','A公司')");
        addCombination(3, "C1", "D3", 18);
        jdbc.execute("ALTER TABLE ip_adjust_log ADD CONSTRAINT test_reject_d2 CHECK (security_code <> 'D2')");
        jdbc.execute("INSERT INTO ip_pool_status(security_code,target_pool_id,audit_status,is_deleted) VALUES ('B',15,'20',0)");

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(2);
        assertThat(result.getMessage()).contains("失败 1 条");
        assertThat(result.getDetailLog()).contains("标的[D2]", "保留原组合并继续");
        assertThat(jdbc.queryForList("SELECT security_code FROM ip_pool_status_crmw WHERE is_deleted=0", String.class)).containsExactly("D2");
        assertThat(jdbc.queryForList("SELECT security_code FROM ip_adjust_log", String.class)).containsExactlyInAnyOrder("D1", "D3");
    }

    /** 超过原500条边界仍一次读取，逐组合独立提交全部出库 */
    @Test
    public void moreThanFormerBatchSizeShouldExitInOneRun() {
        for (int i = 1; i <= 501; i++) {
            String code = "RUN_D" + i;
            jdbc.update("INSERT INTO rrs_securityinfo VALUES (?,'bond','A','A公司')", code);
            // 每只债券与同一张凭证形成独立组合
            addCombination(i, "C1", code, 18);
        }
        jdbc.execute("INSERT INTO ip_pool_status(security_code,target_pool_id,audit_status,is_deleted) VALUES ('B',15,'20',0)");

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(501);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log", Integer.class)).isEqualTo(501);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status_crmw WHERE is_deleted=0", Integer.class)).isZero();
    }

    /** 主档缺失只记失败，不阻断同一轮其他组合出库 */
    @Test
    public void missingMasterShouldNotBlockValidCombinations() {
        // 1. 无主档组合夹在两个正常组合之间，验证共享查询不会提前中断任务
        addCombination(1, "C1", "D1", 18);
        addCombination(2, "C1", "MISSING", 18);
        addCombination(3, "C1", "D2", 18);
        jdbc.execute("INSERT INTO ip_pool_status(security_code,target_pool_id,audit_status,is_deleted) VALUES ('B',15,'20',0)");

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(2);
        assertThat(result.getMessage()).contains("失败 1 条");
        assertThat(result.getDetailLog()).contains("标的[MISSING]", "主档缺失");
        assertThat(jdbc.queryForList("SELECT security_code FROM ip_pool_status_crmw WHERE is_deleted=0", String.class)).containsExactly("MISSING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log", Integer.class)).isEqualTo(2);
    }

    /** 从实际脚本提取目标表的完整列定义，仅转换H2不支持的MySQL表选项 */
    private void createTable(String file, String table) throws Exception {
        String ddl = new String(Files.readAllBytes(Paths.get("sql", file)), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("CREATE TABLE `" + table + "`\\s*\\(.*?\\) ENGINE[^;]*;", Pattern.DOTALL).matcher(ddl);
        assertThat(matcher.find()).as(table + "应存在实际DDL").isTrue();
        jdbc.execute(matcher.group().replaceAll("\\) ENGINE[^;]*;", ");").replace("TINYINT(1)", "TINYINT"));
    }

    /** 插入一条实际已生效组合，不依赖凭证到期日和人工出库限制 */
    private void addCombination(long id, String crmwCode, String securityCode, long poolId) {
        jdbc.update("INSERT INTO ip_pool_status_crmw(id,crmw_scode,crmw_name,crmw_stype,security_code,security_short_name,"
                + "security_type,target_pool_id,pool_type,audit_status,is_deleted) VALUES (?,?,'凭证','crmw',?,'标的债','bond',?,'crmw','20',0)",
                id, crmwCode, securityCode, poolId);
    }

    /** 使用与Spring Boot默认一致的类代理启用注解事务 */
    @Component
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TransactionConfig {
    }
}
