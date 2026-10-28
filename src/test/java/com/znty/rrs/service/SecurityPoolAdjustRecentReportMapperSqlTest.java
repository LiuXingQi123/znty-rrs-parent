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

/** 使用实际 Mapper SQL 验证同发行人入库业务的信评附件及实际入库时间 */
public class SecurityPoolAdjustRecentReportMapperSqlTest {

    /** 固定当前时间，精确验证180天边界 */
    private static final String REFERENCE_TIME = "2026-10-08 12:00:00";
    /** 最近180天内的实际入库时间 */
    private static final String RECENT_ENTRY_TIME = "2026-09-01 12:00:00";
    /** 独立内存数据库访问组件 */
    private JdbcTemplate jdbc;
    /** 从实际 XML 加载的报告查询 */
    private MappedStatement reportStatement;
    /** 从实际 XML 加载的非简易信用债入库经历查询 */
    private MappedStatement historyStatement;

    /** 建立入库业务、报告和附件数据结构，再加载实际 Mapper */
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
        jdbc.execute("CREATE TABLE dict_security_type (security_type VARCHAR(40), category_type VARCHAR(40), is_deleted INT)");
        jdbc.execute("CREATE TABLE ip_pool_status (adjust_log_id BIGINT, security_code VARCHAR(40),"
                + "security_type VARCHAR(40), pool_type VARCHAR(40), adjust_mode VARCHAR(20),"
                + "flow_type VARCHAR(40), audit_status VARCHAR(10), entry_time TIMESTAMP, is_deleted INT)");
        jdbc.execute("CREATE TABLE ip_adjust_log (id BIGINT, security_code VARCHAR(40), adjust_mode VARCHAR(20),"
                + "audit_status VARCHAR(10), is_deleted INT, crte_time TIMESTAMP)");
        for (String tableName : new String[]{"rrs_report_in", "rrs_report_out"}) {
            jdbc.execute("CREATE TABLE " + tableName + " (id BIGINT, company_code VARCHAR(40),"
                    + "security_code VARCHAR(40), report_type VARCHAR(40), crte_time TIMESTAMP, is_deleted INT)");
        }
        jdbc.execute("CREATE TABLE sys_attachment (table_name VARCHAR(40), main_id BIGINT,"
                + "attachment_category VARCHAR(40), is_deleted INT, crte_time TIMESTAMP)");
        jdbc.update("INSERT INTO rrs_securityinfo VALUES ('CURRENT','C001'),('SAME_ISSUER','C001'),"
                + "('OTHER_ISSUER','C002'),('EMPTY_ISSUER',''),('NULL_ISSUER',NULL)");
        jdbc.update("INSERT INTO dict_security_type VALUES ('company_bond','bond',0),('stock','stock',0)");
        Configuration configuration = new Configuration();
        String resource = "mapper/SecurityPoolAdjustMapper.xml";
        try (InputStream xml = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(xml).as("证券池调整 Mapper XML 应存在").isNotNull();
            new XMLMapperBuilder(xml, configuration, resource, configuration.getSqlFragments()).parse();
        }
        String namespace = "com.znty.rrs.mapper.SecurityPoolAdjustMapper.";
        reportStatement = configuration.getMappedStatement(namespace + "queryIssuerHasRecentCreditReportWithinDays");
        historyStatement = configuration.getMappedStatement(namespace + "queryIssuerHasNonSimpleCreditBondInboundWithinDays");
    }

    /** 关闭独立内存数据库 */
    @After
    public void tearDown() {
        if (jdbc != null) {
            jdbc.execute("SET DB_CLOSE_DELAY 0");
        }
    }

    /** 手工上传、内部引用、外部引用只要挂在已入库业务的信评位置就计入 */
    @Test
    public void shouldAcceptAllThreeCreditAttachmentSources() {
        // 建立同发行人其他券的成功入库业务
        insertInbound(1L, "SAME_ISSUER", "normalInbound", RECENT_ENTRY_TIME);
        assertThat(hasReport("CURRENT", 180)).isFalse();
        for (String category : new String[]{"credit_report_hand", "credit_report_in", "credit_report_out"}) {
            jdbc.update("DELETE FROM sys_attachment");
            // 按三种来源分别检查同一业务的信评附件
            insertAttachment(1L, category);
            assertThat(hasReport("CURRENT", 180)).as("信评来源 %s", category).isTrue();
        }
    }

    /** 引用其他主体的旧入库或出库报告，均按当前入库业务认定 */
    @Test
    public void shouldIgnoreOriginalReportIssuerTypeAndCreationTime() {
        // 当前主体已成功入库，原报告属于另一个主体且早于180天
        insertInbound(1L, "SAME_ISSUER", "normalInbound", RECENT_ENTRY_TIME);
        for (String source : new String[]{"in", "out"}) {
            for (String reportType : new String[]{"bond_in_report", "bond_out_report"}) {
                jdbc.update("DELETE FROM sys_attachment");
                jdbc.update("DELETE FROM rrs_report_" + source);
                jdbc.update("INSERT INTO rrs_report_" + source + " VALUES (99, 'C002', 'OTHER_ISSUER', ?, ?, 0)",
                        reportType, Timestamp.valueOf("2025-01-01 12:00:00"));
                // 保存原库附件和本次入库业务的信评引用
                jdbc.update("INSERT INTO sys_attachment VALUES (?, 99, ?, 0, ?)",
                        "rrs_report_" + source, "report_" + source, Timestamp.valueOf("2025-01-01 12:00:00"));
                insertAttachment(1L, "credit_report_" + source);
                assertThat(hasReport("CURRENT", 180)).as("原报告 %s/%s", source, reportType).isTrue();
            }
        }
    }

    /** 同主体以调库证券的发行人代码判断，代码缺失或不同均不计入 */
    @Test
    public void shouldRequireMatchingBusinessIssuer() {
        // 附件存在，但入库证券属于其他发行人
        insertInbound(1L, "OTHER_ISSUER", "normalInbound", RECENT_ENTRY_TIME);
        insertAttachment(1L, "credit_report_in");
        assertThat(hasReport("CURRENT", 180)).isFalse();
        jdbc.update("UPDATE ip_pool_status SET security_code = 'SAME_ISSUER'");
        jdbc.update("UPDATE ip_adjust_log SET security_code = 'SAME_ISSUER'");
        assertThat(hasReport("CURRENT", 180)).isTrue();
        for (String securityCode : new String[]{"EMPTY_ISSUER", "NULL_ISSUER", "UNKNOWN"}) {
            assertThat(hasReport(securityCode, 180)).as("当前券 %s", securityCode).isFalse();
        }
        jdbc.update("UPDATE rrs_securityinfo SET issuer_code = '' WHERE wind_code = 'SAME_ISSUER'");
        assertThat(hasReport("CURRENT", 180)).isFalse();
    }

    /** 入库业务和状态均须通过，且必须有实际落池记录 */
    @Test
    public void shouldRequireApprovedInboundWithPoolStatus() {
        // 先确认审批通过且已落池的调入业务计入
        insertInbound(1L, "SAME_ISSUER", "normalInbound", RECENT_ENTRY_TIME);
        insertAttachment(1L, "credit_report_hand");
        assertThat(hasReport("CURRENT", 180)).isTrue();
        for (String status : new String[]{"00", "11", "21", "99", "-1"}) {
            jdbc.update("UPDATE ip_adjust_log SET audit_status = ?", status);
            assertThat(hasReport("CURRENT", 180)).as("业务状态 %s", status).isFalse();
        }
        jdbc.update("UPDATE ip_adjust_log SET audit_status = '20'");
        jdbc.update("UPDATE ip_pool_status SET audit_status = '00'");
        assertThat(hasReport("CURRENT", 180)).isFalse();
        jdbc.update("UPDATE ip_pool_status SET audit_status = '20', adjust_mode = '调出'");
        assertThat(hasReport("CURRENT", 180)).isFalse();
        jdbc.update("UPDATE ip_pool_status SET adjust_mode = '调入'");
        jdbc.update("UPDATE ip_adjust_log SET adjust_mode = '调出'");
        assertThat(hasReport("CURRENT", 180)).isFalse();
        jdbc.update("UPDATE ip_adjust_log SET adjust_mode = '调入', is_deleted = 1");
        assertThat(hasReport("CURRENT", 180)).isFalse();
        jdbc.update("UPDATE ip_adjust_log SET is_deleted = 0");
        jdbc.update("DELETE FROM ip_pool_status");
        assertThat(hasReport("CURRENT", 180)).isFalse();
    }

    /** 仅报告库有报告不能替代入库业务下的信评附件 */
    @Test
    public void shouldRejectLibraryOnlyReportsAndUnrelatedAttachments() {
        // 业务没有绑定附件，即使库中已有本主体报告也不计入
        insertInbound(1L, "SAME_ISSUER", "normalInbound", RECENT_ENTRY_TIME);
        jdbc.update("INSERT INTO rrs_report_in VALUES (1, 'C001', 'SAME_ISSUER', 'bond_in_report', ?, 0)",
                Timestamp.valueOf(RECENT_ENTRY_TIME));
        jdbc.update("INSERT INTO sys_attachment VALUES ('rrs_report_in', 1, 'report_in', 0, ?)",
                Timestamp.valueOf(RECENT_ENTRY_TIME));
        assertThat(hasReport("CURRENT", 180)).isFalse();
        jdbc.update("DELETE FROM sys_attachment");
        // 附件业务表、关联记录、分类和删除标志均须正确
        insertAttachment(1L, "credit_report_in");
        for (String category : new String[]{"material_hand", "material_in", "material_out", "report_in"}) {
            jdbc.update("UPDATE sys_attachment SET attachment_category = ?", category);
            assertThat(hasReport("CURRENT", 180)).as("非信评分类 %s", category).isFalse();
        }
        jdbc.update("UPDATE sys_attachment SET attachment_category = 'credit_report_in', is_deleted = 1");
        assertThat(hasReport("CURRENT", 180)).isFalse();
        jdbc.update("UPDATE sys_attachment SET is_deleted = 0, table_name = 'ip_adjust_log_fund'");
        assertThat(hasReport("CURRENT", 180)).isFalse();
        jdbc.update("UPDATE sys_attachment SET table_name = 'ip_adjust_log', main_id = 2");
        assertThat(hasReport("CURRENT", 180)).isFalse();
        jdbc.update("UPDATE sys_attachment SET main_id = 1");
        assertThat(hasReport("CURRENT", 180)).isTrue();
    }

    /** 时间窗口只取实际入库时间，日志创建和附件绑定时间都不续期 */
    @Test
    public void shouldUseEntryTimeInsteadOfLogOrAttachmentTime() {
        // 新业务和新附件不能让180天前的入库重新有效
        insertInbound(1L, "SAME_ISSUER", "normalInbound", "2026-04-11 11:59:59");
        insertAttachment(1L, "credit_report_hand");
        assertThat(hasReport("CURRENT", 180)).isFalse();
        jdbc.update("UPDATE ip_pool_status SET entry_time = ?", Timestamp.valueOf(RECENT_ENTRY_TIME));
        jdbc.update("UPDATE ip_adjust_log SET crte_time = ?", Timestamp.valueOf("2025-01-01 12:00:00"));
        jdbc.update("UPDATE sys_attachment SET crte_time = ?", Timestamp.valueOf("2025-01-01 12:00:00"));
        assertThat(hasReport("CURRENT", 180)).isTrue();
    }

    /** 实际入库时间包含180天精确边界，缺失或未来时间不计入 */
    @Test
    public void shouldUseInclusive180DayEntryBoundary() {
        // 固定当前时间减180天，边界时刻恰好计入
        insertInbound(1L, "SAME_ISSUER", "normalInbound", "2026-04-11 12:00:00");
        insertAttachment(1L, "credit_report_out");
        assertThat(hasReport("CURRENT", 180)).isTrue();
        jdbc.update("UPDATE ip_pool_status SET entry_time = ?", Timestamp.valueOf("2026-04-11 11:59:59"));
        assertThat(hasReport("CURRENT", 180)).isFalse();
        jdbc.update("UPDATE ip_pool_status SET entry_time = NULL");
        assertThat(hasReport("CURRENT", 180)).isFalse();
        jdbc.update("UPDATE ip_pool_status SET entry_time = ?", Timestamp.valueOf(REFERENCE_TIME));
        assertThat(hasReport("CURRENT", 180)).isTrue();
        jdbc.update("UPDATE ip_pool_status SET entry_time = ?", Timestamp.valueOf("2026-10-08 12:00:01"));
        assertThat(hasReport("CURRENT", 180)).isFalse();
    }

    /** 窗口天数使用实际参数，不写死180天 */
    @Test
    public void shouldBindRequestedDays() {
        // 约四个月前的入库满足180天，但不满足90天
        insertInbound(1L, "SAME_ISSUER", "normalInbound", "2026-06-01 12:00:00");
        insertAttachment(1L, "credit_report_in");
        assertThat(hasReport("CURRENT", 180)).isTrue();
        assertThat(hasReport("CURRENT", 90)).isFalse();
    }

    /** 已出池的历史入库仍计入，但非债券业务不计入 */
    @Test
    public void shouldCountExitedBondInboundHistory() {
        // 出池只结束当前池状态，不抹去历史入库和所附材料
        insertInbound(1L, "SAME_ISSUER", "normalInbound", RECENT_ENTRY_TIME);
        insertAttachment(1L, "credit_report_in");
        jdbc.update("UPDATE ip_pool_status SET is_deleted = 1");
        assertThat(hasReport("CURRENT", 180)).isTrue();
        jdbc.update("UPDATE ip_pool_status SET security_type = 'stock'");
        assertThat(hasReport("CURRENT", 180)).isFalse();
    }

    /** 非简易入库经历与信评附件允许来自同主体不同业务，不增加同一笔限制 */
    @Test
    public void shouldKeepInboundHistoryAndReportChecksIndependent() {
        // 第一笔非简易信用债入库没有信评附件
        insertInbound(1L, "CURRENT", "normalInbound", RECENT_ENTRY_TIME);
        // 第二笔简易入库有信评附件，只提供报告条件
        insertInbound(2L, "SAME_ISSUER", "simpleInbound", RECENT_ENTRY_TIME);
        insertAttachment(2L, "credit_report_out");
        assertThat(hasReport("CURRENT", 180)).isTrue();
        // 分别执行两个真实查询，验证两项可由不同业务满足
        assertThat(executeQuery(historyStatement, "CURRENT", 180)).isTrue();
        jdbc.update("UPDATE ip_pool_status SET entry_time = ? WHERE adjust_log_id = 1",
                Timestamp.valueOf("2025-01-01 12:00:00"));
        assertThat(hasReport("CURRENT", 180)).isTrue();
        // 信评条件命中不代替已经过期的非简易入库经历
        assertThat(executeQuery(historyStatement, "CURRENT", 180)).isFalse();
    }

    /** 创建已审批通过且实际落池的调入业务 */
    private void insertInbound(Long logId, String securityCode, String flowType, String entryTime) {
        jdbc.update("INSERT INTO ip_adjust_log VALUES (?, ?, '调入', '20', 0, ?)",
                logId, securityCode, Timestamp.valueOf(RECENT_ENTRY_TIME));
        jdbc.update("INSERT INTO ip_pool_status VALUES (?, ?, 'company_bond', 'credit_bond',"
                        + "'调入', ?, '20', ?, 0)", logId, securityCode, flowType, Timestamp.valueOf(entryTime));
    }

    /** 为指定调库记录保存信评附件 */
    private void insertAttachment(Long logId, String category) {
        jdbc.update("INSERT INTO sys_attachment VALUES ('ip_adjust_log', ?, ?, 0, ?)",
                logId, category, Timestamp.valueOf(RECENT_ENTRY_TIME));
    }

    /** 执行报告条件查询 */
    private boolean hasReport(String securityCode, int days) {
        // 复用真实 SQL 执行，仅替换H2不支持的日期函数
        return executeQuery(reportStatement, securityCode, days);
    }

    /** 执行实际 Mapper 的完整 SQL，仅翻译日期函数以适配H2 */
    private boolean executeQuery(MappedStatement statement, String securityCode, int days) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("securityCode", securityCode);
        parameters.put("days", days);
        BoundSql boundSql = statement.getBoundSql(parameters);
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
