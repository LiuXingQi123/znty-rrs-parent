package com.znty.rrs.service;

import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.mapper.AbsIdentityMapper;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.CommonMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.mapper.WindRatingMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.znty.rrs.entity.bo.IpAdjustLogBo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ABS 身份占用和候选五字段真实 SQL 测试 */
public class AbsIdentityMapperIntegrationTest {
    private JdbcTemplate jdbc;
    private SqlSessionFactory factory;

    @Before
    public void prepare() throws Exception {
        Assume.assumeTrue("H2 需要 Java 11 以上", !System.getProperty("java.specification.version").startsWith("1."));
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:abs_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE rrs_securityinfo (wind_code VARCHAR(32) PRIMARY KEY, short_name VARCHAR(100), security_status VARCHAR(8), issuer VARCHAR(100), issuer_code VARCHAR(32), rating_bondissuer VARCHAR(40), rating_bondissuer_agency VARCHAR(400), inner_issuer_rating VARCHAR(50), abs_flag INT, security_type VARCHAR(32), ts TIMESTAMP)");
        // 使用项目真实表结构，CRMW 申请与普通申请共用同一张日志表
        createBusinessTable("rrs_security_pool_adjust_schema.sql", "ip_adjust_log");
        createBusinessTable("rrs_security_pool_adjust_schema.sql", "ip_pool_status");
        createBusinessTable("rrs_crmw_pool_status_schema.sql", "ip_pool_status_crmw");
        createBusinessTable("rrs_adjust_snapshot_schema.sql", "ip_adjust_security_snapshot");
        createBusinessTable("rrs_adjust_snapshot_schema.sql", "ip_adjust_security_snapshot_crmw");
        jdbc.execute("CREATE TABLE dict_security_type (security_type VARCHAR(32), security_type_name VARCHAR(100), category_type VARCHAR(32), is_deleted INT)");
        jdbc.update("INSERT INTO dict_security_type VALUES ('abs','ABS','bond',0)");
        jdbc.execute("CREATE SCHEMA ais_inv_ods");
        jdbc.execute("CREATE SCHEMA ais_inv_analysis");
        jdbc.execute("CREATE TABLE ais_inv_ods.wind_cbondissuer (s_info_windcode VARCHAR(32), s_info_compcode VARCHAR(32), s_info_compname VARCHAR(100), s_info_typecode BIGINT, opdate TIMESTAMP)");
        jdbc.execute("CREATE TABLE ais_inv_ods.wind_cbondissuerrating (object_id VARCHAR(100), s_info_compcode VARCHAR(32), b_info_creditrating VARCHAR(40), b_info_creditratingagency VARCHAR(10), ann_dt VARCHAR(8), ann_dt2 VARCHAR(8), opdate TIMESTAMP, b_info_precreditrating VARCHAR(40), b_creditratingchange VARCHAR(10))");
        jdbc.execute("CREATE TABLE ais_inv_analysis.v_inv_grade_result (windcode VARCHAR(32), windname VARCHAR(100), total_score VARCHAR(50), ts TIMESTAMP)");
        jdbc.execute("CREATE TABLE ais_inv_analysis.t_inv_company (wind_code VARCHAR(32), full_name VARCHAR(100), short_names VARCHAR(100))");
        jdbc.execute("CREATE TABLE dict_external_rating_agency (b_info_creditratingagency VARCHAR(10), is_deleted INT)");
        jdbc.update("INSERT INTO rrs_securityinfo(wind_code, abs_flag, security_type) VALUES ('ABS1',1,'abs')");
        Configuration config = new Configuration(new Environment("abs-test", new JdbcTransactionFactory(), ds));
        config.setMapUnderscoreToCamelCase(true);
        for (String mapper : Arrays.asList("AbsIdentityMapper", "SecurityPoolAdjustMapper", "AutoAdjustMapper",
                "CommonMapper", "WindRatingMapper")) {
            try (InputStream in = getClass().getResourceAsStream("/mapper/" + mapper + ".xml")) {
                new XMLMapperBuilder(in, config, mapper, config.getSqlFragments()).parse();
            }
        }
        factory = new SqlSessionFactoryBuilder().build(config);
    }

    /** 直接执行生产建表脚本中的单张表定义，去掉 H2 不支持的 MySQL 引擎选项 */
    private void createBusinessTable(String schemaFile, String tableName) throws Exception {
        String schema = new String(Files.readAllBytes(Paths.get("sql", schemaFile)), StandardCharsets.UTF_8);
        Matcher ddl = Pattern.compile("(?s)CREATE TABLE `" + tableName + "`\\s*\\(.*?;").matcher(schema);
        assertThat(ddl.find()).as("生产脚本必须定义表 %s", tableName).isTrue();
        jdbc.execute(ddl.group().replaceAll("(?s)\\) ENGINE.*", ")"));
    }

