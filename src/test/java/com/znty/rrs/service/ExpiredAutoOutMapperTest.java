package com.znty.rrs.service;

import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.ExcelPoolExclusionMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.junit.Assume;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 到期出池 SQL 的日期边界和 JDBC 参数绑定测试 */
public class ExpiredAutoOutMapperTest {

    /** 验证截止日期和调出限制参数的绑定，CRMW 限制须按三个组合字段匹配 */
    @Test
    public void expiredQueriesShouldBindOffsetDaysAndKeepExclusiveCutoff() throws Exception {
        Configuration configuration = loadMapper();
        for (String queryId : new String[]{"queryPoolSecurityByExpired", "queryCrmwPoolByExpired"}) {
            boolean crmw = "queryCrmwPoolByExpired".equals(queryId);
            Method method = AutoAdjustMapper.class.getMethod(queryId, Long.class, int.class, List.class);
            MappedStatement statement = configuration.getMappedStatement(
                    AutoAdjustMapper.class.getName() + "." + queryId);
            for (int days : new int[]{1, 0, 3, -1}) {
                Object params = new ParamNameResolver(configuration, method)
                        .getNamedParams(new Object[]{15L, days, Collections.emptyList()});
                BoundSql bound = statement.getBoundSql(params);
                assertThat(bound.getSql()).contains(
                        ".maturity_date < DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL ? DAY), '%Y%m%d')")
                        .doesNotContain(".maturity_date <=", "out_restricted");
                PreparedStatement prepared = mock(PreparedStatement.class);
                new DefaultParameterHandler(statement, params, bound).setParameters(prepared);
                verify(prepared).setLong(1, 15L);
                verify(prepared).setInt(2, days);
            }
            Object params = new ParamNameResolver(configuration, method)
                    .getNamedParams(new Object[]{15L, 1, Arrays.asList(23L, 17L)});
            BoundSql bound = statement.getBoundSql(params);
            assertThat(bound.getSql()).contains("SELECT DISTINCT security_code", "AS out_restricted",
                    "restricted.security_code = ips.security_code");
            if (crmw) {
                assertThat(bound.getSql()).contains("restricted.crmw_scode = ips.crmw_scode",
                        "restricted.crmw_stype = ips.crmw_stype", "pool_type = 'crmw'");
            } else {
                assertThat(bound.getSql()).contains("FROM ip_pool_status ips", "dst.category_type = 'bond'");
            }
            PreparedStatement prepared = mock(PreparedStatement.class);
            new DefaultParameterHandler(statement, params, bound).setParameters(prepared);
            verify(prepared).setLong(1, 23L);
            verify(prepared).setLong(2, 17L);
            verify(prepared).setLong(3, 15L);
            verify(prepared).setInt(4, 1);
        }
    }

