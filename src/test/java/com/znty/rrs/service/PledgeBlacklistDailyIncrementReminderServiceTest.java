package com.znty.rrs.service;

import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.schedule.BondReminderDto;
import com.znty.rrs.mapper.BondReminderMapper;
import com.znty.rrs.mapper.ExcelPoolExclusionMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.junit.Assume;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 质押黑名单库每日增量提醒测试。 */
public class PledgeBlacklistDailyIncrementReminderServiceTest {

    /** 验证原增量候选按流水 ID 过滤后才汇总待发送明细 */
    @Test
    public void executeShouldScanIncrementAndKeepDeliveryPending() {
        BondReminderMapper reminderMapper = mock(BondReminderMapper.class);
        ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
        PledgeBlacklistDailyIncrementReminderService service =
                new PledgeBlacklistDailyIncrementReminderService();
        ReflectionTestUtils.setField(service, "bondReminderMapper", reminderMapper);
        ExcelPoolExclusionService exclusions = mock(ExcelPoolExclusionService.class);
        ReflectionTestUtils.setField(service, "excelPoolExclusionService", exclusions);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "reminderDeliveryService", new ScheduledReminderDeliveryService());
        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setParamJson("{\"poolIds\":[17]}");
        Date previous = new Date(System.currentTimeMillis() - 60000L);
        BondReminderDto row = new BondReminderDto();
        row.setRecordId(1L);
        row.setSecurityCode("SEC001");
        row.setSecurityShortName("测试债");
        BondReminderDto excelRow = new BondReminderDto();
        excelRow.setRecordId(2L);
        excelRow.setSecurityCode("SEC001");
        excelRow.setSecurityShortName("导入来源流水");
        when(taskMapper.queryTaskByCode(PledgeBlacklistDailyIncrementReminderService.TASK_CODE))
                .thenReturn(conf);
        when(taskMapper.queryLastSuccessStartTime(PledgeBlacklistDailyIncrementReminderService.TASK_CODE))
                .thenReturn(previous);
        when(reminderMapper.queryPledgeBlacklistIncrementList(
                eq(Collections.singletonList(17L)), eq(previous), any(Date.class)))
                .thenReturn(Arrays.asList(row, excelRow));
        when(exclusions.loadExcelOriginAdjustLogIds(new LinkedHashSet<>(Arrays.asList(1L, 2L))))
                .thenReturn(Collections.singleton(2L));

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        assertThat(result.getMessage()).contains("通知通道未接入");
        assertThat(result.getDetailLog()).contains("任务开始：【质押黑名单库每日增量提醒】")
                .contains("扫描条件：ip_adjust_log.is_deleted=0、audit_status=20、target_pool_id IN [17]")
                .contains("债券须关联证券主档、状态不为 D，且摘牌日为空或晚于今日")
                .contains("排除 Excel 来源调库流水 1 条")
                .contains("扫描完成：增量提醒候选 1 条")
                .contains("消息投递状态=待接入")
                .contains("任务结束（成功）：候选 1 条，通知状态=待接入")
                .doesNotContain("导入来源流水");
        verify(reminderMapper).queryPledgeBlacklistIncrementList(
                eq(Collections.singletonList(17L)), eq(previous), any(Date.class));
    }

    /** 原查询返回完整候选，共用 Mapper 另按 ID 识别导入、配套及跟随流水 */
    @Test
    public void originalIncrementQueryShouldKeepCandidatesAndSourceLookupShouldMarkExcelRows() throws Exception {
        Assume.assumeTrue("H2 2.3.232 需 Java 11 以上运行时",
                !System.getProperty("java.specification.version").startsWith("1."));
        Configuration config = new Configuration();
        for (String resource : Arrays.asList("mapper/BondReminderMapper.xml", "mapper/ExcelPoolExclusionMapper.xml")) {
            try (InputStream xml = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(xml, config, resource, config.getSqlFragments()).parse();
            }
        }
        Method method = BondReminderMapper.class.getMethod("queryPledgeBlacklistIncrementList", List.class, Date.class, Date.class);
        MappedStatement statement = config.getMappedStatement(BondReminderMapper.class.getName() + "." + method.getName());
        Object args = new ParamNameResolver(config, method).getNamedParams(new Object[]{
                Collections.singletonList(17L), Timestamp.valueOf("2026-11-26 00:00:00"), Timestamp.valueOf("2026-11-27 00:00:00")
        });
        BoundSql bound = statement.getBoundSql(args);
        try (Connection database = DriverManager.getConnection("jdbc:h2:mem:;MODE=MySQL");
             Statement sql = database.createStatement()) {
            sql.execute("CREATE TABLE dict_security_type (security_type VARCHAR(20), category_type VARCHAR(20), is_deleted INT)");
            sql.execute("CREATE TABLE rrs_securityinfo (wind_code VARCHAR(20), issuer_code VARCHAR(20),"
                    + " issuer VARCHAR(20), security_status VARCHAR(2), delist_date VARCHAR(8))");
            sql.execute("CREATE TABLE ip_adjust_log (id BIGINT, security_code VARCHAR(20), security_short_name VARCHAR(20),"
                    + " security_type VARCHAR(20), target_pool_id BIGINT, target_pool_name VARCHAR(20), adjust_mode VARCHAR(10),"
                    + " audit_time TIMESTAMP, is_deleted INT, audit_status VARCHAR(2), adjust_type VARCHAR(32),"
                    + " adjust_batch_no VARCHAR(32), adjust_reason VARCHAR(128))");
            sql.execute("INSERT INTO dict_security_type VALUES ('company','company',0)");
            sql.execute("INSERT INTO ip_adjust_log (id,security_code,security_type,target_pool_id,adjust_mode,is_deleted,"
                    + "audit_status,adjust_type,adjust_batch_no,adjust_reason) VALUES "
                    + "(1,'A','company',17,'调入',0,'20','手工调整','MANUAL',NULL),"
                    + "(2,'A','company',17,'调出',0,'20','Excel导入','EXCEL',NULL),"
                    + "(3,'A','company',17,'调入',0,'20','联动调整','EXCEL',NULL),"
                    + "(4,'A','company',17,'调入',0,'20','自动调整','FOLLOW',"
                    + "'【任务名称：补债】【主体跟随】（【Excel主体跟随】）'),"
                    + "(5,'A','company',17,'调出',0,'20','自动调整','FOLLOW_OUT','（依据：主体Excel调出（主体依据池 [17]））'),"
                    + "(6,'A','company',17,'调入',0,'20','手工调整','MANUAL_6',NULL),"
                    + "(7,'A','company',17,'调出',0,'20','Excel导入','EARLIER_EXCEL',NULL)");
            sql.execute("UPDATE ip_adjust_log SET audit_time=TIMESTAMP '2026-11-26 09:00:00'");
            sql.execute("UPDATE ip_adjust_log SET audit_time=TIMESTAMP '2026-11-25 09:00:00' WHERE id=7");
            String h2Sql = bound.getSql().replace("DATE_FORMAT(CURDATE(), '%Y%m%d')", "FORMATDATETIME(CURDATE(), 'yyyyMMdd')");
            List<Long> candidateIds = new ArrayList<>();
            try (PreparedStatement query = database.prepareStatement(h2Sql)) {
                new DefaultParameterHandler(statement, args, bound).setParameters(query);
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        candidateIds.add(rows.getLong("record_id"));
                    }
                }
            }
            assertThat(candidateIds).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
            MappedStatement lookup = config.getMappedStatement(
                    ExcelPoolExclusionMapper.class.getName() + ".queryExcelOriginAdjustLogIds");
            Object lookupArgs = Collections.singletonMap("adjustLogIds", candidateIds);
            BoundSql lookupSql = lookup.getBoundSql(lookupArgs);
            try (PreparedStatement query = database.prepareStatement(lookupSql.getSql())) {
                new DefaultParameterHandler(lookup, lookupArgs, lookupSql).setParameters(query);
                try (ResultSet rows = query.executeQuery()) {
                    List<Long> excludedIds = new ArrayList<>();
                    while (rows.next()) {
                        excludedIds.add(rows.getLong("id"));
                    }
                    assertThat(excludedIds).containsExactlyInAnyOrder(2L, 3L, 4L, 5L);
                }
            }
        }
    }
}
