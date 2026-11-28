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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用实际 Mapper 查询验证简易入库经历和证券分析时效 */
public class SecurityPoolAdjustSimpleHistoryMapperSqlTest {

    /** 固定查询时点，确保 180 天边界测试不受执行日期影响 */
    private static final String REFERENCE_TIME = "2026-10-08 12:00:00";
    /** 最近半年内的入池时间 */
    private static final String RECENT_ENTRY_TIME = "2026-09-01 12:00:00";
    /** 独立内存数据库访问组件 */
    private JdbcTemplate jdbc;
    /** 实际单笔和批量调整查询 */
    private Configuration configuration;
    /** 从实际 XML 加载的非简易入库历史查询 */
    private MappedStatement historyStatement;

    /** 建立最小数据集，并从生产 XML 加载完整查询语句。 */
    @Before
    public void setUp() throws Exception {
        String version = System.getProperty("java.specification.version");
        int javaMajor = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 数据库集成测试需要 Java 11 以上运行时", javaMajor >= 11);
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:simple_history_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE rrs_securityinfo (wind_code VARCHAR(40), issuer_code VARCHAR(40))");
        jdbc.execute("CREATE TABLE dict_security_type (security_type VARCHAR(40), category_type VARCHAR(40), is_deleted INT)");
        jdbc.execute("CREATE TABLE ip_pool_status (security_code VARCHAR(40), security_type VARCHAR(40),"
                + "target_pool_id BIGINT, pool_type VARCHAR(40), adjust_mode VARCHAR(20), flow_type VARCHAR(40),"
                + "audit_status VARCHAR(10), entry_time TIMESTAMP, is_deleted INT)");
        jdbc.update("INSERT INTO rrs_securityinfo VALUES ('CURRENT','C001'),('SAME_ISSUER','C001'),"
                + "('OTHER_ISSUER','C002'),"
                + "('NULL_CURRENT',NULL),('NULL_HISTORY',NULL)");
        jdbc.update("INSERT INTO dict_security_type VALUES ('company_bond','bond',0),"
                + "('stock','stock',0),('deleted_bond','bond',1)");
        configuration = new Configuration();
        for (String resource : Arrays.asList("mapper/SecurityPoolAdjustMapper.xml", "mapper/BatchSecurityCheckMapper.xml")) {
            try (InputStream xml = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertThat(xml).as("证券池调整 Mapper XML 应存在").isNotNull();
                new XMLMapperBuilder(xml, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        historyStatement = configuration.getMappedStatement("com.znty.rrs.mapper.SecurityPoolAdjustMapper."
                + "queryIssuerHasNonSimpleCreditBondInboundWithinDays");
    }

    /** 关闭本次测试的独立内存数据库。 */
    @After
    public void tearDown() {
        if (jdbc != null) {
            jdbc.execute("SET DB_CLOSE_DELAY 0");
        }
    }

    /** 同发行人其他债券的任意信用债层级历史均计入，无需与当前目标池相同。 */
    @Test
    public void shouldAcceptNormalInboundAcrossCreditBondLevels() {
        // 先验证没有入池历史时不会命中。
        assertThat(hasHistory("CURRENT", 180)).isFalse();
        for (long poolId : new long[]{2L, 4L, 6L, 104L}) {
            jdbc.update("DELETE FROM ip_pool_status");
            // 不同层级及不同信用债大库均独立验证。
            insertHistory("SAME_ISSUER", "normalInbound", RECENT_ENTRY_TIME);
            jdbc.update("UPDATE ip_pool_status SET target_pool_id = ?", poolId);
            // 查询身份取当前券，但历史可来自同发行人其他债券。
            assertThat(hasHistory("CURRENT", 180)).as("历史目标池 %s", poolId).isTrue();
        }
    }

    /** 已出池软删的审批通过非简易入库记录仍表示曾经入库。 */
    @Test
    public void shouldCountSoftDeletedInboundHistory() {
        // 构造已出池的同发行人非简易入库历史。
        insertHistory("SAME_ISSUER", "normalInbound", RECENT_ENTRY_TIME);
        jdbc.update("UPDATE ip_pool_status SET is_deleted = 1");
        // 历史经历不要求当前仍在库。
        assertThat(hasHistory("CURRENT", 180)).isTrue();
    }

    /** 升库、降库、特殊、白名单和批量调入均属于非简易入库经历。 */
    @Test
    public void shouldAcceptOtherNonSimpleInboundFlowTypes() {
        for (String flowType : new String[]{"upgradeInbound", "downgradeInbound",
                "whitelistInbound", "specialInbound", "batchInbound"}) {
            jdbc.update("DELETE FROM ip_pool_status");
            // 每次只放入一种非简易流程，验证无需依赖标准调入历史。
            insertHistory("SAME_ISSUER", flowType, RECENT_ENTRY_TIME);
            assertThat(hasHistory("CURRENT", 180)).as("流程类型 %s", flowType).isTrue();
        }
    }

    /** 简易流程以及空流程类型不能充当非简易入库经历。 */
    @Test
    public void shouldRejectSimpleOrMissingInboundFlowType() {
        for (String flowType : new String[]{"simpleInbound", null}) {
            jdbc.update("DELETE FROM ip_pool_status");
            // 未明确记录流程类型时不能认定为非简易入库。
            insertHistory("SAME_ISSUER", flowType, RECENT_ENTRY_TIME);
            assertThat(hasHistory("CURRENT", 180)).as("流程类型 %s", flowType).isFalse();
        }
    }

    /** 非信用债大库、非调入以及尚未通过或已驳回的记录均不得计入。 */
    @Test
    public void shouldRequireEffectiveCreditBondInbound() {
        // 构造唯一历史，再逐项改变其业务状态。
        insertHistory("SAME_ISSUER", "normalInbound", RECENT_ENTRY_TIME);
        jdbc.update("UPDATE ip_pool_status SET pool_type = 'bond_product'");
        // 其他债券池的一般入库不满足信用债大库经历。
        assertThat(hasHistory("CURRENT", 180)).isFalse();
        jdbc.update("UPDATE ip_pool_status SET pool_type = 'credit_bond', adjust_mode = '调出'");
        // 调出经历不能替代调入。
        assertThat(hasHistory("CURRENT", 180)).isFalse();
        for (String auditStatus : new String[]{"00", "11", "21", "99", null}) {
            jdbc.update("UPDATE ip_pool_status SET adjust_mode = '调入', audit_status = ?", auditStatus);
            // 只有审批通过才视为已入库。
            assertThat(hasHistory("CURRENT", 180)).as("审核状态 %s", auditStatus).isFalse();
        }
    }

    /** 半年按 180 天计算，精确边界计入，边界前一秒不计入。 */
    @Test
    public void shouldUseInclusive180DayBoundary() {
        // 固定当前时间减 180 天，精确到秒验证包含边界。
        insertHistory("SAME_ISSUER", "normalInbound", "2026-04-11 12:00:00");
        assertThat(hasHistory("CURRENT", 180)).isTrue();
        jdbc.update("UPDATE ip_pool_status SET entry_time = ?", Timestamp.valueOf("2026-04-11 11:59:59"));
        // 边界前一秒已经超过半年。
        assertThat(hasHistory("CURRENT", 180)).isFalse();
        jdbc.update("UPDATE ip_pool_status SET entry_time = NULL");
        // 缺少实际入池时间不能认定为半年内入库。
        assertThat(hasHistory("CURRENT", 180)).isFalse();
    }

    /** 指定天数由真实 MyBatis 参数绑定，不写死 180 天。 */
    @Test
    public void shouldBindRequestedDayCount() {
        // 四个月前的记录满足 180 天，但不满足 90 天范围。
        insertHistory("SAME_ISSUER", "normalInbound", "2026-06-08 12:00:00");
        assertThat(hasHistory("CURRENT", 180)).isTrue();
        assertThat(hasHistory("CURRENT", 90)).isFalse();
    }

    /** 当前时刻可以计入，未来入池时间不能认定为过去半年的历史。 */
    @Test
    public void shouldRejectFutureInboundHistory() {
        // 恰好当前时刻的入池记录已经生效，计入历史。
        insertHistory("SAME_ISSUER", "normalInbound", REFERENCE_TIME);
        assertThat(hasHistory("CURRENT", 180)).isTrue();
        jdbc.update("UPDATE ip_pool_status SET entry_time = ?", Timestamp.valueOf("2026-10-08 12:00:01"));
        // 未来一秒的错误日期不在过去 180 天内。
        assertThat(hasHistory("CURRENT", 180)).isFalse();
    }

    /** 发行人代码不同或为 NULL 时都不能命中同主体经历 */
    @Test
    public void shouldRequireSameNonEmptyIssuerCode() {
        // 名称不参与关联，发行人代码不同的债券不能提供历史。
        insertHistory("OTHER_ISSUER", "normalInbound", RECENT_ENTRY_TIME);
        assertThat(hasHistory("CURRENT", 180)).isFalse();
        // 两条证券的发行人代码均为 NULL 时不能认作同一主体
        insertHistory("NULL_HISTORY", "normalInbound", RECENT_ENTRY_TIME);
        assertThat(hasHistory("NULL_CURRENT", 180)).isFalse();
        assertThat(hasHistory("UNKNOWN", 180)).isFalse();
    }

    /** 证券类型必须关联到有效债券字典，股票或已删除类型不能提供债券经历。 */
    @Test
    public void shouldRequireActiveBondCategory() {
        // 构造非简易入库历史，再分别验证不属于有效债券类型的记录。
        insertHistory("SAME_ISSUER", "normalInbound", RECENT_ENTRY_TIME);
        for (String securityType : new String[]{"stock", "deleted_bond", "unknown_type"}) {
            jdbc.update("UPDATE ip_pool_status SET security_type = ?", securityType);
            // 历史证券类型依据入池状态快照和字典，而非目标池名称。
            assertThat(hasHistory("CURRENT", 180)).as("证券类型 %s", securityType).isFalse();
        }
    }

    /** 按分析来源时间选最新正文，引用了过期分析的新日志不满足单笔和批量资格 */
    @Test
    public void shouldUseOriginalAnalysisDateForSelectionAndQualification() {
        // 1. 准备原始正文和后来引用的快照，区分日志创建时间与分析来源时间
        jdbc.execute("CREATE TABLE ip_adjust_log (id BIGINT, security_code VARCHAR(40), adjust_mode VARCHAR(20),"
                + " audit_status VARCHAR(10), is_deleted INT, crte_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE ip_adjust_security_snapshot (id BIGINT, adjust_log_id BIGINT, wind_code VARCHAR(40),"
                + " analysis VARCHAR(100), analysis_source_time TIMESTAMP, is_deleted INT)");
        jdbc.update("INSERT INTO ip_adjust_log VALUES"
                + " (1,'SAME_ISSUER','调入','20',0,'2026-10-07 12:00:00'),"
                + " (2,'SAME_ISSUER','调入','20',0,'2026-09-01 12:00:00'),"
                + " (3,'SAME_ISSUER','调入','20',0,'2026-10-06 12:00:00'),"
                + " (4,'OTHER_ISSUER','调入','20',0,'2026-10-07 12:00:00')");
        jdbc.update("INSERT INTO ip_adjust_security_snapshot VALUES"
                + " (1,1,'SAME_ISSUER','过期引用','2026-04-01 12:00:00',0),"
                + " (2,2,'SAME_ISSUER','最新正文',NULL,0),"
                + " (3,3,'SAME_ISSUER','较旧引用','2026-07-08 12:00:00',0),"
                + " (4,4,'OTHER_ISSUER','其他主体的过期引用','2026-04-01 12:00:00',0)");

        // 2. 用同一查询时点检查批量取正文及单笔、批量资格
        Map<String, Object> params = new HashMap<>();
        params.put("companyCodes", Arrays.asList("C001", "C002"));
        params.put("securityCode", "CURRENT");
        params.put("days", 180);
        BoundSql latest = configuration.getMappedStatement("com.znty.rrs.mapper.BatchSecurityCheckMapper."
                + "queryLatestAnalysisByIssuers").getBoundSql(params);
        BoundSql eligible = configuration.getMappedStatement("com.znty.rrs.mapper.BatchSecurityCheckMapper."
                + "queryRecentAnalysisIssuers").getBoundSql(params);
        BoundSql single = configuration.getMappedStatement("com.znty.rrs.mapper.SecurityPoolAdjustMapper."
                + "queryIssuerHasRecentAnalysisWithinDays").getBoundSql(params);

        Map<String, Object> selected = jdbc.queryForMap(compatibleSql(latest), bind(latest, params));
        assertThat(selected).containsEntry("company_code", "C001").containsEntry("analysis", "最新正文")
                .containsEntry("analysis_source_time", Timestamp.valueOf(RECENT_ENTRY_TIME));
        assertThat(jdbc.queryForList(compatibleSql(eligible), String.class, bind(eligible, params))).containsExactly("C001");
        assertThat(jdbc.queryForObject(compatibleSql(single), Boolean.class, bind(single, params))).isTrue();

        // 3. 作废近期快照，验证新日志里的过期引用无法续期
        jdbc.update("UPDATE ip_adjust_security_snapshot SET is_deleted = 1 WHERE id IN (2, 3)");
        assertThat(jdbc.queryForList(compatibleSql(latest), bind(latest, params))).isEmpty();
        assertThat(jdbc.queryForList(compatibleSql(eligible), String.class, bind(eligible, params))).isEmpty();
        assertThat(jdbc.queryForObject(compatibleSql(single), Boolean.class, bind(single, params))).isFalse();
    }

    /** 写入一条审批通过的一般债券历史，其他测试条件在各场景中独立调整。 */
    private void insertHistory(String securityCode, String flowType, String entryTime) {
        jdbc.update("INSERT INTO ip_pool_status VALUES (?, 'company_bond', 2, 'credit_bond',"
                + "'调入', ?, '20', ?, 0)", securityCode, flowType, Timestamp.valueOf(entryTime));
    }

    /** 执行真实 XML 的完整 BoundSql，仅翻译 MySQL 日期函数并固定测试当前时间。 */
    private boolean hasHistory(String securityCode, int days) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("securityCode", securityCode);
        parameters.put("days", days);
        BoundSql boundSql = historyStatement.getBoundSql(parameters);
        return Boolean.TRUE.equals(jdbc.queryForObject(compatibleSql(boundSql), Boolean.class, bind(boundSql, parameters)));
    }

    /** 仅翻译 MySQL 日期函数，并固定测试当前时间 */
    private String compatibleSql(BoundSql boundSql) {
        String sql = boundSql.getSql();
        assertThat(sql).contains("DATE_SUB(NOW(), INTERVAL ? DAY)");
        sql = sql.replace("DATE_SUB(NOW(), INTERVAL ? DAY)",
                "DATEADD('DAY', -CAST(? AS INT), TIMESTAMP '" + REFERENCE_TIME + "')");
        return sql.replace("NOW()", "TIMESTAMP '" + REFERENCE_TIME + "'");
    }

    /** 按 MyBatis 生成的顺序绑定 SQL 参数 */
    private Object[] bind(BoundSql boundSql, Map<String, Object> parameters) {
        List<Object> values = new ArrayList<>();
        for (ParameterMapping mapping : boundSql.getParameterMappings()) {
            String name = mapping.getProperty();
            values.add(boundSql.hasAdditionalParameter(name) ? boundSql.getAdditionalParameter(name) : parameters.get(name));
        }
        return values.toArray();
    }
}