    /** 主档为空时仍应通过申请快照占用身份，终结后才解除 */
    @Test
    public void pendingAndReturnedForEditShouldOccupyUntilTerminal() {
        jdbc.update("INSERT INTO ip_adjust_log(id,security_code,security_type,target_pool_id,audit_status,is_deleted) VALUES (1,'ABS1','abs',101,'00',0)");
        jdbc.update("INSERT INTO ip_adjust_security_snapshot(adjust_log_id,wind_code,issuer_code,is_deleted) VALUES(1,'ABS1','A',0)");
        try (SqlSession session = factory.openSession()) {
            assertThat(session.getMapper(AbsIdentityMapper.class).queryOccupiedIdentityCodes("ABS1")).containsExactly("A");
        }
        jdbc.update("UPDATE ip_adjust_log SET audit_status='11'");
        try (SqlSession session = factory.openSession()) {
            assertThat(session.getMapper(AbsIdentityMapper.class).queryOccupiedIdentityCodes("ABS1")).containsExactly("A");
        }
        for (String status : Arrays.asList("99", "21")) {
            jdbc.update("UPDATE ip_adjust_log SET audit_status=?", status);
            try (SqlSession session = factory.openSession()) {
                assertThat(session.getMapper(AbsIdentityMapper.class).queryOccupiedIdentityCodes("ABS1")).isEmpty();
            }
        }
    }

    /** 自动联动已入池但未确认主体时，不向唯一身份查询返回空主体编码 */
    @Test
    public void passiveInboundPoolStatusShouldNotOccupyIdentity() {
        jdbc.update("INSERT INTO ip_pool_status(id,security_code,security_type,target_pool_id,audit_status,is_deleted) VALUES(1,'ABS1','abs',101,'20',0)");
        try (SqlSession session = factory.openSession()) {
            assertThat(session.getMapper(AbsIdentityMapper.class).queryOccupiedIdentityCodes("ABS1")).isEmpty();
        }
    }

    /** 忽略自动入池时同时软删除在池状态和对应调库日志，人工记录不进入该条件 */
    @Test
    public void ignorePassiveInboundShouldDeletePoolStatusAndAdjustLog() {
        jdbc.update("INSERT INTO ip_adjust_log(id,security_code,security_type,adjust_type,adjust_mode,target_pool_id,audit_status,is_deleted) VALUES(1,'ABS1','abs','自动调整','调入',101,'20',0)");
        jdbc.update("INSERT INTO ip_pool_status(id,security_code,security_type,adjust_type,adjust_mode,adjust_log_id,target_pool_id,audit_status,is_deleted) VALUES(1,'ABS1','abs','自动调整','调入',1,101,'20',0)");
        try (SqlSession session = factory.openSession()) {
            AbsIdentityMapper mapper = session.getMapper(AbsIdentityMapper.class);
            assertThat(mapper.deletePassiveInboundLog("ABS1", 101L)).isEqualTo(1);
            assertThat(mapper.deletePassiveInboundStatus("ABS1", 101L)).isEqualTo(1);
            session.commit();
        }
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM ip_pool_status WHERE id=1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM ip_adjust_log WHERE id=1", Integer.class)).isEqualTo(1);
    }

    /** 未确认 ABS 的定时入池日志可查询并按研究员结论转为无效 */
    @Test
    public void scheduledPendingInboundLogShouldBeInvalidated() {
        jdbc.update("INSERT INTO ip_adjust_log(id,security_code,security_short_name,security_type,adjust_type,adjust_mode,"
                + "target_pool_id,target_pool_name,pool_type,flow_type,audit_status,is_deleted) "
                + "VALUES(1,'ABS1','测试ABS','abs','自动调整','调入',101,'目标池','forbidden','absIdentityPending','00',0)");
        try (SqlSession session = factory.openSession()) {
            AbsIdentityMapper mapper = session.getMapper(AbsIdentityMapper.class);
            IpAdjustLogBo pending = mapper.queryPendingScheduledInboundLog("ABS1", 101L);
            assertThat(pending.getId()).isEqualTo(1L);
            assertThat(pending.getFlowType()).isEqualTo("absIdentityPending");
            assertThat(mapper.editPendingScheduledInboundSecurityShortName(1L, "补回ABS")).isEqualTo(1);
            assertThat(mapper.editPendingScheduledInboundInvalid(1L)).isEqualTo(1);
            session.commit();
        }
        assertThat(jdbc.queryForObject("SELECT security_short_name FROM ip_adjust_log WHERE id=1", String.class))
                .isEqualTo("补回ABS");
        assertThat(jdbc.queryForObject("SELECT audit_status FROM ip_adjust_log WHERE id=1", String.class))
                .isEqualTo("-1");
    }