    /** 到期候选包含 ABS 和 CRMW，排除股票，再按在池记录 ID 排除 Excel 保护项 */
    @Test
    public void expiredQueryShouldExcludeOnlyProtectedStatusRows() throws Exception {
        Assume.assumeTrue("H2 2.3.232 SQL 测试需 Java 11 以上运行时",
                !System.getProperty("java.specification.version").startsWith("1."));
        Configuration configuration = loadMapper();
        Method method = AutoAdjustMapper.class.getMethod("queryPoolSecurityByExpired", Long.class, int.class, List.class);
        MappedStatement mapped = configuration.getMappedStatement(AutoAdjustMapper.class.getName() + "." + method.getName());
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:;MODE=MySQL");
             Statement jdbc = conn.createStatement()) {
            jdbc.execute("CREATE TABLE ip_pool_status (id BIGINT PRIMARY KEY, target_pool_id BIGINT, security_code VARCHAR(16),"
                    + "security_short_name VARCHAR(16), security_type VARCHAR(16), audit_status VARCHAR(2), is_deleted INT,"
                    + "adjust_batch_no VARCHAR(24), adjust_reason VARCHAR(100))");
            jdbc.execute("CREATE TABLE ip_adjust_log (adjust_batch_no VARCHAR(24), adjust_type VARCHAR(16),"
                    + "adjust_mode VARCHAR(8), audit_status VARCHAR(2), is_deleted INT)");
            jdbc.execute("CREATE TABLE rrs_securityinfo (wind_code VARCHAR(16), maturity_date VARCHAR(8))");
            jdbc.execute("CREATE TABLE dict_security_type (security_type VARCHAR(16), category_type VARCHAR(16), is_deleted INT)");
            jdbc.execute("INSERT INTO dict_security_type VALUES ('mtn','bond',0),('abs','bond',0),"
                    + "('stock','stock',0),('crmw','bond',0)");
            jdbc.execute("INSERT INTO rrs_securityinfo VALUES ('DIRECT','20000101'),('FOLLOW','20000101'),"
                    + "('PENDING','20000101'),('STOCK','20000101'),('CRMW','20000101'),('CRMW_EXCEL','20000101')");
            jdbc.execute("INSERT INTO ip_adjust_log VALUES ('BOND_EXCEL','Excel导入','调入','20',0),"
                    + "('BOND_PENDING','Excel导入','调入','00',0)");
            jdbc.execute("INSERT INTO ip_pool_status VALUES"
                    + " (1,17,'DIRECT','债券一','mtn','20',0,'BOND_EXCEL',NULL),"
                    + " (2,17,'FOLLOW','债券二','mtn','20',0,'BOND_JOB','【任务名称：补债】【主体跟随】（【Excel主体跟随】）'),"
                    + " (3,23,'DIRECT','债券一','mtn','20',0,'BOND_MANUAL',NULL),"
                    + " (4,17,'PENDING','ABS债券','abs','20',0,'BOND_PENDING',NULL),"
                    + " (5,17,'STOCK','股票','stock','20',0,'STOCK_BASE',NULL),"
                    + " (6,17,'CRMW','凭证','crmw','20',0,'CRMW_BASE',NULL),"
                    + " (7,17,'CRMW_EXCEL','Excel凭证','crmw','20',0,'BOND_EXCEL',NULL)");

            Set<Long> protectedIds = new HashSet<>();
            MappedStatement protection = configuration.getMappedStatement(
                    ExcelPoolExclusionMapper.class.getName() + ".queryExcelProtectedPoolStatusIds");
            Method protectionMethod = ExcelPoolExclusionMapper.class.getMethod(
                    "queryExcelProtectedPoolStatusIds", Collection.class, boolean.class);
            Object protectionParams = new ParamNameResolver(configuration, protectionMethod)
                    .getNamedParams(new Object[]{Arrays.asList(17L, 23L), true});
            BoundSql protectionSql = protection.getBoundSql(protectionParams);
            try (PreparedStatement prepared = conn.prepareStatement(protectionSql.getSql())) {
                new DefaultParameterHandler(protection, protectionParams, protectionSql).setParameters(prepared);
                try (ResultSet rows = prepared.executeQuery()) {
                    while (rows.next()) {
                        protectedIds.add(rows.getLong("id"));
                    }
                }
            }
            assertThat(protectedIds).containsExactlyInAnyOrder(1L, 2L, 7L);
            for (long poolId : new long[]{17L, 23L}) {
                Object params = new ParamNameResolver(configuration, method)
                        .getNamedParams(new Object[]{poolId, 0, Collections.emptyList()});
                BoundSql bound = mapped.getBoundSql(params);
                // H2 使用等价的日期表达式，其他条件执行原 Mapper SQL
                String sql = bound.getSql().replace("DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL ? DAY), '%Y%m%d')",
                        "FORMATDATETIME(DATEADD('DAY', -?, CURRENT_DATE), 'yyyyMMdd')");
                try (PreparedStatement prepared = conn.prepareStatement(sql)) {
                    new DefaultParameterHandler(mapped, params, bound).setParameters(prepared);
                    List<ScheduledAdjustCandidateDto> candidates = new ArrayList<>();
                    try (ResultSet rows = prepared.executeQuery()) {
                        while (rows.next()) {
                            ScheduledAdjustCandidateDto candidate = new ScheduledAdjustCandidateDto();
                            candidate.setPoolStatusId(rows.getLong("pool_status_id"));
                            candidate.setSecurityCode(rows.getString("security_code"));
                            candidates.add(candidate);
                        }
                    }
                    assertThat(candidates).hasSize(poolId == 17L ? 5 : 1);
                    assertThat(ExcelPoolExclusionService.excludeProtectedRows(candidates, protectedIds))
                            .extracting(ScheduledAdjustCandidateDto::getSecurityCode)
                            .containsExactlyInAnyOrder(poolId == 17L ? new String[]{"PENDING", "CRMW"}
                                    : new String[]{"DIRECT"});
                }
            }
        }
    }

    private Configuration loadMapper() throws Exception {
        Configuration configuration = new Configuration();
        for (String resource : Arrays.asList("mapper/AutoAdjustMapper.xml", "mapper/ExcelPoolExclusionMapper.xml")) {
            try (InputStream input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        return configuration;
    }
}
