package com.znty.rrs.service;

import com.znty.rrs.entity.scripttool.ScriptExecuteResultDto;
import com.znty.rrs.entity.scripttool.ScriptTableDto;
import com.znty.rrs.entity.scripttool.ScriptTableGroupDto;
import com.znty.rrs.entity.scripttool.ScriptTaskDto;
import com.znty.rrs.entity.scripttool.ScriptToolReq;
import com.znty.rrs.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 脚本工具服务。
 * <p>仅执行后端白名单内的 SQL 文件和固定清空语句，不接收前端传入的任意 SQL。</p>
 */
@Service
public class ScriptToolService {

    /** 初始化建表任务编码 */
    private static final String TASK_INIT_SCHEMA = "INIT_SCHEMA";
    /** 初始化 Demo 数据任务编码 */
    private static final String TASK_INIT_DEMO = "INIT_DEMO";
    /** 重建完整演示环境任务编码 */
    private static final String TASK_RESET_ALL = "RESET_ALL";
    /** 清空调库运行态数据任务编码 */
    private static final String TASK_CLEAR_ADJUST_FLOW = "CLEAR_ADJUST_FLOW";
    /** 清空选中表数据任务编码 */
    private static final String TASK_CLEAR_SELECTED_TABLES = "CLEAR_SELECTED_TABLES";
    /** 清空选中表确认文本 */
    private static final String CONFIRM_CLEAR_SELECTED_TABLES = "CLEAR_SELECTED_TABLES";
    /** 重置选中表数据任务编码 */
    private static final String TASK_RESET_SELECTED_TABLES = "RESET_SELECTED_TABLES";
    /** 重置选中表确认文本 */
    private static final String CONFIRM_RESET_SELECTED_TABLES = "RESET_SELECTED_TABLES";

    /** 脚本执行状态：成功 */
    private static final String STATUS_SUCCESS = "success";
    /** 脚本执行状态：失败 */
    private static final String STATUS_FAILED = "failed";
    /** USE 数据库语句匹配规则 */
    private static final Pattern USE_DATABASE_PATTERN = Pattern.compile("(?is)^USE\\s+`?([A-Za-z0-9_]+)`?.*");
    /** 数据操作语句目标表匹配规则 */
    private static final Pattern TABLE_STATEMENT_PATTERN = Pattern.compile("(?is)^(?:INSERT\\s+(?:IGNORE\\s+)?INTO|REPLACE\\s+INTO|TRUNCATE\\s+TABLE|DELETE\\s+FROM|UPDATE)\\s+(?:`?([A-Za-z0-9_]+)`?\\.)?`?([A-Za-z0-9_]+)`?.*");

    /** 数据库连接池 */
    @Resource
    private DataSource dataSource;

    /** SQL 文件目录 */
    @Value("${rrs.script.sql-path:sql}")
    private String sqlPath;

    /** 脚本执行互斥标记 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 查询可执行脚本任务列表。
     */
    public List<ScriptTaskDto> queryScriptTaskList(ScriptToolReq req) {
        // 构建任务白名单
        return new ArrayList<>(queryTaskMap().values());
    }

    /**
     * 查询可清空表分组列表。
     */
    public List<ScriptTableGroupDto> queryClearTableGroupList(ScriptToolReq req) {
        // 构建可清空表白名单
        return queryClearTableGroups();
    }

    /**
     * 执行脚本任务。
     */
    public ScriptExecuteResultDto executeScriptTask(ScriptToolReq req) {
        // 获取并校验任务配置
        ScriptTaskDto task = requireTask(req);
        // 校验二次确认文本
        validateConfirmText(req, task);
        if (!running.compareAndSet(false, true)) {
            throw new BizException("已有脚本任务正在执行，请稍后再试");
        }

        ScriptExecuteResultDto result = new ScriptExecuteResultDto();
        result.setTaskCode(task.getTaskCode());
        result.setTaskName(task.getTaskName());
        result.setCurrentUserId(req.getCurrentUserId());
        result.setCurrentUserName(req.getCurrentUserName());
        result.setStartTime(new Date());
        result.setExecutedItems(new ArrayList<String>());

        try {
            // 按任务类型执行白名单脚本或固定清空语句
            executeTask(task.getTaskCode(), result.getExecutedItems());
            result.setStatus(STATUS_SUCCESS);
        } catch (Exception e) {
            result.setStatus(STATUS_FAILED);
            result.setErrorMessage(e.getMessage());
        } finally {
            result.setEndTime(new Date());
            result.setCostMillis(result.getEndTime().getTime() - result.getStartTime().getTime());
            running.set(false);
        }
        return result;
    }

