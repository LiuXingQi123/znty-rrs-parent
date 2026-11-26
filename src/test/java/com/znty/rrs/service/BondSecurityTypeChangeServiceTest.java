package com.znty.rrs.service;

import com.znty.rrs.entity.schedule.BondSecurityTypeChangeDto;
import com.znty.rrs.mapper.BondSecurityMaintenanceMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 债券类型变更任务测试。 */
public class BondSecurityTypeChangeServiceTest {

    /** 验证普通池与 CRMW 池均同步池状态及当前关联日志。 */
    @Test
    public void executeShouldUpdateBothPoolTablesAndCurrentLogs() {
        BondSecurityMaintenanceMapper mapper = mock(BondSecurityMaintenanceMapper.class);
        BondSecurityTypeChangeService service = new BondSecurityTypeChangeService();
        ReflectionTestUtils.setField(service, "bondSecurityMaintenanceMapper", mapper);
        BondSecurityTypeChangeDto normal = buildRow(1L, 11L, "old_a", "new_a");
        BondSecurityTypeChangeDto crmw = buildRow(2L, 22L, "old_b", "new_b");
        when(mapper.queryPoolSecurityTypeChangeList()).thenReturn(Collections.singletonList(normal));
        when(mapper.queryCrmwPoolSecurityTypeChangeList()).thenReturn(Collections.singletonList(crmw));
        when(mapper.editPoolSecurityType(eq(normal), any(Date.class))).thenReturn(1);
        when(mapper.editCrmwPoolSecurityType(eq(crmw), any(Date.class))).thenReturn(1);
        when(mapper.editAdjustLogSecurityType(eq(11L), eq("new_a"), any(Date.class))).thenReturn(1);
        when(mapper.editAdjustLogSecurityType(eq(22L), eq("new_b"), any(Date.class))).thenReturn(1);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(2);
        assertThat(result.getDetailLog()).contains("任务开始：【债券类型变更（仅债券）】")
                .contains("扫描条件：ip_pool_status/ip_pool_status_crmw.is_deleted=0、audit_status=20")
                .contains("新旧 security_type 均属于 bond 大类且不相等")
                .contains("扫描完成：普通池候选 1 条，CRMW 池候选 1 条")
                .contains("同步成功：表=ip_pool_status，poolStatusId=1")
                .contains("同步成功：表=ip_pool_status_crmw，poolStatusId=2")
                .contains("任务结束（成功）：普通池更新 1 条，CRMW 池更新 1 条，跳过 0 条，补齐调库流水 0 条");
        verify(mapper).editPoolSecurityType(eq(normal), any(Date.class));
        verify(mapper).editCrmwPoolSecurityType(eq(crmw), any(Date.class));
        verify(mapper).editAdjustLogSecurityType(eq(11L), eq("new_a"), any(Date.class));
        verify(mapper).editAdjustLogSecurityType(eq(22L), eq("new_b"), any(Date.class));
    }

