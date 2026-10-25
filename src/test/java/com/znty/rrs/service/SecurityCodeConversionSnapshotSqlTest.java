package com.znty.rrs.service;

import com.znty.rrs.entity.bo.SecurityCodeConversionBo;
import com.znty.rrs.mapper.SecurityCodeConversionMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.Assume;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/** 用隔离内存库验证独立转换的快照 INSERT、旧快照保留及运行态引用判断。 */
public class SecurityCodeConversionSnapshotSqlTest {

    /** 证券换码和仅凭证换码都按实际标的主档新增，旧快照不构成残留引用。 */
    @Test
    public void shouldInsertMasterSnapshotsAndIgnoreRetainedHistoricalVersions() throws Exception {
        Assume.assumeTrue("H2 需要 Java 11 以上", !System.getProperty("java.specification.version").startsWith("1."));
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:conversion_snapshot_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        for (String file : Arrays.asList("rrs_external_import_schema.sql", "rrs_adjust_snapshot_schema.sql",
                "rrs_security_pool_adjust_schema.sql", "rrs_crmw_pool_status_schema.sql")) {
            String sql = new String(Files.readAllBytes(Paths.get("sql", file)), StandardCharsets.UTF_8);
            Matcher ddl = Pattern.compile("CREATE TABLE\\s+`?\\w+`?\\s*\\(.*?\\)\\s*ENGINE\\s*=.*?;", Pattern.DOTALL).matcher(sql);
            while (ddl.find()) {
                jdbc.execute(ddl.group().replaceFirst("(?s)\\)\\s*ENGINE.*$", ")"));
            }
        }
        // 这些空表仅用于执行生产运行态复核 SQL，不连接业务数据库
        jdbc.execute("CREATE TABLE IF NOT EXISTS my_security_pool(security_code VARCHAR(100),status VARCHAR(20))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS rrs_report_in(security_code VARCHAR(100),is_deleted INT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS rrs_report_out(security_code VARCHAR(100),is_deleted INT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ip_grade_rule_alert(security_code VARCHAR(100),alert_status VARCHAR(10),is_deleted INT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS sys_imp_tmp(imp_id VARCHAR(100),is_deleted INT,save_rslt VARCHAR(10),template_code VARCHAR(100))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS sys_imp_tmp_detl(imp_id VARCHAR(100),is_deleted INT,fld001 VARCHAR(100),fld004 VARCHAR(100))");
        jdbc.update("INSERT INTO rrs_securityinfo(wind_code,full_name,short_name,security_type,coupon_rate,maturity_date,analysis) VALUES"
                + " ('T.IB','临时全称','临时简称','mtn','1.0','20300101','临时主档'),"
                + " ('B.IB','正式全称','正式简称','mtn','3.5','20351231','正式主档分析'),"
                + " ('X.IB','另一标的全称','另一标的','mtn','4.2','20361231','标的主档分析')");
        jdbc.update("INSERT INTO ip_adjust_log(id,security_code,security_type,pool_type,crmw_scode,audit_status,adjuster_id,is_deleted)"
                + " VALUES (11,'T.IB','mtn','credit_bond',NULL,'00','u1',0),(12,'X.IB','mtn','crmw','T.IB','11','u2',0)");
        jdbc.update("INSERT INTO ip_adjust_security_snapshot(id,adjust_log_id,wind_code,analysis,is_deleted) VALUES(21,11,'T.IB','旧分析',0)");
        jdbc.update("INSERT INTO ip_adjust_security_snapshot_crmw(id,adjust_log_id,wind_code,crmw_scode,analysis,is_deleted)"
                + " VALUES(31,12,'X.IB','T.IB','旧凭证分析',0)");
        Map<String, Object> oldOrdinary = jdbc.queryForMap("SELECT * FROM ip_adjust_security_snapshot WHERE id=21");
        Map<String, Object> oldCrmw = jdbc.queryForMap("SELECT * FROM ip_adjust_security_snapshot_crmw WHERE id=31");
        Configuration config = new Configuration(new Environment("test", new JdbcTransactionFactory(), ds));
        config.setMapUnderscoreToCamelCase(true);
        try (InputStream xml = Files.newInputStream(Paths.get("src/main/resources/mapper/SecurityCodeConversionMapper.xml"))) {
            new XMLMapperBuilder(xml, config, "SecurityCodeConversionMapper.xml", config.getSqlFragments()).parse();
        }
        try (SqlSession session = new SqlSessionFactoryBuilder().build(config).openSession(true)) {
            SecurityCodeConversionMapper mapper = session.getMapper(SecurityCodeConversionMapper.class);
            SecurityCodeConversionBo mapping = new SecurityCodeConversionBo();
            mapping.setTempSecurityCode("T.IB");
            mapping.setSecurityCode("B.IB");
            mapping.setShortName("正式简称");
            mapping.setSecurityType("mtn");
            mapping.setUpdateTime(new Date());
            assertThat(mapper.editAdjustLogSecurityReference(mapping, Arrays.asList(11L))).isEqualTo(1);
            assertThat(mapper.editAdjustLogCrmwReference(mapping, Arrays.asList(12L))).isEqualTo(1);
            List<SecurityCodeConversionBo> logs = mapper.queryPendingAdjustLogListForSnapshot(Arrays.asList(11L, 12L));
            assertThat(logs).hasSize(2);
            assertThat(mapper.addSecuritySnapshotFromMaster(11L, "u1", logs.get(0).getSecurityCode(), new Date())).isEqualTo(1);
            assertThat(mapper.addCrmwSecuritySnapshotFromMaster(logs.get(1), new Date())).isEqualTo(1);
            // 两份原快照的全部字段原样保留，新记录分别来自正式证券和实际标的主档
            assertThat(jdbc.queryForMap("SELECT * FROM ip_adjust_security_snapshot WHERE id=21")).isEqualTo(oldOrdinary);
            assertThat(jdbc.queryForMap("SELECT * FROM ip_adjust_security_snapshot_crmw WHERE id=31")).isEqualTo(oldCrmw);
            assertThat(jdbc.queryForObject("SELECT coupon_rate FROM ip_adjust_security_snapshot WHERE wind_code='B.IB'", String.class)).isEqualTo("3.5");
            assertThat(jdbc.queryForObject("SELECT full_name FROM ip_adjust_security_snapshot WHERE wind_code='B.IB'", String.class)).isEqualTo("正式全称");
            assertThat(jdbc.queryForObject("SELECT coupon_rate FROM ip_adjust_security_snapshot_crmw WHERE crmw_scode='B.IB'", String.class)).isEqualTo("4.2");
            assertThat(jdbc.queryForObject("SELECT wind_code FROM ip_adjust_security_snapshot_crmw WHERE crmw_scode='B.IB'", String.class)).isEqualTo("X.IB");
            assertThat(mapper.queryRuntimeReferenceCount(mapping)).isZero();
        } finally {
            jdbc.execute("SET DB_CLOSE_DELAY 0");
        }
    }
}