    /**
     * 清空选中的表数据。
     */
    public ScriptExecuteResultDto executeClearSelectedTables(ScriptToolReq req) {
        // 校验自定义清空表请求
        validateSelectedTableReq(req, CONFIRM_CLEAR_SELECTED_TABLES);
        if (!running.compareAndSet(false, true)) {
            throw new BizException("已有脚本任务正在执行，请稍后再试");
        }

        ScriptExecuteResultDto result = new ScriptExecuteResultDto();
        result.setTaskCode(TASK_CLEAR_SELECTED_TABLES);
        result.setTaskName("自定义清空表数据");
        result.setCurrentUserId(req.getCurrentUserId());
        result.setCurrentUserName(req.getCurrentUserName());
        result.setStartTime(new Date());
        result.setExecutedItems(new ArrayList<String>());

        try {
            // 清空选中白名单表
            clearSelectedTables(req.getTableKeys(), result.getExecutedItems());
            result.setStatus(STATUS_SUCCESS);
        } catch (Exception e) {
            result.setStatus(STATUS_FAILED);
            result.setErrorMessage(e.getMessage());
        } finally {
            result.setEndTime(new Date());
            result.setCostMillis(result.getEndTime().getTime() - result.getStartTime().getTime());
            running.set(false);
        }
        return result;
    }

    /**
     * 重置选中的表数据。
     */
    public ScriptExecuteResultDto executeResetSelectedTables(ScriptToolReq req) {
        // 校验自定义重置表请求
        validateSelectedTableReq(req, CONFIRM_RESET_SELECTED_TABLES);
        if (!running.compareAndSet(false, true)) {
            throw new BizException("已有脚本任务正在执行，请稍后再试");
        }

        ScriptExecuteResultDto result = new ScriptExecuteResultDto();
        result.setTaskCode(TASK_RESET_SELECTED_TABLES);
        result.setTaskName("重置选中表数据");
        result.setCurrentUserId(req.getCurrentUserId());
        result.setCurrentUserName(req.getCurrentUserName());
        result.setStartTime(new Date());
        result.setExecutedItems(new ArrayList<String>());

        try {
            // 清空选中白名单表
            clearSelectedTables(req.getTableKeys(), result.getExecutedItems());
            // 执行选中表对应的 Demo 数据语句
            executeSelectedDemoData(req.getTableKeys(), result.getExecutedItems());
            result.setStatus(STATUS_SUCCESS);
        } catch (Exception e) {
            result.setStatus(STATUS_FAILED);
            result.setErrorMessage(e.getMessage());
        } finally {
            result.setEndTime(new Date());
            result.setCostMillis(result.getEndTime().getTime() - result.getStartTime().getTime());
            running.set(false);
        }
        return result;
    }

    /**
     * 获取任务配置。
     */
    private ScriptTaskDto requireTask(ScriptToolReq req) {
        if (req == null || !StringUtils.hasText(req.getTaskCode())) {
            throw new BizException("脚本任务编码不能为空");
        }
        // 构建任务白名单
        ScriptTaskDto task = queryTaskMap().get(req.getTaskCode());
        if (task == null) {
            throw new BizException("不支持的脚本任务：" + req.getTaskCode());
        }
        return task;
    }

    /**
     * 校验确认文本。
     */
    private void validateConfirmText(ScriptToolReq req, ScriptTaskDto task) {
        String confirmText = req == null ? null : req.getConfirmText();
        if (!task.getConfirmText().equals(confirmText)) {
            throw new BizException("确认文本不正确，请输入：" + task.getConfirmText());
        }
    }

