package com.znty.rrs.service;

import com.znty.rrs.entity.scripttool.ScriptExecuteResultDto;
import com.znty.rrs.entity.scripttool.ScriptDemoSceneDto;
import com.znty.rrs.entity.scripttool.ScriptFileDto;
import com.znty.rrs.entity.scripttool.ScriptHealthCheckDto;
import com.znty.rrs.entity.scripttool.ScriptHealthItemDto;
import com.znty.rrs.entity.scripttool.ScriptModuleTaskDto;
import com.znty.rrs.entity.scripttool.ScriptOverviewDto;
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
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
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
    /** 模块级重置确认文本 */
    private static final String CONFIRM_RESET_MODULE = "RESET_MODULE";
    /** Demo 场景生成确认文本 */
    private static final String CONFIRM_GENERATE_DEMO_SCENE = "GENERATE_DEMO_SCENE";

    /** 脚本执行状态：成功 */
    private static final String STATUS_SUCCESS = "success";
    /** 脚本执行状态：失败 */
    private static final String STATUS_FAILED = "failed";
    /** 健康检查状态：警告 */
    private static final String STATUS_WARNING = "warning";
    /** USE 数据库语句匹配规则 */
    private static final Pattern USE_DATABASE_PATTERN = Pattern.compile("(?is)^USE\\s+`?([A-Za-z0-9_]+)`?.*");
    /** 数据操作语句目标表匹配规则 */
    private static final Pattern TABLE_STATEMENT_PATTERN = Pattern.compile("(?is)^(?:INSERT\\s+(?:IGNORE\\s+)?INTO|REPLACE\\s+INTO|TRUNCATE\\s+TABLE|DELETE\\s+FROM|UPDATE)\\s+(?:`?([A-Za-z0-9_]+)`?\\.)?`?([A-Za-z0-9_]+)`?.*");
    /** 建表语句目标表匹配规则 */
    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile("(?is)^CREATE\\s+TABLE\\s+`?([A-Za-z0-9_]+)`?.*");
    /** 清表语句目标表匹配规则 */
    private static final Pattern TRUNCATE_TABLE_PATTERN = Pattern.compile("(?is)^TRUNCATE\\s+TABLE\\s+`?([A-Za-z0-9_]+)`?.*");
    /** 插入语句目标表匹配规则 */
    private static final Pattern INSERT_TABLE_PATTERN = Pattern.compile("(?is)^INSERT\\s+(?:IGNORE\\s+)?INTO\\s+`?([A-Za-z0-9_]+)`?.*");

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
     * 查询 SQL 脚本清单与依赖总览。
     */
    public ScriptOverviewDto queryScriptOverview(ScriptToolReq req) {
        ScriptOverviewDto overview = new ScriptOverviewDto();
        // 解析 SQL 文件目录
        File sqlDir = resolveSqlDir();
        overview.setSqlPath(sqlDir.getAbsolutePath());
        overview.setSchemaFileCount(querySchemaFiles().size());
        overview.setDemoFileCount(queryDemoFiles().size());
        overview.setFiles(new ArrayList<ScriptFileDto>());

        int missingCount = 0;
        int sortOrder = 1;
        for (String fileName : querySchemaFiles()) {
            // 解析建表脚本文件信息
            ScriptFileDto file = buildScriptFile(sqlDir, fileName, "schema", sortOrder++);
            if (!file.getExists()) {
                missingCount++;
            }
            overview.getFiles().add(file);
        }
        for (String fileName : queryDemoFiles()) {
            // 解析 Demo 脚本文件信息
            ScriptFileDto file = buildScriptFile(sqlDir, fileName, "demo", sortOrder++);
            if (!file.getExists()) {
                missingCount++;
            }
            overview.getFiles().add(file);
        }
        overview.setMissingFileCount(missingCount);
        return overview;
    }

    /**
     * 查询脚本工具环境健康检查结果。
     */
    public ScriptHealthCheckDto queryHealthCheck(ScriptToolReq req) {
        ScriptHealthCheckDto check = new ScriptHealthCheckDto();
        check.setItems(new ArrayList<ScriptHealthItemDto>());
        try {
            // 解析 SQL 文件目录
            File sqlDir = resolveSqlDir();
            check.setSqlPath(sqlDir.getAbsolutePath());
            check.getItems().add(buildHealthItem("sql-path", "SQL 目录", STATUS_SUCCESS, sqlDir.getAbsolutePath(), "目录存在", "SQL 目录可读取"));
        } catch (Exception e) {
            check.setSqlPath(sqlPath);
            check.getItems().add(buildHealthItem("sql-path", "SQL 目录", STATUS_FAILED, sqlPath, "目录存在", e.getMessage()));
        }

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            check.setConnectionInfo(metaData.getURL());
            check.getItems().add(buildHealthItem("database-connection", "数据库连接", STATUS_SUCCESS, metaData.getURL(), "可连接", "数据库连接正常"));
            // 检查数据库和表状态
            appendDatabaseHealthItems(conn, check.getItems());
        } catch (Exception e) {
            check.setConnectionInfo("");
            check.getItems().add(buildHealthItem("database-connection", "数据库连接", STATUS_FAILED, "", "可连接", e.getMessage()));
        }
        check.setStatus(resolveHealthStatus(check.getItems()));
        return check;
    }

    /**
     * 查询模块级重置任务列表。
     */
    public List<ScriptModuleTaskDto> queryModuleResetTaskList(ScriptToolReq req) {
        // 构建模块级重置任务白名单
        return new ArrayList<>(queryModuleTaskMap().values());
    }

    /**
     * 执行模块级重置任务。
     */
    public ScriptExecuteResultDto executeModuleResetTask(ScriptToolReq req) {
        // 获取并校验模块重置任务
        ScriptModuleTaskDto task = requireModuleTask(req);
        // 校验模块重置确认文本
        validateFixedConfirmText(req, CONFIRM_RESET_MODULE);
        if (!running.compareAndSet(false, true)) {
            throw new BizException("已有脚本任务正在执行，请稍后再试");
        }

        ScriptExecuteResultDto result = buildExecuteResult(task.getModuleCode(), task.getModuleName(), req);
        try {
            // 执行模块对应 Demo 脚本
            executeSqlFiles(Arrays.asList(task.getDemoFileName()), result.getExecutedItems());
            result.setStatus(STATUS_SUCCESS);
        } catch (Exception e) {
            result.setStatus(STATUS_FAILED);
            result.setErrorMessage(e.getMessage());
        } finally {
            fillExecuteResultEnd(result);
            running.set(false);
        }
        return result;
    }

    /**
     * 查询 Demo 场景列表。
     */
    public List<ScriptDemoSceneDto> queryDemoSceneList(ScriptToolReq req) {
        // 构建 Demo 场景白名单
        return new ArrayList<>(queryDemoSceneMap().values());
    }

    /**
     * 执行 Demo 场景生成。
     */
    public ScriptExecuteResultDto executeDemoScene(ScriptToolReq req) {
        // 获取并校验 Demo 场景
        ScriptDemoSceneDto scene = requireDemoScene(req);
        // 校验 Demo 场景确认文本
        validateFixedConfirmText(req, CONFIRM_GENERATE_DEMO_SCENE);
        if (!running.compareAndSet(false, true)) {
            throw new BizException("已有脚本任务正在执行，请稍后再试");
        }

        ScriptExecuteResultDto result = buildExecuteResult(scene.getSceneCode(), scene.getSceneName(), req);
        try {
            // 校验场景基础数据依赖
            validateDemoSceneBaseData();
            // 生成固定 Demo 场景数据
            executeDemoSceneSql(scene.getSceneCode(), result.getExecutedItems());
            result.setStatus(STATUS_SUCCESS);
        } catch (Exception e) {
            result.setStatus(STATUS_FAILED);
            result.setErrorMessage(e.getMessage());
        } finally {
            fillExecuteResultEnd(result);
            running.set(false);
        }
        return result;
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
     * 校验固定确认文本。
     */
    private void validateFixedConfirmText(ScriptToolReq req, String expectedConfirmText) {
        String confirmText = req == null ? null : req.getConfirmText();
        if (!expectedConfirmText.equals(confirmText)) {
            throw new BizException("确认文本不正确，请输入：" + expectedConfirmText);
        }
    }

    /**
     * 获取模块重置任务配置。
     */
    private ScriptModuleTaskDto requireModuleTask(ScriptToolReq req) {
        if (req == null || !StringUtils.hasText(req.getModuleCode())) {
            throw new BizException("模块编码不能为空");
        }
        // 构建模块级重置任务白名单
        ScriptModuleTaskDto task = queryModuleTaskMap().get(req.getModuleCode());
        if (task == null) {
            throw new BizException("不支持的模块重置任务：" + req.getModuleCode());
        }
        return task;
    }

    /**
     * 获取 Demo 场景配置。
     */
    private ScriptDemoSceneDto requireDemoScene(ScriptToolReq req) {
        if (req == null || !StringUtils.hasText(req.getSceneCode())) {
            throw new BizException("Demo 场景编码不能为空");
        }
        // 构建 Demo 场景白名单
        ScriptDemoSceneDto scene = queryDemoSceneMap().get(req.getSceneCode());
        if (scene == null) {
            throw new BizException("不支持的 Demo 场景：" + req.getSceneCode());
        }
        return scene;
    }

    /**
     * 构建脚本执行结果。
     */
    private ScriptExecuteResultDto buildExecuteResult(String taskCode, String taskName, ScriptToolReq req) {
        ScriptExecuteResultDto result = new ScriptExecuteResultDto();
        result.setTaskCode(taskCode);
        result.setTaskName(taskName);
        result.setCurrentUserId(req == null ? null : req.getCurrentUserId());
        result.setCurrentUserName(req == null ? null : req.getCurrentUserName());
        result.setStartTime(new Date());
        result.setExecutedItems(new ArrayList<String>());
        return result;
    }

    /**
     * 回填脚本执行结束信息。
     */
    private void fillExecuteResultEnd(ScriptExecuteResultDto result) {
        result.setEndTime(new Date());
        result.setCostMillis(result.getEndTime().getTime() - result.getStartTime().getTime());
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
            // 清理语句前置注释，确保能够识别 USE 和目标表
            String executableSql = normalizeExecutableSql(sql);
            if (!StringUtils.hasText(executableSql)) {
                continue;
            }
            // 识别当前 SQL 文件的默认数据库
            String useDatabase = extractUseDatabase(executableSql);
            if (StringUtils.hasText(useDatabase)) {
                currentDatabase = useDatabase;
                stmt.execute("USE `" + currentDatabase + "`");
                continue;
            }
            // 识别当前语句目标表
            String tableKey = extractStatementTableKey(executableSql, currentDatabase);
            if (selectedKeySet.contains(tableKey)) {
                stmt.execute("USE `" + currentDatabase + "`");
                stmt.execute(executableSql);
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
        // 清理 SQL 注释后判断是否仍有可执行内容
        return StringUtils.hasText(normalizeExecutableSql(sql));
    }

    /**
     * 清理 SQL 注释并返回可匹配语句。
     */
    private String normalizeExecutableSql(String sql) {
        String cleaned = sql.replaceAll("(?s)/\\*.*?\\*/", "");
        StringBuilder builder = new StringBuilder();
        String[] lines = cleaned.split("\\r?\\n");
        for (String line : lines) {
            String trimLine = line.trim();
            if (!trimLine.startsWith("--") && StringUtils.hasText(trimLine)) {
                builder.append(trimLine).append('\n');
            }
        }
        return builder.toString().trim();
    }

    /**
     * 构建脚本文件信息。
     */
    private ScriptFileDto buildScriptFile(File sqlDir, String fileName, String scriptType, int sortOrder) {
        ScriptFileDto dto = new ScriptFileDto();
        dto.setFileName(fileName);
        dto.setScriptType(scriptType);
        dto.setSortOrder(sortOrder);
        dto.setModuleCode(resolveModuleCode(fileName));
        dto.setModuleName(resolveModuleName(fileName));
        dto.setCreateCount(0);
        dto.setTruncateCount(0);
        dto.setInsertCount(0);
        dto.setAffectedTables(new ArrayList<String>());

        File sqlFile = new File(sqlDir, fileName);
        dto.setExists(sqlFile.exists());
        dto.setFileSize(sqlFile.exists() ? sqlFile.length() : 0L);
        if (!sqlFile.exists()) {
            return dto;
        }
        try {
            String sqlText = new String(Files.readAllBytes(sqlFile.toPath()), StandardCharsets.UTF_8);
            // 解析脚本内可执行语句
            fillScriptFileStatementInfo(dto, splitSqlStatements(sqlText));
        } catch (Exception e) {
            dto.setExists(false);
        }
        return dto;
    }

    /**
     * 回填脚本语句统计信息。
     */
    private void fillScriptFileStatementInfo(ScriptFileDto dto, List<String> statements) {
        Set<String> tableSet = new LinkedHashSet<>();
        int createCount = 0;
        int truncateCount = 0;
        int insertCount = 0;
        for (String sql : statements) {
            if (!isExecutableStatement(sql)) {
                continue;
            }
            String matchSql = normalizeExecutableSql(sql);
            Matcher createMatcher = CREATE_TABLE_PATTERN.matcher(matchSql);
            if (createMatcher.matches()) {
                createCount++;
                tableSet.add(createMatcher.group(1));
            }
            Matcher truncateMatcher = TRUNCATE_TABLE_PATTERN.matcher(matchSql);
            if (truncateMatcher.matches()) {
                truncateCount++;
                tableSet.add(truncateMatcher.group(1));
            }
            Matcher insertMatcher = INSERT_TABLE_PATTERN.matcher(matchSql);
            if (insertMatcher.matches()) {
                insertCount++;
                tableSet.add(insertMatcher.group(1));
            }
        }
        dto.setCreateCount(createCount);
        dto.setTruncateCount(truncateCount);
        dto.setInsertCount(insertCount);
        dto.setAffectedTables(new ArrayList<>(tableSet));
    }

    /**
     * 追加数据库健康检查项。
     */
    private void appendDatabaseHealthItems(Connection conn, List<ScriptHealthItemDto> items) {
        // 检查核心数据库
        appendDatabaseItem(conn, items, "znty_rrs");
        appendDatabaseItem(conn, items, "ais_inv_analysis");
        // 检查核心表
        appendTableItem(conn, items, "znty_rrs", "rrs_securityinfo");
        appendTableItem(conn, items, "znty_rrs", "ip_investment_pool");
        appendTableItem(conn, items, "znty_rrs", "wf_flow_version");
        appendTableItem(conn, items, "znty_rrs", "ip_adjust_log");
        appendTableItem(conn, items, "znty_rrs", "ip_adjust_step");
        appendTableItem(conn, items, "ais_inv_analysis", "t_sys_user");
    }

    /**
     * 追加数据库存在性检查项。
     */
    private void appendDatabaseItem(Connection conn, List<ScriptHealthItemDto> items, String databaseName) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '" + databaseName + "'")) {
            if (rs.next()) {
                String charset = rs.getString(1);
                String collation = rs.getString(2);
                String status = "utf8mb4".equalsIgnoreCase(charset) && "utf8mb4_0900_ai_ci".equalsIgnoreCase(collation) ? STATUS_SUCCESS : STATUS_WARNING;
                items.add(buildHealthItem("database-" + databaseName, "数据库 " + databaseName, status,
                        charset + " / " + collation, "utf8mb4 / utf8mb4_0900_ai_ci", "数据库存在"));
            } else {
                items.add(buildHealthItem("database-" + databaseName, "数据库 " + databaseName, STATUS_FAILED,
                        "不存在", "存在", "数据库不存在，请先执行建库建表脚本"));
            }
        } catch (Exception e) {
            items.add(buildHealthItem("database-" + databaseName, "数据库 " + databaseName, STATUS_FAILED,
                    "", "可查询", e.getMessage()));
        }
    }

    /**
     * 追加表存在性和数据量检查项。
     */
    private void appendTableItem(Connection conn, List<ScriptHealthItemDto> items, String databaseName, String tableName) {
        String tableKey = databaseName + "." + tableName;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '" + databaseName + "' AND TABLE_NAME = '" + tableName + "'")) {
            if (!rs.next() || rs.getLong(1) == 0L) {
                items.add(buildHealthItem("table-" + tableKey, "核心表 " + tableKey, STATUS_FAILED,
                        "不存在", "存在", "核心表不存在"));
                return;
            }
        } catch (Exception e) {
            items.add(buildHealthItem("table-" + tableKey, "核心表 " + tableKey, STATUS_FAILED,
                    "", "可查询", e.getMessage()));
            return;
        }

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM `" + databaseName + "`.`" + tableName + "`")) {
            long count = rs.next() ? rs.getLong(1) : 0L;
            String status = count > 0L ? STATUS_SUCCESS : STATUS_WARNING;
            items.add(buildHealthItem("table-" + tableKey, "核心表 " + tableKey, status,
                    String.valueOf(count), "大于等于 0", count > 0L ? "表存在且有数据" : "表存在但暂无数据"));
        } catch (Exception e) {
            items.add(buildHealthItem("table-" + tableKey, "核心表 " + tableKey, STATUS_FAILED,
                    "", "可统计", e.getMessage()));
        }
    }

    /**
     * 构建健康检查项。
     */
    private ScriptHealthItemDto buildHealthItem(String itemCode, String itemName, String status,
                                                String currentValue, String expectedValue, String message) {
        ScriptHealthItemDto item = new ScriptHealthItemDto();
        item.setItemCode(itemCode);
        item.setItemName(itemName);
        item.setStatus(status);
        item.setCurrentValue(currentValue);
        item.setExpectedValue(expectedValue);
        item.setMessage(message);
        return item;
    }

    /**
     * 计算健康检查整体状态。
     */
    private String resolveHealthStatus(List<ScriptHealthItemDto> items) {
        boolean hasWarning = false;
        for (ScriptHealthItemDto item : items) {
            if (STATUS_FAILED.equals(item.getStatus())) {
                return STATUS_FAILED;
            }
            if (STATUS_WARNING.equals(item.getStatus())) {
                hasWarning = true;
            }
        }
        return hasWarning ? STATUS_WARNING : STATUS_SUCCESS;
    }

    /**
     * 解析脚本所属模块编码。
     */
    private String resolveModuleCode(String fileName) {
        if (fileName.startsWith("ais_inv_analysis")) return "ais-analysis";
        if (fileName.startsWith("rrs_dict")) return "dict";
        if (fileName.startsWith("rrs_security_pool_adjust")) return "security-adjust";
        if (fileName.startsWith("rrs_flow_definition")) return "flow-definition";
        if (fileName.startsWith("rrs_rule")) return "rule-config";
        if (fileName.startsWith("rrs_pool_init")) return "pool-config";
        if (fileName.startsWith("rrs_crmw_pool_status")) return "crmw-status";
        if (fileName.startsWith("rrs_sys_attachment")) return "attachment-report";
        if (fileName.startsWith("rrs_my_security_pool")) return "my-security-pool";
        if (fileName.startsWith("rrs_credit_bond_grade_rule")) return "credit-bond-grade";
        if (fileName.startsWith("rrs_temp_security_code")) return "temp-security-code";
        return "unknown";
    }

    /**
     * 解析脚本所属模块名称。
     */
    private String resolveModuleName(String fileName) {
        String moduleCode = resolveModuleCode(fileName);
        if ("ais-analysis".equals(moduleCode)) return "AIS 投资分析库";
        if ("dict".equals(moduleCode)) return "字典数据";
        if ("security-adjust".equals(moduleCode)) return "证券调库演示数据";
        if ("flow-definition".equals(moduleCode)) return "流程定义";
        if ("rule-config".equals(moduleCode)) return "规则配置";
        if ("pool-config".equals(moduleCode)) return "投资池配置";
        if ("crmw-status".equals(moduleCode)) return "CRMW 当前池";
        if ("attachment-report".equals(moduleCode)) return "附件与报告";
        if ("my-security-pool".equals(moduleCode)) return "我的证券池";
        if ("credit-bond-grade".equals(moduleCode)) return "信用债评级规则";
        if ("temp-security-code".equals(moduleCode)) return "临时代码";
        return "未识别模块";
    }

    /**
     * 查询模块级重置任务白名单。
     */
    private Map<String, ScriptModuleTaskDto> queryModuleTaskMap() {
        Map<String, ScriptModuleTaskDto> taskMap = new LinkedHashMap<>();
        addModuleTask(taskMap, "dict", "字典数据", "重置证券类型等基础字典数据。", "medium", "rrs_dict_demo_data.sql");
        addModuleTask(taskMap, "security-adjust", "证券调库演示数据", "重置证券主数据、调库日志、池状态和流程步骤演示数据。", "danger", "rrs_security_pool_adjust_demo_data.sql");
        addModuleTask(taskMap, "flow-definition", "流程定义", "重置流程定义、版本、节点、连线和审批处理人配置。", "danger", "rrs_flow_definition_demo_data.sql");
        addModuleTask(taskMap, "rule-config", "规则配置", "重置规则分类、规则定义、参数、测试用例和测试运行日志。", "danger", "rrs_rule_demo_data.sql");
        addModuleTask(taskMap, "pool-config", "投资池配置", "重置投资池、投资池关系、自动规则和权限配置。", "danger", "rrs_pool_init_demo_data.sql");
        addModuleTask(taskMap, "crmw-status", "CRMW 当前池", "重置 CRMW 当前池状态演示数据。", "medium", "rrs_crmw_pool_status_demo_data.sql");
        addModuleTask(taskMap, "attachment-report", "附件与报告", "重置系统附件、入池报告和出池报告演示数据。", "medium", "rrs_sys_attachment_demo_data.sql");
        addModuleTask(taskMap, "my-security-pool", "我的证券池", "重置我的证券池演示数据。", "medium", "rrs_my_security_pool_demo_data.sql");
        addModuleTask(taskMap, "credit-bond-grade", "信用债评级规则", "重置信用债主体内评分档和评级准入规则。", "medium", "rrs_credit_bond_grade_rule_demo_data.sql");
        addModuleTask(taskMap, "temp-security-code", "临时代码", "重置临时代码管理演示数据。", "medium", "rrs_temp_security_code_demo_data.sql");
        addModuleTask(taskMap, "ais-analysis", "AIS 投资分析库", "重置 AIS 主体评级、用户、角色和用户角色关系演示数据。", "danger", "ais_inv_analysis_demo_data.sql");
        return taskMap;
    }

    /**
     * 加入模块重置任务。
     */
    private void addModuleTask(Map<String, ScriptModuleTaskDto> taskMap, String moduleCode, String moduleName,
                               String description, String riskLevel, String demoFileName) {
        ScriptModuleTaskDto task = new ScriptModuleTaskDto();
        task.setModuleCode(moduleCode);
        task.setModuleName(moduleName);
        task.setDescription(description);
        task.setRiskLevel(riskLevel);
        task.setConfirmText(CONFIRM_RESET_MODULE);
        task.setDemoFileName(demoFileName);
        task.setAffectedTables(queryDemoFileAffectedTables(demoFileName));
        taskMap.put(moduleCode, task);
    }

    /**
     * 查询 Demo 文件影响表。
     */
    private List<String> queryDemoFileAffectedTables(String demoFileName) {
        File sqlDir;
        try {
            // 解析 SQL 文件目录
            sqlDir = resolveSqlDir();
        } catch (Exception e) {
            return new ArrayList<String>();
        }
        // 构建脚本文件信息
        ScriptFileDto file = buildScriptFile(sqlDir, demoFileName, "demo", 1);
        return file.getAffectedTables();
    }

    /**
     * 查询 Demo 场景白名单。
     */
    private Map<String, ScriptDemoSceneDto> queryDemoSceneMap() {
        Map<String, ScriptDemoSceneDto> sceneMap = new LinkedHashMap<>();
        addDemoScene(sceneMap, "security-pending-review", "证券池待复核调库单", "生成一条停留在研究员复核节点的证券池调库申请。",
                Arrays.asList("znty_rrs.ip_adjust_log", "znty_rrs.ip_adjust_step"));
        addDemoScene(sceneMap, "security-approved-history", "证券池审批通过历史", "生成一条审批通过并已落地当前池状态的证券池调库历史。",
                Arrays.asList("znty_rrs.ip_adjust_log", "znty_rrs.ip_adjust_step", "znty_rrs.ip_pool_status"));
        addDemoScene(sceneMap, "security-reject-modify", "证券池驳回待修改", "生成一条审批驳回后回到发起人修改节点的证券池调库申请。",
                Arrays.asList("znty_rrs.ip_adjust_log", "znty_rrs.ip_adjust_step"));
        addDemoScene(sceneMap, "forbidden-company-pending", "禁投池主体待审批", "生成一条主体进入禁投池并等待审批的申请。",
                Arrays.asList("znty_rrs.ip_adjust_log", "znty_rrs.ip_adjust_step"));
        addDemoScene(sceneMap, "crmw-pending-review", "CRMW 待复核调库单", "生成一条 CRMW 调库待复核申请。",
                Arrays.asList("znty_rrs.ip_adjust_log", "znty_rrs.ip_adjust_step"));
        return sceneMap;
    }

    /**
     * 加入 Demo 场景。
     */
    private void addDemoScene(Map<String, ScriptDemoSceneDto> sceneMap, String sceneCode, String sceneName,
                              String description, List<String> affectedTables) {
        ScriptDemoSceneDto scene = new ScriptDemoSceneDto();
        scene.setSceneCode(sceneCode);
        scene.setSceneName(sceneName);
        scene.setDescription(description);
        scene.setRiskLevel("medium");
        scene.setConfirmText(CONFIRM_GENERATE_DEMO_SCENE);
        scene.setAffectedTables(affectedTables);
        scene.setDependencies(Arrays.asList("znty_rrs.rrs_securityinfo", "znty_rrs.ip_investment_pool", "znty_rrs.wf_flow_version"));
        sceneMap.put(sceneCode, scene);
    }

    /**
     * 校验 Demo 场景基础数据。
     */
    private void validateDemoSceneBaseData() throws Exception {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            if (queryTableCount(stmt, "znty_rrs", "rrs_securityinfo") == 0L
                    || queryTableCount(stmt, "znty_rrs", "ip_investment_pool") == 0L
                    || queryTableCount(stmt, "znty_rrs", "wf_flow_version") == 0L) {
                throw new BizException("基础 Demo 数据不存在，请先初始化 Demo 数据");
            }
        }
    }

    /**
     * 查询表数据量。
     */
    private long queryTableCount(Statement stmt, String databaseName, String tableName) throws Exception {
        try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM `" + databaseName + "`.`" + tableName + "`")) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    /**
     * 执行 Demo 场景 SQL。
     */
    private void executeDemoSceneSql(String sceneCode, List<String> executedItems) throws Exception {
        List<String> statements = buildDemoSceneStatements(sceneCode);
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("USE `znty_rrs`");
            for (String sql : statements) {
                stmt.execute(sql);
            }
        }
        executedItems.add(sceneCode);
    }

    /**
     * 构建 Demo 场景 SQL 语句。
     */
    private List<String> buildDemoSceneStatements(String sceneCode) {
        if ("security-pending-review".equals(sceneCode)) {
            return buildSecurityPendingReviewScene();
        }
        if ("security-approved-history".equals(sceneCode)) {
            return buildSecurityApprovedHistoryScene();
        }
        if ("security-reject-modify".equals(sceneCode)) {
            return buildSecurityRejectModifyScene();
        }
        if ("forbidden-company-pending".equals(sceneCode)) {
            return buildForbiddenCompanyPendingScene();
        }
        if ("crmw-pending-review".equals(sceneCode)) {
            return buildCrmwPendingReviewScene();
        }
        throw new BizException("不支持的 Demo 场景：" + sceneCode);
    }

    /**
     * 构建证券池待复核场景。
     */
    private List<String> buildSecurityPendingReviewScene() {
        String batchNo = "SCENE_SECURITY_PENDING_REVIEW";
        List<String> statements = buildSceneCleanupStatements(batchNo);
        statements.add("INSERT INTO `ip_adjust_log` (`id`, `security_code`, `security_short_name`, `security_type`, `adjust_type`, `adjust_mode`, `adjust_batch_no`, `target_pool_id`, `target_pool_name`, `pool_type`, `flow_id`, `flow_key`, `flow_type`, `audit_status`, `adjuster_id`, `adjuster_name`, `adjust_reason`, `adjust_advice`, `submit_time`, `audit_time`, `entry_time`, `is_deleted`, `crte_time`, `updt_time`) VALUES (900001, '104004567', '23某银行二级资本债', 'commercial_bank_bond', '手工调整', '调入', '" + batchNo + "', 3, '二级库', 'credit_bond', 101, 'bond:standard-inbound', 'bond', '00', '2', '研究员1', '脚本工具生成：证券池待复核调库单', NULL, NOW(), NULL, NULL, 0, NOW(), NOW())");
        statements.add("INSERT INTO `ip_adjust_step` (`adjust_log_id`, `adjust_batch_no`, `flow_node_id`, `node_code`, `node_label`, `node_type`, `approval_strategy`, `sort_order`, `step_status`, `handler_id`, `handler_name`, `process_action`, `process_comment`, `start_time`, `process_time`, `crte_time`, `updt_time`) VALUES (900001, '" + batchNo + "', 10101, 'n1', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', NULL, NOW(), NOW(), NOW(), NOW()), (900001, '" + batchNo + "', 10102, 'n2', '研究员A发起', 'approval', 'initiator', 2, 'submit', '2', '研究员1', 'submit', '脚本工具生成待复核场景', NOW(), NOW(), NOW(), NOW()), (900001, '" + batchNo + "', 10103, 'n3', '研究员B复核', 'approval', 'preempt', 3, 'pending', '3', '研究员2', NULL, NULL, NOW(), NULL, NOW(), NOW())");
        return statements;
    }

    /**
     * 构建证券池审批通过历史场景。
     */
    private List<String> buildSecurityApprovedHistoryScene() {
        String batchNo = "SCENE_SECURITY_APPROVED_HISTORY";
        List<String> statements = buildSceneCleanupStatements(batchNo);
        statements.add("INSERT INTO `ip_adjust_log` (`id`, `security_code`, `security_short_name`, `security_type`, `adjust_type`, `adjust_mode`, `adjust_batch_no`, `target_pool_id`, `target_pool_name`, `pool_type`, `flow_id`, `flow_key`, `flow_type`, `audit_status`, `adjuster_id`, `adjuster_name`, `adjust_reason`, `adjust_advice`, `submit_time`, `audit_time`, `entry_time`, `is_deleted`, `crte_time`, `updt_time`) VALUES (900002, '103003456', '24某能源MTN001', 'medium_term_note', '手工调整', '调入', '" + batchNo + "', 2, '一级库', 'credit_bond', 101, 'bond:standard-inbound', 'bond', '20', '1', '管理员', '脚本工具生成：证券池审批通过历史', '审批通过，场景数据已落地', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0, NOW(), NOW())");
        statements.add("INSERT INTO `ip_pool_status` (`security_code`, `security_short_name`, `security_type`, `adjust_type`, `adjust_mode`, `adjust_log_id`, `adjust_batch_no`, `target_pool_id`, `target_pool_name`, `pool_type`, `flow_id`, `flow_key`, `flow_type`, `audit_status`, `adjuster_id`, `adjuster_name`, `adjust_reason`, `adjust_advice`, `submit_time`, `audit_time`, `entry_time`, `is_deleted`, `crte_time`, `updt_time`) VALUES ('103003456', '24某能源MTN001', 'medium_term_note', '手工调整', '调入', 900002, '" + batchNo + "', 2, '一级库', 'credit_bond', 101, 'bond:standard-inbound', 'bond', '20', '1', '管理员', '脚本工具生成：证券池审批通过历史', '审批通过，场景数据已落地', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0, NOW(), NOW())");
        statements.add("INSERT INTO `ip_adjust_step` (`adjust_log_id`, `adjust_batch_no`, `flow_node_id`, `node_code`, `node_label`, `node_type`, `approval_strategy`, `sort_order`, `step_status`, `handler_id`, `handler_name`, `process_action`, `process_comment`, `start_time`, `process_time`, `crte_time`, `updt_time`) VALUES (900002, '" + batchNo + "', 10101, 'n1', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), NOW()), (900002, '" + batchNo + "', 10102, 'n2', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '脚本工具生成审批通过历史', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), NOW()), (900002, '" + batchNo + "', 10103, 'n3', '研究员B复核', 'approval', 'preempt', 3, 'approve', '3', '研究员2', 'approve', '复核通过', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NOW()), (900002, '" + batchNo + "', 10107, 'n7', '结束', 'end', NULL, 7, 'auto_process', NULL, NULL, 'auto_process', NULL, NOW(), NOW(), NOW(), NOW())");
        return statements;
    }

    /**
     * 构建证券池驳回待修改场景。
     */
    private List<String> buildSecurityRejectModifyScene() {
        String batchNo = "SCENE_SECURITY_REJECT_MODIFY";
        List<String> statements = buildSceneCleanupStatements(batchNo);
        statements.add("INSERT INTO `ip_adjust_log` (`id`, `security_code`, `security_short_name`, `security_type`, `adjust_type`, `adjust_mode`, `adjust_batch_no`, `target_pool_id`, `target_pool_name`, `pool_type`, `flow_id`, `flow_key`, `flow_type`, `audit_status`, `adjuster_id`, `adjuster_name`, `adjust_reason`, `adjust_advice`, `submit_time`, `audit_time`, `entry_time`, `is_deleted`, `crte_time`, `updt_time`) VALUES (900003, '102002345', '23某城投债', 'company_bond', '手工调整', '调入', '" + batchNo + "', 4, '三级库', 'credit_bond', 101, 'bond:standard-inbound', 'bond', '11', '5', '研究员4', '脚本工具生成：证券池驳回待修改', '材料不完整，请补充后重新提交', DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NULL, 0, NOW(), NOW())");
        statements.add("INSERT INTO `ip_adjust_step` (`adjust_log_id`, `adjust_batch_no`, `flow_node_id`, `node_code`, `node_label`, `node_type`, `approval_strategy`, `sort_order`, `step_status`, `handler_id`, `handler_name`, `process_action`, `process_comment`, `start_time`, `process_time`, `crte_time`, `updt_time`) VALUES (900003, '" + batchNo + "', 10101, 'n1', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NOW()), (900003, '" + batchNo + "', 10102, 'n2', '研究员A发起', 'approval', 'initiator', 2, 'submit', '5', '研究员4', 'submit', '提交调库申请', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NOW()), (900003, '" + batchNo + "', 10103, 'n3', '研究员B复核', 'approval', 'preempt', 3, 'reject', '3', '研究员2', 'reject', '材料不完整，驳回修改', NOW(), NOW(), NOW(), NOW()), (900003, '" + batchNo + "', 10104, 'n4', '研究员A修改', 'approval', 'initiator', 4, 'pending', '5', '研究员4', NULL, NULL, NOW(), NULL, NOW(), NOW())");
        return statements;
    }

    /**
     * 构建禁投池主体待审批场景。
     */
    private List<String> buildForbiddenCompanyPendingScene() {
        String batchNo = "SCENE_FORBIDDEN_COMPANY_PENDING";
        List<String> statements = buildSceneCleanupStatements(batchNo);
        statements.add("INSERT INTO `ip_adjust_log` (`id`, `security_code`, `security_short_name`, `security_type`, `adjust_type`, `adjust_mode`, `adjust_batch_no`, `target_pool_id`, `target_pool_name`, `pool_type`, `flow_id`, `flow_key`, `flow_type`, `audit_status`, `adjuster_id`, `adjuster_name`, `adjust_reason`, `adjust_advice`, `submit_time`, `audit_time`, `entry_time`, `is_deleted`, `crte_time`, `updt_time`) VALUES (900004, 'C10005', '某地产集团股份有限公司', 'company', '手工调整', '调入', '" + batchNo + "', 15, '禁投池', 'forbidden', 101, 'bond:standard-inbound', 'bond', '00', '1', '管理员', '脚本工具生成：禁投池主体待审批', NULL, NOW(), NULL, NULL, 0, NOW(), NOW())");
        statements.add("INSERT INTO `ip_adjust_step` (`adjust_log_id`, `adjust_batch_no`, `flow_node_id`, `node_code`, `node_label`, `node_type`, `approval_strategy`, `sort_order`, `step_status`, `handler_id`, `handler_name`, `process_action`, `process_comment`, `start_time`, `process_time`, `crte_time`, `updt_time`) VALUES (900004, '" + batchNo + "', 10101, 'n1', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', NULL, NOW(), NOW(), NOW(), NOW()), (900004, '" + batchNo + "', 10102, 'n2', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '提交禁投池主体调整申请', NOW(), NOW(), NOW(), NOW()), (900004, '" + batchNo + "', 10103, 'n3', '研究员B复核', 'approval', 'preempt', 3, 'pending', '3', '研究员2', NULL, NULL, NOW(), NULL, NOW(), NOW())");
        return statements;
    }

    /**
     * 构建 CRMW 待复核场景。
     */
    private List<String> buildCrmwPendingReviewScene() {
        String batchNo = "SCENE_CRMW_PENDING_REVIEW";
        List<String> statements = buildSceneCleanupStatements(batchNo);
        statements.add("INSERT INTO `ip_adjust_log` (`id`, `security_code`, `security_short_name`, `security_type`, `crmw_name`, `crmw_scode`, `crmw_mktcode`, `crmw_stype`, `adjust_type`, `adjust_mode`, `adjust_batch_no`, `target_pool_id`, `target_pool_name`, `pool_type`, `flow_id`, `flow_key`, `flow_type`, `audit_status`, `adjuster_id`, `adjuster_name`, `adjust_reason`, `adjust_advice`, `submit_time`, `audit_time`, `entry_time`, `is_deleted`, `crte_time`, `updt_time`) VALUES (900005, '103003456', '24某能源MTN001', 'medium_term_note', '24某能源CRMW001', 'CRMW24002.IB', 'CIBM', 'crmw', '手工调整', '调入', '" + batchNo + "', 19, 'CRMW核心库', 'crmw', 101, 'bond:standard-inbound', 'bond', '00', '5', '研究员4', '脚本工具生成：CRMW 待复核调库单', NULL, NOW(), NULL, NULL, 0, NOW(), NOW())");
        statements.add("INSERT INTO `ip_adjust_step` (`adjust_log_id`, `adjust_batch_no`, `flow_node_id`, `node_code`, `node_label`, `node_type`, `approval_strategy`, `sort_order`, `step_status`, `handler_id`, `handler_name`, `process_action`, `process_comment`, `start_time`, `process_time`, `crte_time`, `updt_time`) VALUES (900005, '" + batchNo + "', 10101, 'n1', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', NULL, NOW(), NOW(), NOW(), NOW()), (900005, '" + batchNo + "', 10102, 'n2', '研究员A发起', 'approval', 'initiator', 2, 'submit', '5', '研究员4', 'submit', '提交 CRMW 调库申请', NOW(), NOW(), NOW(), NOW()), (900005, '" + batchNo + "', 10103, 'n3', '研究员B复核', 'approval', 'preempt', 3, 'pending', '3', '研究员2', NULL, NULL, NOW(), NULL, NOW(), NOW())");
        return statements;
    }

    /**
     * 构建场景数据清理语句。
     */
    private List<String> buildSceneCleanupStatements(String batchNo) {
        List<String> statements = new ArrayList<>();
        statements.add("DELETE FROM `ip_adjust_step` WHERE `adjust_batch_no` = '" + batchNo + "'");
        statements.add("DELETE FROM `ip_pool_status` WHERE `adjust_batch_no` = '" + batchNo + "'");
        statements.add("DELETE FROM `ip_pool_status_crmw` WHERE `adjust_batch_no` = '" + batchNo + "'");
        statements.add("DELETE FROM `ip_adjust_log` WHERE `adjust_batch_no` = '" + batchNo + "'");
        return statements;
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
