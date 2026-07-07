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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 脚本工具服务测试。
 */
public class ScriptToolServiceTest {

    /** 验证结构差异检查能够解析全部 59 张项目表。 */
    @Test
    public void shouldParseAllSchemaTables() {
        ScriptToolService service = new ScriptToolService();
        ReflectionTestUtils.setField(service, "sqlPath", "sql");

        // 解析全部建表脚本中的期望结构
        Map<?, ?> tables = ReflectionTestUtils.invokeMethod(service, "queryExpectedSchemaTables");
        // 查询环境检查复用的项目表白名单
        Map<?, ?> healthTables = ReflectionTestUtils.invokeMethod(service, "queryClearTableMap");

        assertEquals(60, tables.size());
        assertEquals(tables.keySet(), healthTables.keySet());
    }

    /** 验证重置选中表时会执行带前置注释的 Demo 插入语句。 */
    @Test
    public void shouldExecuteCommentedDemoInsertForSelectedTable() throws Exception {
        ScriptToolService service = new ScriptToolService();
        Statement statement = mock(Statement.class);
        List<String> statements = Arrays.asList(
                "-- 选择数据库\nUSE `znty_rrs`",
                "-- 恢复调库日志演示数据\nINSERT INTO `ip_adjust_log` (`id`) VALUES (1)",
                "-- 未选中的证券主数据\nINSERT INTO `rrs_securityinfo` (`id`) VALUES (1)"
        );
        Set<String> selectedTableKeys = new HashSet<>(Collections.singletonList("znty_rrs.ip_adjust_log"));
        List<String> executedItems = new ArrayList<>();

        // 执行选中表对应的 Demo 数据语句
        ReflectionTestUtils.invokeMethod(service, "executeSelectedDemoStatements", statement, statements,
                "rrs_security_pool_adjust_demo_data.sql", selectedTableKeys, executedItems);

        verify(statement, atLeastOnce()).execute("USE `znty_rrs`");
        verify(statement).execute("INSERT INTO `ip_adjust_log` (`id`) VALUES (1)");
        verify(statement, never()).execute("INSERT INTO `rrs_securityinfo` (`id`) VALUES (1)");
        assertEquals(Collections.singletonList(
                "rrs_security_pool_adjust_demo_data.sql -> znty_rrs.ip_adjust_log"), executedItems);
    }
}