    /**
     * 校验自定义清空表请求。
     */
    private void validateSelectedTableReq(ScriptToolReq req, String expectedConfirmText) {
        if (req == null || !expectedConfirmText.equals(req.getConfirmText())) {
            throw new BizException("确认文本不正确，请输入：" + expectedConfirmText);
        }
        if (req.getTableKeys() == null || req.getTableKeys().isEmpty()) {
            throw new BizException("请至少选择一张需要处理的表");
        }
        // 构建可清空表白名单索引
        Map<String, ScriptTableDto> tableMap = queryClearTableMap();
        for (String tableKey : req.getTableKeys()) {
            if (!tableMap.containsKey(tableKey)) {
                throw new BizException("不支持清空的表：" + tableKey);
            }
        }
    }

    /**
     * 执行具体任务。
     */
    private void executeTask(String taskCode, List<String> executedItems) throws Exception {
        if (TASK_INIT_SCHEMA.equals(taskCode)) {
            // 执行全部建表脚本
            executeSqlFiles(querySchemaFiles(), executedItems);
            return;
        }
        if (TASK_INIT_DEMO.equals(taskCode)) {
            // 执行全部 Demo 数据脚本
            executeSqlFiles(queryDemoFiles(), executedItems);
            return;
        }
        if (TASK_RESET_ALL.equals(taskCode)) {
            // 先执行建表脚本
            executeSqlFiles(querySchemaFiles(), executedItems);
            // 再执行 Demo 数据脚本
            executeSqlFiles(queryDemoFiles(), executedItems);
            return;
        }
        if (TASK_CLEAR_ADJUST_FLOW.equals(taskCode)) {
            // 清空调库运行态数据
            clearAdjustFlowData(executedItems);
            return;
        }
        throw new BizException("不支持的脚本任务：" + taskCode);
    }