    /** 研究员确认主体时，定时入池日志应同步更新为已完成入池原因 */
    @Test
    public void scheduledPendingInboundLogShouldBeApprovedWithConfirmedReason() {
        jdbc.update("INSERT INTO ip_adjust_log(id,security_code,security_short_name,security_type,adjust_type,adjust_mode,"
                + "target_pool_id,target_pool_name,pool_type,flow_type,adjust_reason,audit_status,is_deleted) "
                + "VALUES(1,'ABS1','测试ABS','abs','自动调整','调入',101,'目标池','forbidden',"
                + "'absIdentityPending','ABS主体自动联动入池（待确认）（触发主体：C10001）','00',0)");
        String confirmedReason = "ABS主体自动联动入池（研究员确认主体：C10001，已完成入池）（触发主体：C10001）";
        try (SqlSession session = factory.openSession()) {
            AbsIdentityMapper mapper = session.getMapper(AbsIdentityMapper.class);
            assertThat(mapper.editPendingScheduledInboundApproved(1L, confirmedReason)).isEqualTo(1);
            session.commit();
        }
        assertThat(jdbc.queryForObject("SELECT adjust_reason FROM ip_adjust_log WHERE id=1", String.class))
                .isEqualTo(confirmedReason);
        assertThat(jdbc.queryForObject("SELECT audit_status FROM ip_adjust_log WHERE id=1", String.class))
                .isEqualTo("20");
    }

    /** CRMW 使用共用日志及独立快照，审核中和驳回待修改均占用主体 */
    @Test
    public void crmwPendingShouldUseSharedLogAndCrmwSnapshot() {
        jdbc.update("INSERT INTO ip_adjust_log(id,security_code,security_type,target_pool_id,pool_type,audit_status,is_deleted) VALUES(1,'ABS1','abs',101,'crmw','00',0)");
        jdbc.update("INSERT INTO ip_adjust_security_snapshot_crmw(adjust_log_id,wind_code,issuer,issuer_code,rating_bondissuer,rating_bondissuer_agency,inner_issuer_rating,is_deleted) VALUES(1,'ABS1','甲','A','AA+','R1','2',0)");
        for (String status : Arrays.asList("00", "11")) {
            jdbc.update("UPDATE ip_adjust_log SET audit_status=?", status);
            try (SqlSession session = factory.openSession()) {
                AbsIdentityMapper mapper = session.getMapper(AbsIdentityMapper.class);
                assertThat(mapper.queryOccupiedIdentityCodes("ABS1")).containsExactly("A");
                SecurityInfoBo identity = mapper.queryPendingIdentityDetail("ABS1");
                assertThat(identity.getIssuerCode()).isEqualTo("A");
                assertThat(identity.getIssuer()).isEqualTo("甲");
                assertThat(identity.getInnerIssuerRating()).isEqualTo("2");
                assertThat(identity.getRatingBondissuer()).isEqualTo("AA+");
                assertThat(identity.getRatingBondissuerAgency()).isEqualTo("R1");
            }
        }
        jdbc.update("UPDATE ip_adjust_log SET audit_status='99'");
        try (SqlSession session = factory.openSession()) {
            assertThat(session.getMapper(AbsIdentityMapper.class).queryOccupiedIdentityCodes("ABS1")).isEmpty();
        }
    }