    /** 池状态已经一致或已出池时，仍同步已审批通过的调入和调出流水，覆盖 Excel 来源 */
    @Test
    public void executeShouldUpdateApprovedHistoryWithoutPoolCandidates() throws Exception {
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:bond_type_change;MODE=MySQL", "sa", "")));
        configuration.setMapUnderscoreToCamelCase(true);
        String resource = "mapper/BondSecurityMaintenanceMapper.xml";
        try (InputStream xml = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(xml, configuration, resource, configuration.getSqlFragments()).parse();
        }
        try (SqlSession session = new SqlSessionFactoryBuilder().build(configuration).openSession(true);
             Statement sql = session.getConnection().createStatement()) {
            sql.execute("CREATE TABLE rrs_securityinfo (wind_code VARCHAR(32) PRIMARY KEY, security_type VARCHAR(32))");
            sql.execute("CREATE TABLE dict_security_type (security_type VARCHAR(32) PRIMARY KEY, "
                    + "category_type VARCHAR(16), is_deleted INT)");
            sql.execute("CREATE TABLE ip_pool_status (id BIGINT, adjust_log_id BIGINT, security_code VARCHAR(32), "
                    + "security_short_name VARCHAR(128), security_type VARCHAR(32), audit_status VARCHAR(2), is_deleted INT)");
            sql.execute("CREATE TABLE ip_pool_status_crmw AS SELECT * FROM ip_pool_status WHERE 1=0");
            sql.execute("CREATE TABLE ip_adjust_log (id BIGINT PRIMARY KEY, security_code VARCHAR(32), "
                    + "security_type VARCHAR(32), crmw_stype VARCHAR(32), adjust_mode VARCHAR(8), "
                    + "audit_status VARCHAR(2), is_deleted INT, updt_time TIMESTAMP,"
                    + "adjust_type VARCHAR(32), adjust_batch_no VARCHAR(64), adjust_reason VARCHAR(256))");
            sql.execute("INSERT INTO dict_security_type VALUES ('abs','bond',0), ('cp','bond',0), "
                    + "('fund','fund',0), ('retired','bond',1)");
            sql.execute("INSERT INTO rrs_securityinfo VALUES ('IN','cp'), ('OUT','cp'), ('FUND','fund'), ('RETIRED','retired')");
            sql.execute("INSERT INTO ip_pool_status VALUES (41,44,'IN','证券','cp','20',0)");
            sql.execute("INSERT INTO ip_adjust_log (id,security_code,security_type,crmw_stype,adjust_mode,audit_status,is_deleted,updt_time) VALUES "
                    + "(42,'IN','abs',NULL,'调入','20',0,NULL), (44,'IN','cp',NULL,'调入','20',0,NULL), "
                    + "(45,'IN','abs',NULL,'调出','20',0,NULL), (46,'OUT','abs','crmw','调出','20',0,NULL), "
                    + "(47,'IN','abs',NULL,'调入','00',0,NULL), (48,'IN','abs',NULL,'调入','20',1,NULL), "
                    + "(49,'MISSING','abs',NULL,'调出','20',0,NULL), (50,'IN','fund',NULL,'调入','20',0,NULL), "
                    + "(51,'FUND','abs',NULL,'调入','20',0,NULL), (52,'RETIRED','abs',NULL,'调入','20',0,NULL)");
            sql.execute("INSERT INTO ip_adjust_log VALUES "
                    + "(53,'IN','abs',NULL,'调出','20',0,NULL,'Excel导入','EXCEL_1',NULL),"
                    + "(54,'IN','abs',NULL,'调入','20',0,NULL,'互斥调整','EXCEL_1',NULL),"
                    + "(55,'IN','abs',NULL,'调入','20',0,NULL,'自动调整','FOLLOW',"
                    + "'【任务名称：补债】【主体跟随】（【Excel主体跟随】）')");
            BondSecurityTypeChangeService service = new BondSecurityTypeChangeService();
            ReflectionTestUtils.setField(service, "bondSecurityMaintenanceMapper",
                    session.getMapper(BondSecurityMaintenanceMapper.class));

            ScheduledTaskResult result = service.execute();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getAffectedCount()).isZero();
            assertThat(result.getMessage()).contains("补齐 7 条调库流水类型");
            List<String> logTypes = new ArrayList<>();
            try (ResultSet rows = sql.executeQuery("SELECT id, security_type, crmw_stype, updt_time FROM ip_adjust_log ORDER BY id")) {
                while (rows.next()) {
                    logTypes.add(rows.getString("security_type"));
                    long id = rows.getLong("id");
                    assertThat(rows.getTimestamp("updt_time") != null)
                            .isEqualTo(id == 42 || id == 45 || id == 46 || id == 50 || id == 53 || id == 54 || id == 55);
                    if (id == 46) {
                        assertThat(rows.getString("crmw_stype")).isEqualTo("crmw");
                    }
                }
            }
            assertThat(logTypes).containsExactly("cp", "cp", "cp", "cp", "abs", "abs", "abs", "cp", "abs", "abs",
                    "cp", "cp", "cp");
            assertThat(service.execute().getMessage()).contains("补齐 0 条调库流水类型");
        }
    }

    /** 验证同步过程异常时任务返回失败，供事务代理统一回滚本轮修改。 */
    @Test
    public void executeShouldReturnFailureWhenUpdateThrows() {
        BondSecurityMaintenanceMapper mapper = mock(BondSecurityMaintenanceMapper.class);
        BondSecurityTypeChangeService service = new BondSecurityTypeChangeService();
        ReflectionTestUtils.setField(service, "bondSecurityMaintenanceMapper", mapper);
        BondSecurityTypeChangeDto normal = buildRow(1L, 11L, "old_a", "new_a");
        when(mapper.queryPoolSecurityTypeChangeList()).thenReturn(Collections.singletonList(normal));
        when(mapper.queryCrmwPoolSecurityTypeChangeList()).thenReturn(Collections.<BondSecurityTypeChangeDto>emptyList());
        when(mapper.editPoolSecurityType(eq(normal), any(Date.class)))
                .thenThrow(new IllegalStateException("模拟更新异常"));

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("模拟更新异常");
        assertThat(result.getDetailLog()).contains("任务结束（失败）");
    }

    /** 构建类型变更记录。 */
    private BondSecurityTypeChangeDto buildRow(Long poolStatusId, Long adjustLogId,
                                               String oldType, String newType) {
        BondSecurityTypeChangeDto row = new BondSecurityTypeChangeDto();
        row.setPoolStatusId(poolStatusId);
        row.setAdjustLogId(adjustLogId);
        row.setSecurityCode("SEC" + poolStatusId);
        row.setOldSecurityType(oldType);
        row.setNewSecurityType(newType);
        return row;
    }
}
