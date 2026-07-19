package com.znty.rrs.service;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 脚本工具服务测试。
 */
public class ScriptToolServiceTest {

    /** 验证结构差异检查能够解析项目关键表。 */
    @Test
    public void shouldParseAllSchemaTables() {
        ScriptToolService service = new ScriptToolService();
        ReflectionTestUtils.setField(service, "sqlPath", "sql");

        // 解析全部建表脚本中的期望结构
        Map<?, ?> tables = ReflectionTestUtils.invokeMethod(service, "queryExpectedSchemaTables");
        // 查询环境检查复用的项目表白名单
        Map<?, ?> healthTables = ReflectionTestUtils.invokeMethod(service, "queryClearTableMap");

        assertTrue(tables.containsKey("znty_rrs.rrs_securityinfo"));
        assertTrue(tables.containsKey("znty_rrs.rrs_temp_security_code"));
        assertTrue(tables.containsKey("znty_rrs.ip_adjust_log"));
        assertEquals(tables.keySet(), healthTables.keySet());
    }

    /** 验证主库批量任务排除外部导入表，且 AIS/外部导入拆为独立任务。 */
    @Test
    public void shouldExcludeExternalImportAndSplitAisTasks() {
        ScriptToolService service = new ScriptToolService();
        ReflectionTestUtils.setField(service, "sqlPath", "sql");

        // 构建数据初始化任务白名单
        Map<?, ?> taskMap = ReflectionTestUtils.invokeMethod(service, "queryTaskMap");
        Object initSchema = taskMap.get("INIT_SCHEMA");
        Object initDemo = taskMap.get("INIT_DEMO");
        Object resetAll = taskMap.get("RESET_ALL");
        Object externalImportSchema = taskMap.get("INIT_EXTERNAL_IMPORT_SCHEMA");
        Object externalImportDemo = taskMap.get("INIT_EXTERNAL_IMPORT_DEMO");
        Object aisSchema = taskMap.get("INIT_AIS_SCHEMA");
        Object aisDemo = taskMap.get("INIT_AIS_DEMO");
        Object clearFlow = taskMap.get("CLEAR_ADJUST_FLOW");

        assertTrue(initSchema != null);
        assertTrue(initDemo != null);
        assertTrue(resetAll != null);
        assertTrue(externalImportSchema != null);
        assertTrue(externalImportDemo != null);
        assertTrue(aisSchema != null);
        assertTrue(aisDemo != null);
        assertTrue(clearFlow != null);
        assertTrue(taskMap.get("INIT_AIS") == null);
        assertTrue(taskMap.get("INIT_SECURITYINFO_SCHEMA") == null);
        // 完整重建任务排在列表首位，便于前端通栏展示
        assertEquals("RESET_ALL", taskMap.keySet().iterator().next());

        @SuppressWarnings("unchecked")
        List<String> schemaItems = (List<String>) ReflectionTestUtils.getField(initSchema, "items");
        @SuppressWarnings("unchecked")
        List<String> demoItems = (List<String>) ReflectionTestUtils.getField(initDemo, "items");
        @SuppressWarnings("unchecked")
        List<String> resetItems = (List<String>) ReflectionTestUtils.getField(resetAll, "items");
        @SuppressWarnings("unchecked")
        List<String> excluded = (List<String>) ReflectionTestUtils.getField(initSchema, "excludedItems");
        @SuppressWarnings("unchecked")
        List<String> externalSchemaItems = (List<String>) ReflectionTestUtils.getField(externalImportSchema, "items");
        Integer schemaTableCount = (Integer) ReflectionTestUtils.getField(initSchema, "tableCount");
        Integer clearTableCount = (Integer) ReflectionTestUtils.getField(clearFlow, "tableCount");
        Integer externalImportTableCount = (Integer) ReflectionTestUtils.getField(externalImportSchema, "tableCount");

        assertTrue(!schemaItems.contains("rrs_external_import_schema.sql"));
        assertTrue(!demoItems.contains("rrs_external_import_demo_data.sql"));
        assertTrue(!demoItems.contains("ais_inv_analysis_demo_data.sql"));
        assertTrue(!demoItems.contains("ais_inv_ods_demo_data.sql"));
        assertTrue(!resetItems.contains("rrs_external_import_schema.sql"));
        assertTrue(!resetItems.contains("rrs_external_import_demo_data.sql"));
        assertTrue(!resetItems.contains("ais_inv_analysis_demo_data.sql"));
        assertTrue(!resetItems.contains("ais_inv_ods_demo_data.sql"));
        assertTrue(externalSchemaItems.contains("rrs_external_import_schema.sql"));
        // 主库批量任务「已排除」须标出 AIS 与外部导入脚本，便于页面展示
        assertTrue(excluded.contains("ais_inv_analysis_demo_data.sql"));
        assertTrue(excluded.contains("ais_inv_ods_demo_data.sql"));
        assertTrue(excluded.contains("ais_inv_analysis_schema.sql"));
        assertTrue(excluded.contains("ais_inv_ods_schema.sql"));
        assertTrue(excluded.contains("rrs_external_import_schema.sql"));
        assertTrue(excluded.contains("rrs_external_import_demo_data.sql"));
        @SuppressWarnings("unchecked")
        List<String> resetExcluded = (List<String>) ReflectionTestUtils.getField(resetAll, "excludedItems");
        assertTrue(resetExcluded.contains("ais_inv_analysis_demo_data.sql"));
        assertTrue(resetExcluded.contains("ais_inv_ods_demo_data.sql"));
        // 主库 schema 受影响表 = CREATE TABLE 去重（含流程/池事件表等），不是脚本文件数 10
        assertTrue(schemaTableCount != null && schemaTableCount > 0);
        assertEquals(Integer.valueOf(55), schemaTableCount);
        assertEquals(Integer.valueOf(1), externalImportTableCount);
        assertEquals(Integer.valueOf(7), clearTableCount);
        assertEquals(10, schemaItems.size());
    }

    /** 验证重置选中表时会执行带前置注释的 Demo 插入语句。 */
    @Test
    public void shouldExecuteCommentedDemoInsertForSelectedTable() throws Exception {
        ScriptToolService service = new ScriptToolService();
        Statement statement = mock(Statement.class);
        List<String> statements = Arrays.asList(
                "-- 选择数据库\nUSE `znty_rrs`",
                "-- 恢复调库日志演示数据\nINSERT INTO `ip_adjust_log` (`id`) VALUES (1)",
                "-- 未选中的证券主数据\nINSERT INTO `rrs_securityinfo` (`wind_code`) VALUES ('TMP001')"
        );
        Set<String> selectedTableKeys = new HashSet<>(Collections.singletonList("znty_rrs.ip_adjust_log"));
        List<String> executedItems = new ArrayList<>();

        // 执行选中表对应的 Demo 数据语句
        ReflectionTestUtils.invokeMethod(service, "executeSelectedDemoStatements", statement, statements,
                "rrs_security_pool_adjust_demo_data.sql", selectedTableKeys, executedItems);

        verify(statement, atLeastOnce()).execute("USE `znty_rrs`");
        verify(statement).execute("INSERT INTO `ip_adjust_log` (`id`) VALUES (1)");
        verify(statement, never()).execute("INSERT INTO `rrs_securityinfo` (`wind_code`) VALUES ('TMP001')");
        assertEquals(Collections.singletonList(
                "rrs_security_pool_adjust_demo_data.sql -> znty_rrs.ip_adjust_log"), executedItems);
    }
}