    /** 两条链路同时有申请时都参与唯一主体校验，不能因共用日志而读取错快照 */
    @Test
    public void ordinaryAndCrmwPendingShouldReserveIdentityTogether() {
        jdbc.update("INSERT INTO ip_adjust_log(id,security_code,security_type,target_pool_id,pool_type,audit_status,is_deleted) VALUES(1,'ABS1','abs',101,'credit','00',0),(2,'ABS1','abs',102,'crmw','11',0)");
        jdbc.update("INSERT INTO ip_adjust_security_snapshot(adjust_log_id,wind_code,issuer_code,is_deleted) VALUES(1,'ABS1','A',0)");
        jdbc.update("INSERT INTO ip_adjust_security_snapshot_crmw(adjust_log_id,wind_code,issuer_code,is_deleted) VALUES(2,'ABS1','B',0)");
        try (SqlSession session = factory.openSession()) {
            AbsIdentityMapper mapper = session.getMapper(AbsIdentityMapper.class);
            assertThat(mapper.queryOccupiedIdentityCodes("ABS1")).containsExactlyInAnyOrder("A", "B");
            AbsIdentityService service = new AbsIdentityService();
            ReflectionTestUtils.setField(service, "absIdentityMapper", mapper);
            assertThatThrownBy(() -> service.validateIdentity("ABS1", "A")).hasMessageContaining("主体[B]");
            assertThatThrownBy(() -> service.validateIdentity("ABS1", "B")).hasMessageContaining("主体[A]");
        }
    }

    /** A、B 兼任多个角色也只返回去重主体，四类之外的关系不参与 */
    @Test
    public void relatedSubjectsShouldUseExactlyTheFourTypes() {
        jdbc.update("INSERT INTO ais_inv_ods.wind_cbondissuer VALUES ('ABS1','A','甲',115201000,NOW()),('ABS1','B','乙',115200000,NOW()),('ABS1','B','乙',115203000,NOW()),('ABS1','X','范围外',999,NOW())");
        jdbc.update("INSERT INTO ip_pool_status(id,security_code,security_type,target_pool_id,audit_status,is_deleted,adjust_type) VALUES(1,'B','company',101,'20',0,'手工调整'),(2,'X','company',101,'20',0,'手工调整')");
        try (SqlSession session = factory.openSession()) {
            AbsIdentityMapper mapper = session.getMapper(AbsIdentityMapper.class);
            assertThat(mapper.queryOtherCompanyInPool("ABS1", "A", 101L)).containsExactly("B");
            assertThat(mapper.queryCompanyPoolContext("ABS1", 101L)).containsExactly("A:out:0", "B:in:0");
        }
    }

    /** A 不在主体池但 B 仍在时不能在候选 SQL 中直接忽略，后续需要生成研究员提醒 */
    @Test
    public void remainingRelatedCompanyShouldNotHideOutboundReminderCandidate() {
        jdbc.update("UPDATE rrs_securityinfo SET issuer_code='A',security_status='L'");
        jdbc.update("INSERT INTO dict_security_type VALUES ('company','主体','company',0)");
        jdbc.update("INSERT INTO ais_inv_ods.wind_cbondissuer VALUES ('ABS1','B','乙',115200000,NOW())");
        jdbc.update("INSERT INTO ip_pool_status(id,security_code,security_type,target_pool_id,audit_status,is_deleted) VALUES(1,'ABS1','abs',101,'20',0),(2,'B','company',202,'20',0)");
        try (SqlSession session = factory.openSession()) {
            assertThat(session.getMapper(AutoAdjustMapper.class).queryBondInPoolWhenCompanyNotIn(101L, 202L, null))
                    .singleElement().satisfies(row -> assertThat(row.getSecurityCode()).isEqualTo("ABS1"));
            assertThat(session.getMapper(AbsIdentityMapper.class).queryOtherCompanyInPool("ABS1", "A", 202L))
                    .containsExactly("B");
        }
    }

