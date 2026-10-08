package com.znty.rrs.service;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.InputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用实际 Mapper 的完整 SQL 验证简易流程所需的报告库信评报告。 */
public class SecurityPoolAdjustRecentReportMapperSqlTest {

    /** 固定当前时间，精确验证 180 天边界 */
    private static final String REFERENCE_TIME = "2026-10-08 12:00:00";
    /** 最近 180 天内的报告创建时间 */
    private static final String RECENT_REPORT_TIME = "2026-09-01 12:00:00";
    /** 独立内存数据库访问组件 */
    private JdbcTemplate jdbc;
    /** 从实际 XML 加载的报告查询 */
    private MappedStatement reportStatement;

    /** 建立报告库和有效附件数据，并加载真实生产 XML。 */
    @Before
    public void setUp() throws Exception {
        String version = System.getProperty("java.specification.version");
        int javaMajor = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 数据库集成测试需要 Java 11 以上运行时", javaMajor >= 11);
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:simple_report_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE rrs_securityinfo (wind_code VARCHAR(40), issuer_code VARCHAR(40))");
        for (String tableName : new String[]{"rrs_report_in", "rrs_report_out"}) {
            jdbc.execute("CREATE TABLE " + tableName + " (id BIGINT, company_code VARCHAR(40),"
                    + "security_code VARCHAR(40), report_type VARCHAR(40), crte_time TIMESTAMP, is_deleted INT)");
        }
        jdbc.execute("CREATE TABLE sys_attachment (table_name VARCHAR(40), main_id BIGINT,"
                + "attachment_category VARCHAR(40), is_deleted INT)");
        jdbc.update("INSERT INTO rrs_securityinfo VALUES ('CURRENT','C001'),('SAME_ISSUER','C001'),"
                + "('OTHER_ISSUER','C002'),('EMPTY_ISSUER',''),('NULL_ISSUER',NULL)");
        Configuration configuration = new Configuration();
        String resource = "mapper/SecurityPoolAdjustMapper.xml";
        try (InputStream xml = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(xml).as("证券池调整 Mapper XML 应存在").isNotNull();
            new XMLMapperBuilder(xml, configuration, resource, configuration.getSqlFragments()).parse();
        }
        reportStatement = configuration.getMappedStatement("com.znty.rrs.mapper.SecurityPoolAdjustMapper."
                + "queryIssuerHasRecentCreditReportWithinDays");
    }

    /** 关闭独立内存数据库。 */
    @After
    public void tearDown() {
        if (jdbc != null) {
            jdbc.execute("SET DB_CLOSE_DELAY 0");
        }
    }

    /** 同发行人的内部或外部债券入库、出库报告均可满足信评报告门槛。 */
    @Test
    public void shouldAcceptInternalAndExternalCreditReports() {
        // 没有报告时不得命中。
        assertThat(hasReport("CURRENT", 180)).isFalse();
        for (String tableName : new String[]{"rrs_report_in", "rrs_report_out"}) {
            for (String reportType : new String[]{"bond_in_report", "bond_out_report"}) {
                jdbc.update("DELETE FROM rrs_report_in");
                jdbc.update("DELETE FROM rrs_report_out");
                jdbc.update("DELETE FROM sys_attachment");
                // 不要求报告证券代码等于当前券，同发行人报告即可。
                insertReport(tableName, "C001", reportType, RECENT_REPORT_TIME);
                insertAttachment(tableName);
                assertThat(hasReport("CURRENT", 180)).as("%s/%s", tableName, reportType).isTrue();
            }
        }
    }

    /** 以报告的明确主体编码关联，不按证券代码推断缺失或矛盾主体。 */
    @Test
    public void shouldRequireMatchingReportCompanyCode() {
        // 报告证券属于当前发行人，但主体字段不同，不得放行。
        insertReport("rrs_report_in", "C002", "bond_in_report", RECENT_REPORT_TIME);
        insertAttachment("rrs_report_in");
        assertThat(hasReport("CURRENT", 180)).isFalse();
        for (String companyCode : new String[]{null, ""}) {
            jdbc.update("UPDATE rrs_report_in SET company_code = ?", companyCode);
            // 主体字段缺失时，不回退到 report.security_code 关联。
            assertThat(hasReport("CURRENT", 180)).isFalse();
        }
        jdbc.update("UPDATE rrs_report_in SET company_code = 'C001'");
        // 当前券发行人代码也必须明确且非空。
        assertThat(hasReport("EMPTY_ISSUER", 180)).isFalse();
        assertThat(hasReport("NULL_ISSUER", 180)).isFalse();
        assertThat(hasReport("UNKNOWN", 180)).isFalse();
    }

    /** 其他报告类型和已删除报告不能充当有效信评报告。 */
    @Test
    public void shouldRejectNonCreditAndDeletedReports() {
        // 正确附件不能把其他报告类型转为信评。
        insertReport("rrs_report_in", "C001", "fund_in_report", RECENT_REPORT_TIME);
        insertAttachment("rrs_report_in");
        for (String reportType : new String[]{"fund_in_report", "stock_in_report", "other_report", null}) {
            jdbc.update("UPDATE rrs_report_in SET report_type = ?", reportType);
            assertThat(hasReport("CURRENT", 180)).as("报告类型 %s", reportType).isFalse();
        }
        jdbc.update("UPDATE rrs_report_in SET report_type = 'bond_in_report', is_deleted = 1");
        // 逻辑删除的信评报告不得计入。
        assertThat(hasReport("CURRENT", 180)).isFalse();
    }

