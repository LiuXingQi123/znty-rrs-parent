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

/** 到期出池 SQL 的主档状态、摘牌日边界和 JDBC 参数绑定测试 */
public class ExpiredAutoOutMapperTest {

    private static final String MYSQL_CUTOFF = "DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL ? DAY), '%Y%m%d')";
    private static final String H2_CUTOFF = "FORMATDATETIME(DATEADD('DAY', -?, CURRENT_DATE), 'yyyyMMdd')";

    /** 验证摘牌日的日期边界和调出限制参数绑定，CRMW 限制须按三个组合字段匹配 */
    @Test
    public void expiredQueriesShouldBindOffsetDaysAndUseDelistDate() throws Exception {
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
                        "AND (" + (crmw ? "crmw" : "si") + ".security_status = 'D'",
                        ".delist_date IS NOT NULL", ".delist_date != ''",
                        (crmw ? "crmw" : "si") + ".delist_date < " + MYSQL_CUTOFF)
                        .doesNotContain(".delist_date <=", "out_restricted");
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
            jdbc.execute("CREATE TABLE rrs_securityinfo (wind_code VARCHAR(16), delist_date VARCHAR(8), security_status VARCHAR(1))");
            jdbc.execute("CREATE TABLE dict_security_type (security_type VARCHAR(16), category_type VARCHAR(16), is_deleted INT)");
            jdbc.execute("INSERT INTO dict_security_type VALUES ('mtn','bond',0),('abs','bond',0),"
                    + "('stock','stock',0),('crmw','bond',0)");
            jdbc.execute("INSERT INTO rrs_securityinfo VALUES ('DIRECT',NULL,'D'),('FOLLOW','20000101','D'),"
                    + "('PENDING','20000101','D'),('STOCK','20000101','D'),('CRMW','20000101','D'),"
                    + "('CRMW_EXCEL','20000101','D'),('ACTIVE','20000101','L'),('NO_DATE',NULL,'D'),('EMPTY_DATE','','D'),"
                    + "('CUTOFF',FORMATDATETIME(DATEADD('DAY', -1, CURRENT_DATE),'yyyyMMdd'),'D'),"
                    + "('FUTURE_D','20991231','D'),('FUTURE_L','20991231','L'),('NO_DATE_L',NULL,'L'),"
                    + "('CUTOFF_L',FORMATDATETIME(DATEADD('DAY', -1, CURRENT_DATE),'yyyyMMdd'),'L')");
            jdbc.execute("INSERT INTO ip_adjust_log VALUES ('BOND_EXCEL','Excel导入','调入','20',0),"
                    + "('BOND_PENDING','Excel导入','调入','00',0)");
            jdbc.execute("INSERT INTO ip_pool_status VALUES"
                    + " (1,17,'DIRECT','债券一','mtn','20',0,'BOND_EXCEL',NULL),"
                    + " (2,17,'FOLLOW','债券二','mtn','20',0,'BOND_JOB','【任务名称：补债】【主体跟随】（【Excel主体跟随】）'),"
                    + " (3,23,'DIRECT','债券一','mtn','20',0,'BOND_MANUAL',NULL),"
                    + " (4,17,'PENDING','ABS债券','abs','20',0,'BOND_PENDING',NULL),"
                    + " (5,17,'STOCK','股票','stock','20',0,'STOCK_BASE',NULL),"
                    + " (6,17,'CRMW','凭证','crmw','20',0,'CRMW_BASE',NULL),"
                    + " (7,17,'CRMW_EXCEL','Excel凭证','crmw','20',0,'BOND_EXCEL',NULL),"
                    + " (8,17,'ACTIVE','存续债券','mtn','20',0,'BOND_MANUAL',NULL),"
                    + " (9,17,'NO_DATE','摘牌日NULL','mtn','20',0,'BOND_MANUAL',NULL),"
                    + " (10,17,'EMPTY_DATE','摘牌日空串','mtn','20',0,'BOND_MANUAL',NULL),"
                    + " (11,17,'CUTOFF','退市且摘牌日等于截止日','mtn','20',0,'BOND_MANUAL',NULL),"
                    + " (12,17,'FUTURE_D','退市且摘牌日晚于截止日','mtn','20',0,'BOND_MANUAL',NULL),"
                    + " (13,17,'FUTURE_L','摘牌日晚于截止日','mtn','20',0,'BOND_MANUAL',NULL),"
                    + " (14,17,'NO_DATE_L','存续且摘牌日NULL','mtn','20',0,'BOND_MANUAL',NULL),"
                    + " (15,17,'CUTOFF_L','存续且摘牌日等于截止日','mtn','20',0,'BOND_MANUAL',NULL),"
                    + " (16,17,'NO_DATE','未生效退市债券','mtn','00',0,'BOND_MANUAL',NULL),"
                    + " (17,17,'NO_DATE','已删除退市债券','mtn','20',1,'BOND_MANUAL',NULL)");

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
                        .getNamedParams(new Object[]{poolId, 1, Collections.emptyList()});
                BoundSql bound = mapped.getBoundSql(params);
                try (PreparedStatement prepared = conn.prepareStatement(bound.getSql().replace(MYSQL_CUTOFF, H2_CUTOFF))) {
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
                    assertThat(candidates).hasSize(poolId == 17L ? 10 : 1);
                    assertThat(ExcelPoolExclusionService.excludeProtectedRows(candidates, protectedIds))
                            .extracting(ScheduledAdjustCandidateDto::getSecurityCode)
                            .containsExactlyInAnyOrder(poolId == 17L
                                    ? new String[]{"PENDING", "CRMW", "ACTIVE", "NO_DATE", "EMPTY_DATE", "CUTOFF", "FUTURE_D"}
                                    : new String[]{"DIRECT"});
                }
            }
        }
    }

    /** 组合按凭证状态或摘牌日判断，标的债券单独到期不触发出池 */
    @Test
    public void crmwExpiredQueryShouldUseOnlyCertificateStatusAndDelistDate() throws Exception {
        Assume.assumeTrue("H2 2.3.232 SQL 测试需 Java 11 以上运行时",
                !System.getProperty("java.specification.version").startsWith("1."));
        Configuration configuration = loadMapper();
        Method method = AutoAdjustMapper.class.getMethod("queryCrmwPoolByExpired", Long.class, int.class, List.class);
        MappedStatement mapped = configuration.getMappedStatement(AutoAdjustMapper.class.getName() + "." + method.getName());
        Object params = new ParamNameResolver(configuration, method).getNamedParams(new Object[]{18L, 1, Collections.emptyList()});
        BoundSql bound = mapped.getBoundSql(params);
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:;MODE=MySQL");
             Statement jdbc = conn.createStatement()) {
            jdbc.execute("CREATE TABLE ip_pool_status_crmw (target_pool_id BIGINT, security_code VARCHAR(16),"
                    + "security_short_name VARCHAR(16), security_type VARCHAR(16), crmw_name VARCHAR(16),"
                    + "crmw_scode VARCHAR(16), crmw_mktcode VARCHAR(16), crmw_stype VARCHAR(16),"
                    + "audit_status VARCHAR(2), is_deleted INT, pool_type VARCHAR(16))");
            jdbc.execute("CREATE TABLE rrs_securityinfo (wind_code VARCHAR(16), security_type VARCHAR(16),"
                    + "delist_date VARCHAR(8), security_status VARCHAR(1))");
            jdbc.execute("INSERT INTO rrs_securityinfo VALUES ('ACTIVE_BOND','mtn',NULL,'L'),"
                    + "('DELISTED_BOND','mtn','20000101','D'),('D_CERT','crmw',NULL,'D'),('L_CERT','crmw','20000101','L'),"
                    + "('D_FUTURE_CERT','crmw','20991231','D'),('L_FUTURE_CERT','crmw','20991231','L')");
            jdbc.execute("INSERT INTO ip_pool_status_crmw VALUES"
                    + " (18,'ACTIVE_BOND','债券A','mtn','凭证L','L_CERT','NIB','crmw','20',0,'crmw'),"
                    + " (18,'ACTIVE_BOND','债券A','mtn','凭证D','D_CERT','NIB','crmw','20',0,'crmw'),"
                    + " (18,'ACTIVE_BOND','债券A','mtn','凭证D未来摘牌','D_FUTURE_CERT','NIB','crmw','20',0,'crmw'),"
                    + " (18,'DELISTED_BOND','债券B','mtn','凭证L未来摘牌','L_FUTURE_CERT','NIB','crmw','20',0,'crmw')");
            try (PreparedStatement prepared = conn.prepareStatement(bound.getSql().replace(MYSQL_CUTOFF, H2_CUTOFF))) {
                new DefaultParameterHandler(mapped, params, bound).setParameters(prepared);
                try (ResultSet rows = prepared.executeQuery()) {
                    List<String> certificates = new ArrayList<>();
                    while (rows.next()) {
                        certificates.add(rows.getString("crmw_scode"));
                    }
                    assertThat(certificates).containsExactly("D_CERT", "D_FUTURE_CERT", "L_CERT");
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
