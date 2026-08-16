package com.znty.rrs.service;

import com.znty.rrs.entity.scripttool.ScriptToolReq;
import com.znty.rrs.exception.BizException;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 脚本工具服务测试。
 */
public class ScriptToolServiceTest {

    /** 验证结构差异检查能够解析项目关键表，且与可清空表白名单一致。 */
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
        assertTrue(tables.containsKey("znty_rrs.ip_adjust_security_snapshot"));
        assertTrue(tables.containsKey("znty_rrs.ip_adjust_security_snapshot_crmw"));
        assertEquals(tables.keySet(), healthTables.keySet());
    }

    /** 验证 sql/ 目录业务脚本均已注册到 schema/demo 白名单（排除只读工具脚本）。 */
    @Test
    public void shouldRegisterAllSqlFilesExceptToolScripts() {
        ScriptToolService service = new ScriptToolService();
        ReflectionTestUtils.setField(service, "sqlPath", "sql");

        @SuppressWarnings("unchecked")
        List<String> schemaFiles = (List<String>) ReflectionTestUtils.invokeMethod(service, "querySchemaFiles");
        @SuppressWarnings("unchecked")
        List<String> demoFiles = (List<String>) ReflectionTestUtils.invokeMethod(service, "queryDemoFiles");
        @SuppressWarnings("unchecked")
        List<String> excluded = (List<String>) ReflectionTestUtils.invokeMethod(service, "querySqlFilesExcludedFromRegistration");

        Set<String> registered = new HashSet<>();
        registered.addAll(schemaFiles);
        registered.addAll(demoFiles);
        registered.addAll(excluded);

        File sqlDir = new File("sql");
        assertTrue("sql 目录应存在: " + sqlDir.getAbsolutePath(), sqlDir.isDirectory());
        File[] files = sqlDir.listFiles((dir, name) -> name != null && name.endsWith(".sql"));
        assertTrue(files != null && files.length > 0);
        for (File file : files) {
            assertTrue("未注册到 ScriptTool 白名单: " + file.getName(), registered.contains(file.getName()));
        }
        assertTrue(schemaFiles.contains("rrs_adjust_snapshot_schema.sql"));
        assertTrue(schemaFiles.contains("rrs_grade_rule_alert_schema.sql"));
        assertTrue(schemaFiles.contains("rrs_scheduled_task_schema.sql"));
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
        @SuppressWarnings("unchecked")
        List<String> unseededTables = (List<String>) ReflectionTestUtils.getField(initDemo, "unseededTables");
        @SuppressWarnings("unchecked")
        List<String> clearItems = (List<String>) ReflectionTestUtils.getField(clearFlow, "items");

        assertTrue(!schemaItems.contains("rrs_external_import_schema.sql"));
        assertTrue(!demoItems.contains("rrs_external_import_demo_data.sql"));
        assertTrue(!demoItems.contains("ais_inv_analysis_demo_data.sql"));
        assertTrue(!demoItems.contains("ais_inv_ods_demo_data.sql"));
        assertTrue(!resetItems.contains("rrs_external_import_schema.sql"));
        assertTrue(!resetItems.contains("rrs_external_import_demo_data.sql"));
        assertTrue(!resetItems.contains("ais_inv_analysis_demo_data.sql"));
        assertTrue(!resetItems.contains("ais_inv_ods_demo_data.sql"));
        assertTrue(schemaItems.contains("rrs_adjust_snapshot_schema.sql"));
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
        // 表数/文件数随脚本增减变化，只断言与清单动态一致，避免硬编码漂移
        assertTrue(schemaTableCount != null && schemaTableCount > 0);
        assertEquals(Integer.valueOf(1), externalImportTableCount);
        assertEquals(Integer.valueOf(clearItems.size()), clearTableCount);
        assertTrue(clearItems.contains("ip_adjust_security_snapshot"));
        assertTrue(clearItems.contains("ip_adjust_security_snapshot_crmw"));
        assertEquals(schemaItems.size(), ((List<?>) ReflectionTestUtils.invokeMethod(service, "queryRrsSchemaFiles")).size());
        // 初始化 Demo 数据任务须标注仅建结构、未灌 demo 数据的表（导入临时表 + 快照等）
        assertTrue(unseededTables.contains("sys_imp_tmp"));
        assertTrue(unseededTables.contains("sys_imp_tmp_detl"));
        assertTrue(unseededTables.contains("ip_adjust_security_snapshot"));
        assertTrue(unseededTables.contains("ip_adjust_security_snapshot_crmw"));
        assertTrue(unseededTables.size() >= 2);
    }

    /** 验证 Demo 场景清单覆盖主要业务链路。 */
    @Test
    public void shouldExposeExpandedDemoScenes() {
        ScriptToolService service = new ScriptToolService();
        @SuppressWarnings("unchecked")
        Map<String, ?> sceneMap = (Map<String, ?>) ReflectionTestUtils.invokeMethod(service, "queryDemoSceneMap");
        assertTrue(sceneMap.size() >= 14);
        assertTrue(sceneMap.containsKey("security-pending-review"));
        assertTrue(sceneMap.containsKey("security-final-reject"));
        assertTrue(sceneMap.containsKey("security-outbound-approved"));
        assertTrue(sceneMap.containsKey("security-withdrawn"));
        assertTrue(sceneMap.containsKey("forbidden-company-pending"));
        assertTrue(sceneMap.containsKey("forbidden-observe-pending"));
        assertTrue(sceneMap.containsKey("forbidden-restricted-pending"));
        assertTrue(sceneMap.containsKey("forbidden-company-approved"));
        assertTrue(sceneMap.containsKey("forbidden-abs-pending"));
        assertTrue(sceneMap.containsKey("crmw-pending-review"));
        assertTrue(sceneMap.containsKey("crmw-approved-history"));
        assertTrue(sceneMap.containsKey("grade-rule-alert-pending"));
        @SuppressWarnings("unchecked")
        List<String> forbiddenSql = (List<String>) ReflectionTestUtils.invokeMethod(service, "buildDemoSceneStatements", "forbidden-company-pending");
        assertTrue(forbiddenSql.toString().contains("company:forbidden-inbound"));
        assertTrue(forbiddenSql.toString().contains("11303"));
    }

    /** 验证模块编码/名称映射覆盖新模块。 */
    @Test
    public void shouldResolveModuleNamesForNewModules() {
        ScriptToolService service = new ScriptToolService();
        assertEquals("scheduled-task", ReflectionTestUtils.invokeMethod(service, "resolveModuleCode", "rrs_scheduled_task_schema.sql"));
        assertEquals("定时任务配置", ReflectionTestUtils.invokeMethod(service, "resolveModuleName", "rrs_scheduled_task_demo_data.sql"));
        assertEquals("import-temp", ReflectionTestUtils.invokeMethod(service, "resolveModuleCode", "rrs_import_temp_schema.sql"));
        assertEquals("Excel 导入临时表", ReflectionTestUtils.invokeMethod(service, "resolveModuleName", "rrs_import_temp_schema.sql"));
        assertEquals("adjust-snapshot", ReflectionTestUtils.invokeMethod(service, "resolveModuleCode", "rrs_adjust_snapshot_schema.sql"));
        assertEquals("调库信息快照", ReflectionTestUtils.invokeMethod(service, "resolveModuleName", "rrs_adjust_snapshot_schema.sql"));
    }

    /** 验证关闭开关后写操作被拒绝。 */
    @Test
    public void shouldRejectWriteWhenScriptToolDisabled() {
        ScriptToolService service = new ScriptToolService();
        ReflectionTestUtils.setField(service, "scriptEnabled", Boolean.FALSE);
        ScriptToolReq req = new ScriptToolReq();
        req.setTaskCode("INIT_SCHEMA");
        req.setConfirmText("INIT_SCHEMA");
        try {
            service.executeScriptTask(req);
            org.junit.Assert.fail("关闭开关后应拒绝执行");
        } catch (BizException e) {
            assertTrue(e.getMessage().contains("脚本工具已禁用"));
        }
        assertFalse(service.isScriptToolEnabled());
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