    /** 没有附件、附件已删除或附件业务来源不匹配时，报告无效。 */
    @Test
    public void shouldRequireActiveMatchingReportAttachment() {
        // 仅报告记录，不足以证明存在可用报告文件。
        insertReport("rrs_report_in", "C001", "bond_in_report", RECENT_REPORT_TIME);
        assertThat(hasReport("CURRENT", 180)).isFalse();
        // 外部附件不能通过相同 ID 冒充内部报告文件。
        insertAttachment("rrs_report_out");
        assertThat(hasReport("CURRENT", 180)).isFalse();
        jdbc.update("UPDATE sys_attachment SET table_name = 'rrs_report_in', attachment_category = 'material_in'");
        // 分类错误的其他材料不能提供信评报告。
        assertThat(hasReport("CURRENT", 180)).isFalse();
        jdbc.update("UPDATE sys_attachment SET attachment_category = 'report_in', is_deleted = 1");
        assertThat(hasReport("CURRENT", 180)).isFalse();
        jdbc.update("UPDATE sys_attachment SET is_deleted = 0, main_id = 2");
        // 附件必须归属本报告。
        assertThat(hasReport("CURRENT", 180)).isFalse();
        jdbc.update("UPDATE sys_attachment SET main_id = 1");
        assertThat(hasReport("CURRENT", 180)).isTrue();
    }

    /** 内部和外部报告均按创建时间 180 天精确边界判断。 */
    @Test
    public void shouldUseInclusive180DayReportBoundary() {
        for (String tableName : new String[]{"rrs_report_in", "rrs_report_out"}) {
            jdbc.update("DELETE FROM rrs_report_in");
            jdbc.update("DELETE FROM rrs_report_out");
            jdbc.update("DELETE FROM sys_attachment");
            // 固定当前时间减 180 天，恰好落在边界的有效报告计入。
            insertReport(tableName, "C001", "bond_in_report", "2026-04-11 12:00:00");
            insertAttachment(tableName);
            assertThat(hasReport("CURRENT", 180)).isTrue();
            jdbc.update("UPDATE " + tableName + " SET crte_time = ?", Timestamp.valueOf("2026-04-11 11:59:59"));
            assertThat(hasReport("CURRENT", 180)).isFalse();
            jdbc.update("UPDATE " + tableName + " SET crte_time = NULL");
            // 日期缺失不能推定为近期报告。
            assertThat(hasReport("CURRENT", 180)).isFalse();
        }
    }

    /** 当前时刻的报告计入，内部和外部库未来日期的报告均不计入。 */
    @Test
    public void shouldRejectFutureReportDates() {
        for (String tableName : new String[]{"rrs_report_in", "rrs_report_out"}) {
            jdbc.update("DELETE FROM rrs_report_in");
            jdbc.update("DELETE FROM rrs_report_out");
            jdbc.update("DELETE FROM sys_attachment");
            // 先验证报告时间恰好为当前时刻时包含上界。
            insertReport(tableName, "C001", "bond_in_report", REFERENCE_TIME);
            insertAttachment(tableName);
            assertThat(hasReport("CURRENT", 180)).isTrue();
            jdbc.update("UPDATE " + tableName + " SET crte_time = ?", Timestamp.valueOf("2026-10-08 12:00:01"));
            // 未来日期报告不能充当过去半年的信评报告。
            assertThat(hasReport("CURRENT", 180)).isFalse();
        }
    }

    /** 创建一条指定主体、类型和时间的报告。 */
    private void insertReport(String tableName, String companyCode, String reportType, String reportTime) {
        jdbc.update("INSERT INTO " + tableName + " VALUES (1, ?, 'SAME_ISSUER', ?, ?, 0)",
                companyCode, reportType, Timestamp.valueOf(reportTime));
    }

    /** 为指定报告库的报告添加来源和分类正确的有效附件。 */
    private void insertAttachment(String tableName) {
        String category = "rrs_report_in".equals(tableName) ? "report_in" : "report_out";
        jdbc.update("INSERT INTO sys_attachment VALUES (?, 1, ?, 0)", tableName, category);
    }

    /** 执行真实 XML 的完整 BoundSql，仅翻译日期函数以适配 H2。 */
    private boolean hasReport(String securityCode, int days) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("securityCode", securityCode);
        parameters.put("days", days);
        BoundSql boundSql = reportStatement.getBoundSql(parameters);
        String sql = boundSql.getSql();
        assertThat(sql).contains("DATE_SUB(NOW(), INTERVAL ? DAY)");
        sql = sql.replace("DATE_SUB(NOW(), INTERVAL ? DAY)",
                "DATEADD('DAY', -CAST(? AS INT), TIMESTAMP '" + REFERENCE_TIME + "')");
        sql = sql.replace("NOW()", "TIMESTAMP '" + REFERENCE_TIME + "'");
        List<Object> values = new ArrayList<>();
        for (ParameterMapping mapping : boundSql.getParameterMappings()) {
            values.add(parameters.get(mapping.getProperty()));
        }
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, values.toArray()));
    }
}
