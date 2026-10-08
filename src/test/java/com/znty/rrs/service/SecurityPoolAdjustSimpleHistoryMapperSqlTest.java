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

/** 使用实际 Mapper 的完整 SQL 验证简易流程所需的非简易入库历史。 */
public class SecurityPoolAdjustSimpleHistoryMapperSqlTest {

    /** 固定当前时间，确保自然月边界测试不受执行时间影响 */
    private static final String REFERENCE_TIME = "2026-10-08 12:00:00";
    /** 最近半年内的入池时间 */
    private static final String RECENT_ENTRY_TIME = "2026-09-01 12:00:00";
    /** 独立内存数据库访问组件 */
    private JdbcTemplate jdbc;
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
                + "('OTHER_ISSUER','C002'),('EMPTY_CURRENT',''),('EMPTY_HISTORY',''),"
                + "('NULL_CURRENT',NULL),('NULL_HISTORY',NULL)");
        jdbc.update("INSERT INTO dict_security_type VALUES ('company_bond','bond',0),"
                + "('stock','stock',0),('deleted_bond','bond',1)");
        Configuration configuration = new Configuration();
        String resource = "mapper/SecurityPoolAdjustMapper.xml";
        try (InputStream xml = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(xml).as("证券池调整 Mapper XML 应存在").isNotNull();
            new XMLMapperBuilder(xml, configuration, resource, configuration.getSqlFragments()).parse();
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
        for (String flowType : new String[]{"simpleInbound", null, ""}) {
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

    /** 不同发行人、缺失或空发行人代码均不得命中同主体经历。 */
    @Test
    public void shouldRequireSameNonEmptyIssuerCode() {
        // 名称不参与关联，发行人代码不同的债券不能提供历史。
        insertHistory("OTHER_ISSUER", "normalInbound", RECENT_ENTRY_TIME);
        assertThat(hasHistory("CURRENT", 180)).isFalse();
        // 空主体与 NULL 主体均不得相互误认。
        insertHistory("EMPTY_HISTORY", "normalInbound", RECENT_ENTRY_TIME);
        insertHistory("NULL_HISTORY", "normalInbound", RECENT_ENTRY_TIME);
        assertThat(hasHistory("EMPTY_CURRENT", 180)).isFalse();
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