    /** 按评级日期取最新认可外评，评级与机构取同一条记录 */
    @Test
    public void candidatesShouldReturnLatestRecognizedOuterRatingAndAgencyTogether() {
        jdbc.update("INSERT INTO ais_inv_ods.wind_cbondissuer VALUES ('ABS1','A','甲',115201000,NOW())");
        jdbc.update("INSERT INTO ais_inv_analysis.v_inv_grade_result VALUES('A','甲','2',NOW())");
        jdbc.update("INSERT INTO ais_inv_analysis.t_inv_company VALUES('A','甲',NULL)");
        jdbc.update("INSERT INTO dict_external_rating_agency VALUES ('R1',0),('R2',0)");
        jdbc.update("INSERT INTO ais_inv_ods.wind_cbondissuerrating"
                + " (object_id,s_info_compcode,b_info_creditrating,b_info_creditratingagency,ann_dt,ann_dt2,opdate)"
                + " VALUES ('1','A','AA','R1','20260101','20260301',NOW()),"
                + " ('2','A','AA+','R2','20260201','20260201',NOW()),"
                + " ('3','A','AAA','INVALID','20260401','20260401',NOW())");
        try (SqlSession session = factory.openSession()) {
            SecurityPoolAdjustMapper mapper = session.getMapper(SecurityPoolAdjustMapper.class);
            assertThat(mapper.queryRelatedRatingCompanyList("ABS1")).singleElement().satisfies(row -> {
                assertThat(row.getCompanyCode()).isEqualTo("A");
                assertThat(row.getInnerRating()).isEqualTo("2");
                assertThat(row.getRatingBondissuer()).isEqualTo("AA+");
                assertThat(row.getRatingBondissuerAgency()).isEqualTo("R2");
            });
            assertThat(mapper.querySelfSelectedRightsHolderByCode("A")).satisfies(row -> {
                assertThat(row.getRatingBondissuer()).isEqualTo("AA+");
                assertThat(row.getRatingBondissuerAgency()).isEqualTo("R2");
            });
            assertThat(mapper.querySelfSelectedRightsHolderPage("A", null)).singleElement().satisfies(row -> {
                assertThat(row.getRatingBondissuer()).isEqualTo("AA+");
                assertThat(row.getRatingBondissuerAgency()).isEqualTo("R2");
            });
            assertThat(session.getMapper(CommonMapper.class).queryGuarantorGradeList(Collections.singletonList("ABS1")))
                    .singleElement().satisfies(row -> {
                        assertThat(row.getRatingBondissuer()).isEqualTo("AA+");
                        assertThat(row.getRatingBondissuerAgency()).isEqualTo("R2");
                    });
            assertThat(session.getMapper(WindRatingMapper.class).queryLatestRating("A", Arrays.asList("R1", "R2"))
                    .getCreditRating()).isEqualTo("AA+");
        }
    }

    /** 成功入库固定主体，完全出清后仍不能更换，原主体继续允许申请 */
    @Test
    public void identityLifecycleShouldPersistOnlyOnApprovalAndRemainBoundAfterClear() {
        jdbc.update("INSERT INTO ip_adjust_log(id,security_code,security_type,target_pool_id,audit_status,is_deleted) VALUES (1,'ABS1','abs',101,'00',0)");
        jdbc.update("INSERT INTO ip_adjust_security_snapshot(adjust_log_id,wind_code,issuer,issuer_code,rating_bondissuer,rating_bondissuer_agency,inner_issuer_rating,is_deleted) VALUES(1,'ABS1','甲','A','AA+','R1','2',0)");
        try (SqlSession session = factory.openSession(false)) {
            AbsIdentityService service = new AbsIdentityService();
            ReflectionTestUtils.setField(service, "absIdentityMapper", session.getMapper(AbsIdentityMapper.class));
            ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", session.getMapper(SecurityPoolAdjustMapper.class));
            assertThat(jdbc.queryForObject("SELECT issuer_code FROM rrs_securityinfo", String.class)).isNull();
            assertThatThrownBy(() -> service.validateIdentity("ABS1", "B")).hasMessageContaining("主体[A]");
            IpAdjustLogBo log = new IpAdjustLogBo();
            log.setId(1L);
            log.setSecurityCode("ABS1");
            log.setAdjustMode("调入");
            service.approveIdentities(Collections.singletonList(log));
            session.commit();
            assertThat(jdbc.queryForObject("SELECT issuer_code FROM rrs_securityinfo", String.class)).isEqualTo("A");
            assertThat(jdbc.queryForObject("SELECT rating_bondissuer_agency FROM rrs_securityinfo", String.class)).isEqualTo("R1");
            jdbc.update("UPDATE ip_adjust_log SET audit_status='20'");
            jdbc.update("INSERT INTO ip_pool_status(id,security_code,security_type,target_pool_id,audit_status,is_deleted) VALUES(1,'ABS1','abs',101,'20',0)");
            assertThatThrownBy(() -> service.validateIdentity("ABS1", "B")).hasMessageContaining("主体[A]");
            session.rollback(true);
            jdbc.update("UPDATE ip_pool_status SET is_deleted=1");
            assertThatThrownBy(() -> service.validateIdentity("ABS1", "B"))
                    .hasMessageContaining("主体[A]").hasMessageContaining("不能更换主体");
            service.validateIdentity("ABS1", "A");
            SecurityInfoBo master = session.getMapper(SecurityPoolAdjustMapper.class).querySecurityBoByCode("ABS1");
            assertThat(service.queryCurrentIdentity(master).getIssuerCode()).isEqualTo("A");
            session.rollback(true);
            assertThat(jdbc.queryForObject("SELECT issuer_code FROM ip_adjust_security_snapshot WHERE adjust_log_id=1", String.class)).isEqualTo("A");
        }
    }
}