    /**
     * 执行 SQL 文件列表。
     */
    private void executeSqlFiles(List<String> fileNames, List<String> executedItems) throws Exception {
        // 解析 SQL 文件目录
        File sqlDir = resolveSqlDir();
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            try {
                for (String fileName : fileNames) {
                    File sqlFile = new File(sqlDir, fileName);
                    if (!sqlFile.exists()) {
                        throw new BizException("SQL 文件不存在：" + sqlFile.getAbsolutePath());
                    }
                    String sqlText = new String(Files.readAllBytes(sqlFile.toPath()), StandardCharsets.UTF_8);
                    // 拆分 SQL 文件语句
                    List<String> statements = splitSqlStatements(sqlText);
                    for (String sql : statements) {
                        if (isExecutableStatement(sql)) {
                            stmt.execute(sql);
                        }
                    }
                    executedItems.add(fileName);
                }
            } finally {
                // 重置当前连接默认库
                stmt.execute("USE `znty_rrs`");
            }
        }
    }

    /**
     * 清空调库运行态数据。
     */
    private void clearAdjustFlowData(List<String> executedItems) throws Exception {
        // 查询调库运行态清空表
        List<String> tables = queryAdjustFlowRuntimeTables();
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("USE `znty_rrs`");
            for (String table : tables) {
                stmt.execute("TRUNCATE TABLE `" + table + "`");
                executedItems.add(table);
            }
        }
    }

    /**
     * 清空选中的白名单表。
     */
    private void clearSelectedTables(List<String> tableKeys, List<String> executedItems) throws Exception {
        Set<String> selectedKeySet = new HashSet<>(tableKeys);
        // 构建可清空表白名单索引
        Map<String, ScriptTableDto> tableMap = queryClearTableMap();
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            for (Map.Entry<String, ScriptTableDto> entry : tableMap.entrySet()) {
                if (selectedKeySet.contains(entry.getKey())) {
                    ScriptTableDto table = entry.getValue();
                    stmt.execute("TRUNCATE TABLE `" + table.getDatabaseName() + "`.`" + table.getTableName() + "`");
                    executedItems.add(table.getTableKey());
                }
            }
            // 重置当前连接默认库
            stmt.execute("USE `znty_rrs`");
        }
    }

    /**
     * 执行选中表对应的 Demo 数据语句。
     */
    private void executeSelectedDemoData(List<String> tableKeys, List<String> executedItems) throws Exception {
        Set<String> selectedKeySet = new HashSet<>(tableKeys);
        // 解析 SQL 文件目录
        File sqlDir = resolveSqlDir();
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            try {
                for (String fileName : queryDemoFiles()) {
                    File sqlFile = new File(sqlDir, fileName);
                    if (!sqlFile.exists()) {
                        throw new BizException("SQL 文件不存在：" + sqlFile.getAbsolutePath());
                    }
                    String sqlText = new String(Files.readAllBytes(sqlFile.toPath()), StandardCharsets.UTF_8);
                    // 执行当前 Demo 文件内命中选中表的语句
                    executeSelectedDemoStatements(stmt, splitSqlStatements(sqlText), fileName, selectedKeySet, executedItems);
                }
            } finally {
                // 重置当前连接默认库
                stmt.execute("USE `znty_rrs`");
            }
        }
    }

    /**
     * 执行 Demo 文件中命中选中表的语句。
     */
    private void executeSelectedDemoStatements(Statement stmt, List<String> statements, String fileName,
                                               Set<String> selectedKeySet, List<String> executedItems) throws Exception {
        String currentDatabase = "znty_rrs";
        Set<String> executedTableSet = new HashSet<>();
        for (String sql : statements) {
            if (!isExecutableStatement(sql)) {
                continue;
            }
            // 识别当前 SQL 文件的默认数据库
            String useDatabase = extractUseDatabase(sql);
            if (StringUtils.hasText(useDatabase)) {
                currentDatabase = useDatabase;
                stmt.execute("USE `" + currentDatabase + "`");
                continue;
            }
            // 识别当前语句目标表
            String tableKey = extractStatementTableKey(sql, currentDatabase);
            if (selectedKeySet.contains(tableKey)) {
                stmt.execute("USE `" + currentDatabase + "`");
                stmt.execute(sql);
                if (!executedTableSet.contains(tableKey)) {
                    executedItems.add(fileName + " -> " + tableKey);
                    executedTableSet.add(tableKey);
                }
            }
        }
    }

    /**
     * 提取 USE 语句中的数据库名。
     */
    private String extractUseDatabase(String sql) {
        Matcher matcher = USE_DATABASE_PATTERN.matcher(sql.trim());
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 提取数据操作语句目标表 key。
     */
    private String extractStatementTableKey(String sql, String currentDatabase) {
        Matcher matcher = TABLE_STATEMENT_PATTERN.matcher(sql.trim());
        if (!matcher.matches()) {
            return null;
        }
        String databaseName = StringUtils.hasText(matcher.group(1)) ? matcher.group(1) : currentDatabase;
        String tableName = matcher.group(2);
        return databaseName + "." + tableName;
    }

    /**
     * 解析 SQL 文件目录。
     */
    private File resolveSqlDir() {
        File dir = new File(sqlPath);
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir"), sqlPath);
            if (!dir.exists()) {
                dir = new File(System.getProperty("user.dir"), "znty-rrs-parent/" + sqlPath);
            }
        }
        if (!dir.exists() || !dir.isDirectory()) {
            throw new BizException("SQL 目录不存在：" + dir.getAbsolutePath());
        }
        return dir;
    }

    /**
     * 拆分 SQL 语句，忽略字符串内部分号。
     */
    private List<String> splitSqlStatements(String sqlText) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escape = false;
        for (int i = 0; i < sqlText.length(); i++) {
            char ch = sqlText.charAt(i);
            current.append(ch);
            if (escape) {
                escape = false;
                continue;
            }
            if (ch == '\\') {
                escape = true;
                continue;
            }
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (ch == ';' && !inSingleQuote && !inDoubleQuote) {
                String statement = current.toString();
                current.setLength(0);
                statements.add(statement.substring(0, statement.length() - 1).trim());
            }
        }
        String tail = current.toString().trim();
        if (StringUtils.hasText(tail)) {
            statements.add(tail);
        }
        return statements;
    }

    /**
     * 判断语句是否需要执行。
     */
    private boolean isExecutableStatement(String sql) {
        if (!StringUtils.hasText(sql)) {
            return false;
        }
        String cleaned = sql.replaceAll("(?s)/\\*.*?\\*/", "");
        StringBuilder builder = new StringBuilder();
        String[] lines = cleaned.split("\\r?\\n");
        for (String line : lines) {
            String trimLine = line.trim();
            if (!trimLine.startsWith("--") && StringUtils.hasText(trimLine)) {
                builder.append(trimLine).append('\n');
            }
        }
        return StringUtils.hasText(builder.toString());
    }

    /**
     * 查询建表脚本执行顺序。
     */
    private List<String> querySchemaFiles() {
        return Arrays.asList(
                "ais_inv_analysis_schema.sql",
                "rrs_dict_schema.sql",
                "rrs_security_pool_adjust_schema.sql",
                "rrs_flow_definition_schema.sql",
                "rrs_pool_init_schema.sql",
                "rrs_rule_schema.sql",
                "rrs_sys_attachment_schema.sql",
                "rrs_crmw_pool_status_schema.sql",
                "rrs_my_security_pool_schema.sql",
                "rrs_credit_bond_grade_rule_schema.sql",
                "rrs_temp_security_code_schema.sql"
        );
    }

    /**
     * 查询 Demo 数据脚本执行顺序。
     */
    private List<String> queryDemoFiles() {
        return Arrays.asList(
                "ais_inv_analysis_demo_data.sql",
                "rrs_dict_demo_data.sql",
                "rrs_security_pool_adjust_demo_data.sql",
                "rrs_flow_definition_demo_data.sql",
                "rrs_rule_demo_data.sql",
                "rrs_pool_init_demo_data.sql",
                "rrs_crmw_pool_status_demo_data.sql",
                "rrs_sys_attachment_demo_data.sql",
                "rrs_my_security_pool_demo_data.sql",
                "rrs_credit_bond_grade_rule_demo_data.sql",
                "rrs_temp_security_code_demo_data.sql"
        );
    }

    /**
     * 查询调库运行态清空表。
     */
    private List<String> queryAdjustFlowRuntimeTables() {
        return Arrays.asList(
                "ip_adjust_step",
                "ip_adjust_log",
                "ip_pool_status",
                "ip_pool_status_crmw",
                "sys_attachment",
                "rrs_report_in",
                "rrs_report_out"
        );
    }

    /**
     * 查询可清空表白名单索引。
     */
    private Map<String, ScriptTableDto> queryClearTableMap() {
        Map<String, ScriptTableDto> tableMap = new LinkedHashMap<>();
        // 构建可清空表分组
        for (ScriptTableGroupDto group : queryClearTableGroups()) {
            for (ScriptTableDto table : group.getTables()) {
                tableMap.put(table.getTableKey(), table);
            }
        }
        return tableMap;
    }

    /**
     * 查询可清空表分组。
     */
    private List<ScriptTableGroupDto> queryClearTableGroups() {
        List<ScriptTableGroupDto> groups = new ArrayList<>();
        groups.add(buildTableGroup("security-adjust", "证券池调库运行表", "znty_rrs", Arrays.asList(
                buildTable("znty_rrs", "ip_adjust_step", "调库审批步骤"),
                buildTable("znty_rrs", "ip_adjust_log", "调库申请日志"),
                buildTable("znty_rrs", "ip_pool_status", "证券当前池状态"),
                buildTable("znty_rrs", "ip_pool_status_crmw", "CRMW 当前池状态"),
                buildTable("znty_rrs", "sys_attachment", "系统附件"),
                buildTable("znty_rrs", "rrs_report_in", "入池报告"),
                buildTable("znty_rrs", "rrs_report_out", "出池报告")
        )));
        groups.add(buildTableGroup("security-master", "证券与临时代码", "znty_rrs", Arrays.asList(
                buildTable("znty_rrs", "rrs_securityinfo", "证券主数据"),
                buildTable("znty_rrs", "rrs_temp_security_code", "临时代码")
        )));
        groups.add(buildTableGroup("pool-config", "投资池配置", "znty_rrs", Arrays.asList(
                buildTable("znty_rrs", "ip_pool_permission_evt", "投资池权限事件"),
                buildTable("znty_rrs", "ip_pool_auto_rule_evt", "投资池自动规则事件"),
                buildTable("znty_rrs", "ip_pool_relation_evt", "投资池关系事件"),
                buildTable("znty_rrs", "ip_investment_pool_evt", "投资池事件"),
                buildTable("znty_rrs", "ip_pool_permission", "投资池权限"),
                buildTable("znty_rrs", "ip_pool_auto_rule", "投资池自动规则"),
                buildTable("znty_rrs", "ip_pool_relation", "投资池关系"),
                buildTable("znty_rrs", "ip_investment_pool", "投资池")
        )));
        groups.add(buildTableGroup("flow-config", "流程定义", "znty_rrs", Arrays.asList(
                buildTable("znty_rrs", "wf_edge_cond_rule_evt", "条件边规则事件"),
                buildTable("znty_rrs", "wf_edge_cond_rule", "条件边规则"),
                buildTable("znty_rrs", "wf_flow_edge_evt", "流程连线事件"),
                buildTable("znty_rrs", "wf_flow_edge", "流程连线"),
                buildTable("znty_rrs", "wf_node_condition_config_evt", "条件节点配置事件"),
                buildTable("znty_rrs", "wf_node_condition_config", "条件节点配置"),
                buildTable("znty_rrs", "wf_node_notify_config_evt", "通知节点配置事件"),
                buildTable("znty_rrs", "wf_node_notify_config", "通知节点配置"),
                buildTable("znty_rrs", "wf_node_auto_config_evt", "自动节点配置事件"),
                buildTable("znty_rrs", "wf_node_auto_config", "自动节点配置"),
                buildTable("znty_rrs", "wf_node_approval_handler_evt", "审批处理人事件"),
                buildTable("znty_rrs", "wf_node_approval_handler", "审批处理人"),
                buildTable("znty_rrs", "wf_node_approval_config_evt", "审批节点配置事件"),
                buildTable("znty_rrs", "wf_node_approval_config", "审批节点配置"),
                buildTable("znty_rrs", "wf_flow_node_evt", "流程节点事件"),
                buildTable("znty_rrs", "wf_flow_node", "流程节点"),
                buildTable("znty_rrs", "wf_flow_version_evt", "流程版本事件"),
                buildTable("znty_rrs", "wf_flow_version", "流程版本"),
                buildTable("znty_rrs", "wf_flow_definition_evt", "流程定义事件"),
                buildTable("znty_rrs", "wf_flow_definition", "流程定义"),
                buildTable("znty_rrs", "wf_role_dict_evt", "流程角色字典事件"),
                buildTable("znty_rrs", "wf_role_dict", "流程角色字典")
        )));
        groups.add(buildTableGroup("rule-config", "规则管理", "znty_rrs", Arrays.asList(
                buildTable("znty_rrs", "rule_test_run_log", "规则测试运行日志"),
                buildTable("znty_rrs", "rule_test_run", "规则测试运行"),
                buildTable("znty_rrs", "rule_test_case_param", "规则测试用例参数"),
                buildTable("znty_rrs", "rule_test_case", "规则测试用例"),
                buildTable("znty_rrs", "rule_param_option", "规则参数选项"),
                buildTable("znty_rrs", "rule_param", "规则参数"),
                buildTable("znty_rrs", "rule_definition", "规则定义"),
                buildTable("znty_rrs", "rule_preset_option_item", "规则预设选项明细"),
                buildTable("znty_rrs", "rule_preset_option_set", "规则预设选项集"),
                buildTable("znty_rrs", "rule_category", "规则分类")
        )));
        groups.add(buildTableGroup("dict-grade", "字典与评级规则", "znty_rrs", Arrays.asList(
                buildTable("znty_rrs", "dict_security_type", "证券类型字典"),
                buildTable("znty_rrs", "credit_bond_pool_grade_rule", "信用债大库评级准入规则"),
                buildTable("znty_rrs", "credit_bond_inner_rating_grade", "主体内评分档"),
                buildTable("znty_rrs", "credit_bond_term_bucket", "期限分组"),
                buildTable("znty_rrs", "my_security_pool", "我的证券池")
        )));
        groups.add(buildTableGroup("ais-analysis", "AIS 投资分析库", "ais_inv_analysis", Arrays.asList(
                buildTable("ais_inv_analysis", "t_inv_grade_result", "主体评级结果"),
                buildTable("ais_inv_analysis", "t_inv_company", "投资分析主体"),
                buildTable("ais_inv_analysis", "t_sys_user_role", "用户角色关系"),
                buildTable("ais_inv_analysis", "t_sys_user", "用户"),
                buildTable("ais_inv_analysis", "t_sys_role", "角色")
        )));
        return groups;
    }

    /**
     * 构建可清空表分组。
     */
    private ScriptTableGroupDto buildTableGroup(String groupCode, String groupName, String databaseName, List<ScriptTableDto> tables) {
        ScriptTableGroupDto group = new ScriptTableGroupDto();
        group.setGroupCode(groupCode);
        group.setGroupName(groupName);
        group.setDatabaseName(databaseName);
        group.setTables(tables);
        return group;
    }

    /**
     * 构建可清空表信息。
     */
    private ScriptTableDto buildTable(String databaseName, String tableName, String tableDesc) {
        ScriptTableDto table = new ScriptTableDto();
        table.setTableKey(databaseName + "." + tableName);
        table.setDatabaseName(databaseName);
        table.setTableName(tableName);
        table.setTableDesc(tableDesc);
        return table;
    }

    /**
     * 构建任务白名单。
     */
    private Map<String, ScriptTaskDto> queryTaskMap() {
        Map<String, ScriptTaskDto> taskMap = new LinkedHashMap<>();
        // 加入建表初始化任务
        addTask(taskMap, TASK_INIT_SCHEMA, "初始化建表脚本", "按固定顺序执行 sql 目录下全部 schema 脚本，重建表结构。", "high", "INIT_SCHEMA",
                "会 DROP 并重新 CREATE 相关表结构，原表数据会被清空。", buildSchemaTaskItems());
        // 加入 Demo 数据初始化任务
        addTask(taskMap, TASK_INIT_DEMO, "初始化 Demo 数据", "按固定顺序执行 sql 目录下全部 demo 数据脚本。", "medium", "INIT_DEMO",
                "会按脚本内 TRUNCATE 逻辑重置演示数据。", buildDemoTaskItems());
        // 加入完整重建任务
        addTask(taskMap, TASK_RESET_ALL, "重建完整演示环境", "先执行全部 schema 脚本，再执行全部 demo 数据脚本。", "danger", "RESET_ALL",
                "会重建表结构并重置全部演示数据。", buildResetAllTaskItems());
        // 加入调库运行态清空任务
        addTask(taskMap, TASK_CLEAR_ADJUST_FLOW, "清空调库流程数据", "只清空调库申请、步骤、当前池状态、附件和报告等运行态数据。", "danger", "CLEAR_ADJUST_FLOW",
                "不清空证券主数据、投资池、流程定义、规则和字典配置。", buildAdjustFlowRuntimeTaskItems());
        return taskMap;
    }

    /**
     * 构建建表任务展示项。
     */
    private List<String> buildSchemaTaskItems() {
        // 查询建表脚本执行顺序
        return querySchemaFiles();
    }

    /**
     * 构建 Demo 数据任务展示项。
     */
    private List<String> buildDemoTaskItems() {
        // 查询 Demo 数据脚本执行顺序
        return queryDemoFiles();
    }

    /**
     * 构建完整重建任务展示项。
     */
    private List<String> buildResetAllTaskItems() {
        // 合并建表脚本和 Demo 数据脚本
        return mergeList(querySchemaFiles(), queryDemoFiles());
    }

    /**
     * 构建调库运行态清空任务展示项。
     */
    private List<String> buildAdjustFlowRuntimeTaskItems() {
        // 查询调库运行态清空表
        return queryAdjustFlowRuntimeTables();
    }

    /**
     * 加入任务配置。
     */
    private void addTask(Map<String, ScriptTaskDto> taskMap, String taskCode, String taskName, String description,
                         String riskLevel, String confirmText, String affectScope, List<String> items) {
        ScriptTaskDto dto = new ScriptTaskDto();
        dto.setTaskCode(taskCode);
        dto.setTaskName(taskName);
        dto.setDescription(description);
        dto.setRiskLevel(riskLevel);
        dto.setConfirmText(confirmText);
        dto.setAffectScope(affectScope);
        dto.setItems(new ArrayList<>(items));
        taskMap.put(taskCode, dto);
    }

    /**
     * 合并列表。
     */
    private List<String> mergeList(List<String> first, List<String> second) {
        List<String> result = new ArrayList<>();
        result.addAll(first);
        result.addAll(second);
        return result;
    }
}
