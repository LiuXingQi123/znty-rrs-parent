package com.znty.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.schedule.HsPoolExportPoolDto;
import com.znty.rrs.entity.schedule.HsPoolExportRowDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.HsPoolExcelExportMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.schedule.RrsScheduledTask;
import com.znty.rrs.schedule.ScheduledTaskResult;
import com.znty.rrs.schedule.TaskDetailLog;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 恒生池 Excel 定时导出公共实现。 */
public abstract class AbstractHsPoolExcelExportService implements RrsScheduledTask {
    /** JSON 参数解析组件。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /** Excel 固定表头，与老系统恒生定时导出格式一致。 */
    private static final String[] TITLES = {"证券名称", "证券代码", "操作类型", "市场名称", "备注"};
    /** Excel 每列固定宽度，单位为字符。 */
    private static final int COLUMN_WIDTH = 25;
    /** 流式工作簿在内存中保留的行数。 */
    private static final int ROW_ACCESS_WINDOW_SIZE = 500;
    /** 增量首次执行时间参数格式。 */
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 恒生池 Excel 默认导出目录。 */
    @Value("${rrs.hs-pool-export.storage-path:D:/uploads/znty_rrs/hs-pool-export}")
    private String defaultOutputDir;

    /** 恒生池 Excel 导出数据访问组件。 */
    @Resource
    private HsPoolExcelExportMapper hsPoolExcelExportMapper;
    /** 定时任务配置与执行历史数据访问组件。 */
    @Resource
    private ScheduledTaskMapper scheduledTaskMapper;

    /**
     * 判断当前任务是否为增量导出。
     *
     * @return true 表示增量导出
     */
    protected abstract boolean isIncrement();

    /**
     * 获取导出文件名前缀。
     *
     * @return 文件名前缀
     */
    protected abstract String filePrefix();

    /**
     * 执行恒生池 Excel 导出任务。
     *
     * @return 定时任务执行结果
     */
    @Override
    public ScheduledTaskResult execute() {
        Date startTime = new Date();
        long beginTime = System.currentTimeMillis();
        TaskDetailLog detail = new TaskDetailLog();
        String taskName = getTaskCode();
        try {
            SysScheduledTaskBo taskConfig = scheduledTaskMapper.queryTaskByCode(getTaskCode());
            taskName = taskConfig == null || !StringUtils.hasText(taskConfig.getTaskName())
                    ? getTaskCode() : taskConfig.getTaskName();
            // 解析当前任务的池范围、空池、目录和首次增量时间参数。
            ExportParams params = parseParams(taskConfig);
            // 预留增量任务交易日过滤入口，交易日数据源和非交易日策略确认后再启用。
            if (isIncrement() && shouldSkipByTradeDay(startTime)) {
                detail.line("INFO", "当前日期不满足交易日执行条件，本次增量导出不执行");
                return ScheduledTaskResult.failure(getTaskCode(), taskName, "交易日校验未通过，增量导出未执行", startTime,
                        System.currentTimeMillis() - beginTime, detail.build());
            }
            // 后续增量读取上次成功开始时间，首次增量使用配置的初始时间。
            Date previousSuccessTime = queryWatermark();
            boolean firstIncrement = isIncrement() && previousSuccessTime == null;
            Date windowStart = firstIncrement ? params.initialStartTime : previousSuccessTime;
            if (firstIncrement && windowStart == null) {
                throw new BizException("增量任务首次执行必须配置 initialStartTime，格式为 " + DATE_TIME_PATTERN);
            }
            // 按参数筛选叶子池；未指定 poolIds 时查询全部叶子池。
            List<HsPoolExportPoolDto> pools = hsPoolExcelExportMapper.queryExportPoolList(params.poolIds);
            // 解析并创建按执行日期划分的绝对导出目录。
            Path outputDir = resolveOutputDir(params.outputDir, startTime);
            // 按叶子池逐池查询，并使用流式工作簿生成恒生 Excel 文件和备份文件。
            ExportResult result = writeWorkbook(pools, windowStart, startTime, params.exportEmptyPool,
                    outputDir, detail);
            String scope = firstIncrement ? "首次增量" : isIncrement() ? "增量" : "全量";
            String window = "";
            if (isIncrement()) {
                window = "，时间窗口=(" + formatTime(windowStart) + ", " + formatTime(startTime) + "]";
            }
            detail.line("INFO", scope + "导出完成，文件=" + result.filePath + "，备份=" + result.backupPath
                    + "，Sheet=" + result.sheetCount + "，证券行=" + result.rowCount + window);
            return ScheduledTaskResult.success(getTaskCode(), taskName, scope + "导出完成：" + result.filePath,
                    result.rowCount, startTime, System.currentTimeMillis() - beginTime, detail.build());
        } catch (Exception e) {
            detail.line("ERROR", "导出失败：" + e.getMessage());
            return ScheduledTaskResult.failure(getTaskCode(), taskName, "导出失败：" + e.getMessage(), startTime,
                    System.currentTimeMillis() - beginTime, detail.build());
        }
    }

    /**
     * 查询增量任务水位线。
     *
     * @return 上一次成功执行的开始时间；全量任务或首次执行返回 null
     */
    private Date queryWatermark() {
        return isIncrement() ? scheduledTaskMapper.queryLastSuccessStartTime(getTaskCode()) : null;
    }

    /**
     * 判断本次增量任务是否应因交易日条件跳过。
     *
     * @param executeTime 本次任务开始时间
     * @return 当前阶段固定返回 false，不执行交易日过滤
     */
    private boolean shouldSkipByTradeDay(Date executeTime) {
        // 查询结果为空表示交易日过滤尚未启用，本次不做拦截。
        List<Date> tradeDays = queryTradeDayList(executeTime);
        if (tradeDays == null || tradeDays.isEmpty()) {
            return false;
        }
        String executeDate = new SimpleDateFormat("yyyyMMdd").format(executeTime);
        for (Date tradeDay : tradeDays) {
            if (tradeDay != null && executeDate.equals(new SimpleDateFormat("yyyyMMdd").format(tradeDay))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 查询用于校验的交易日日期。
     *
     * @param executeTime 本次任务开始时间
     * @return 交易日日期；当前返回空集合，表示暂不启用交易日过滤
     */
    protected List<Date> queryTradeDayList(Date executeTime) {
        // TODO 待确认交易日数据源、查询异常处理和非交易日强制执行配置后接入正式查询。
        return Collections.emptyList();
    }

    /**
     * 查询指定投资池本次应导出的证券或调库事件。
     *
     * @param poolId 投资池 ID
     * @param windowStart 增量时间窗口下界
     * @param endTime 增量时间窗口上界
     * @return 待导出数据
     */
    private List<HsPoolExportRowDto> queryRows(Long poolId, Date windowStart, Date endTime) {
        if (!isIncrement()) {
            return hsPoolExcelExportMapper.queryFullExportRowList(poolId);
        }
        return hsPoolExcelExportMapper.queryIncrementExportRowList(poolId, windowStart, endTime);
    }

    /**
     * 解析任务扩展参数。
     *
     * @param taskConfig 当前任务配置
     * @return 规范化导出参数
     * @throws Exception JSON 或首次时间解析失败
     */
    private ExportParams parseParams(SysScheduledTaskBo taskConfig) throws Exception {
        ExportParams params = new ExportParams();
        params.outputDir = defaultOutputDir;
        if (taskConfig == null || !StringUtils.hasText(taskConfig.getParamJson())) {
            return params;
        }
        JsonNode node = OBJECT_MAPPER.readTree(taskConfig.getParamJson());
        if (!node.isObject()) {
            throw new BizException("任务参数必须是 JSON 对象");
        }
        if (node.hasNonNull("outputDir") && StringUtils.hasText(node.get("outputDir").asText())) {
            params.outputDir = node.get("outputDir").asText().trim();
        }
        if (node.has("exportEmptyPool") && !node.get("exportEmptyPool").isNull()) {
            params.exportEmptyPool = node.get("exportEmptyPool").asBoolean(true);
        }
        // 解析可选的叶子投资池 ID 数组。
        params.poolIds = parsePoolIds(node.get("poolIds"));
        if (node.hasNonNull("initialStartTime") && StringUtils.hasText(node.get("initialStartTime").asText())) {
            params.initialStartTime = parseTime(node.get("initialStartTime").asText().trim());
        }
        return params;
    }

    /**
     * 解析投资池 ID 数组。
     *
     * @param poolIdsNode poolIds JSON 节点
     * @return 投资池 ID；未配置或空数组时返回 null
     */
    private List<Long> parsePoolIds(JsonNode poolIdsNode) {
        if (poolIdsNode == null || poolIdsNode.isNull()) {
            return null;
        }
        if (!poolIdsNode.isArray()) {
            throw new BizException("poolIds 必须使用数组格式，例如 [15,16]");
        }
        List<Long> poolIds = new ArrayList<>();
        for (JsonNode poolIdNode : poolIdsNode) {
            if (!poolIdNode.canConvertToLong()) {
                throw new BizException("poolIds 只能包含数字 ID");
            }
            poolIds.add(poolIdNode.asLong());
        }
        return poolIds.isEmpty() ? null : poolIds;
    }

    /**
     * 严格解析首次增量时间。
     *
     * @param value 时间文本
     * @return 时间对象
     */
    private Date parseTime(String value) {
        SimpleDateFormat formatter = new SimpleDateFormat(DATE_TIME_PATTERN);
        formatter.setLenient(false);
        try {
            return formatter.parse(value);
        } catch (ParseException e) {
            throw new BizException("initialStartTime 格式错误，应为 " + DATE_TIME_PATTERN);
        }
    }

    /**
     * 解析并创建“根目录/执行日期”导出目录。
     *
     * @param outputDir 配置的导出根目录
     * @param startTime 本次执行开始时间
     * @return 已创建的日期目录
     * @throws Exception 目录创建失败
     */
    private Path resolveOutputDir(String outputDir, Date startTime) throws Exception {
        Path rootPath = Paths.get(outputDir).toAbsolutePath().normalize();
        Path datePath = rootPath.resolve(new SimpleDateFormat("yyyyMMdd").format(startTime)).normalize();
        Files.createDirectories(datePath);
        return datePath;
    }

    /**
     * 按投资池生成流式 Excel 工作簿，同名 Sheet 合并追加。
     *
     * @param pools 待处理叶子投资池
     * @param windowStart 增量时间窗口下界
     * @param endTime 增量时间窗口上界
     * @param exportEmptyPool 是否导出空池 Sheet
     * @param outputDir 按日期划分的绝对导出目录
     * @param detail 任务过程日志
     * @return 工作簿写入结果
     * @throws Exception 文件写入失败
     */
    private ExportResult writeWorkbook(List<HsPoolExportPoolDto> pools, Date windowStart, Date endTime,
                                       boolean exportEmptyPool, Path outputDir, TaskDetailLog detail) throws Exception {
        SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW_SIZE);
        workbook.setCompressTempFiles(true);
        try {
            int rowCount = 0;
            Map<String, Sheet> sheets = new HashMap<>();
            Map<String, String> sheetOwners = new HashMap<>();
            for (HsPoolExportPoolDto pool : pools) {
                // 逐池查询，避免一次性将全部池数据加载到内存。
                List<HsPoolExportRowDto> poolRows = queryRows(pool.getPoolId(), windowStart, endTime);
                if (!exportEmptyPool && (poolRows == null || poolRows.isEmpty())) {
                    detail.line("INFO", "投资池 " + pool.getPoolId() + "-" + pool.getPoolName() + " 为空，已跳过 Sheet");
                    continue;
                }
                // 一个恒生池名称可使用竖线拆分为多个 Sheet 名称。
                for (String sheetName : resolveSheetNames(pool)) {
                    String sheetKey = sheetName.toLowerCase(Locale.ROOT);
                    Sheet sheet = sheets.get(sheetKey);
                    if (sheet == null) {
                        sheet = workbook.createSheet(sheetName);
                        sheets.put(sheetKey, sheet);
                        sheetOwners.put(sheetKey, pool.getPoolId() + "-" + pool.getPoolName());
                        // 新建 Sheet 时写入固定表头并设置列宽。
                        createHeader(sheet);
                    } else {
                        detail.line("WARN", "Sheet 名称重复，已合并追加：" + sheetName + "；原池="
                                + sheetOwners.get(sheetKey) + "，当前池=" + pool.getPoolId() + "-" + pool.getPoolName());
                    }
                    // 全量按市场和代码去重；增量保留同一证券的多次调入、调出事件。
                    rowCount += writePoolRows(sheet, poolRows, !isIncrement());
                }
            }
            if (sheets.isEmpty()) {
                Sheet emptySheet = workbook.createSheet("（空池）");
                createHeader(emptySheet);
                sheets.put("（空池）", emptySheet);
            }
            String fileName = filePrefix() + "_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss").format(endTime) + ".xlsx";
            Path filePath = outputDir.resolve(fileName).toAbsolutePath().normalize();
            try (OutputStream outputStream = Files.newOutputStream(filePath)) {
                workbook.write(outputStream);
            }
            // 文件写入成功后复制到日期目录下的 bak 备份目录。
            Path backupDir = outputDir.resolve("bak");
            Files.createDirectories(backupDir);
            Path backupPath = backupDir.resolve(fileName).toAbsolutePath().normalize();
            Files.copy(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            // TODO 后续接入公司 FTP 配置，将主文件和备份文件上传至恒生接收目录。
            return new ExportResult(filePath.toString(), backupPath.toString(), sheets.size(), rowCount);
        } finally {
            try {
                workbook.close();
            } finally {
                workbook.dispose();
            }
        }
    }

    /**
     * 生成投资池对应的 Sheet 名称列表。
     *
     * @param pool 当前投资池
     * @return Sheet 名称列表
     */
    private List<String> resolveSheetNames(HsPoolExportPoolDto pool) {
        boolean usePoolFullName = !StringUtils.hasText(pool.getHsPoolName());
        String configuredName = usePoolFullName ? pool.getPoolFullName() : pool.getHsPoolName();
        if (!StringUtils.hasText(configuredName)) {
            configuredName = pool.getPoolName();
        }
        if (usePoolFullName) {
            configuredName = configuredName.replaceAll("[\\\\/:*?\"<>|]", "_");
        }
        List<String> sheetNames = new ArrayList<>();
        for (String name : configuredName.split("\\|")) {
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String truncatedName = name.trim().length() > 31 ? name.trim().substring(0, 31) : name.trim();
            String safeName = WorkbookUtil.createSafeSheetName(truncatedName);
            if (StringUtils.hasText(safeName)) {
                sheetNames.add(safeName);
            }
        }
        if (sheetNames.isEmpty()) {
            sheetNames.add("（空池）");
        }
        return sheetNames;
    }

    /**
     * 写入固定五列表头，并将全部列宽设置为 25 个字符。
     *
     * @param sheet 当前工作表
     */
    private void createHeader(Sheet sheet) {
        Row header = sheet.createRow(0);
        for (int index = 0; index < TITLES.length; index++) {
            header.createCell(index).setCellValue(TITLES[index]);
            sheet.setColumnWidth(index, COLUMN_WIDTH * 256);
        }
    }

    /**
     * 写入市场拆分后的证券或调库事件行。
     *
     * @param sheet 当前工作表
     * @param rows 当前投资池原始数据
     * @param deduplicate 是否按市场和证券代码去重
     * @return 实际写入行数
     */
    private int writePoolRows(Sheet sheet, List<HsPoolExportRowDto> rows, boolean deduplicate) {
        if (rows == null) {
            return 0;
        }
        int rowIndex = sheet.getLastRowNum() + 1;
        int startIndex = rowIndex;
        Set<String> seen = new HashSet<>();
        for (HsPoolExportRowDto row : rows) {
            // 将证券各市场代码展开为独立市场行，市场转换规则本轮保持不变。
            List<String[]> marketRows = markets(row);
            for (String[] market : marketRows) {
                if (deduplicate && !seen.add(market[0] + "|" + market[1])) {
                    continue;
                }
                Row line = sheet.createRow(rowIndex++);
                line.createCell(0).setCellValue(emptyIfNull(row.getSecurityShortName()));
                line.createCell(1).setCellValue(market[1]);
                line.createCell(2).setCellValue(emptyIfNull(row.getOperationType()));
                line.createCell(3).setCellValue(market[0]);
                line.createCell(4).setCellValue("");
            }
        }
        return rowIndex - startIndex;
    }

    /**
     * 将证券各市场代码展开为市场名称与证券代码组合。
     *
     * @param row 证券原始行
     * @return 市场拆分行
     */
    private List<String[]> markets(HsPoolExportRowDto row) {
        List<String[]> result = new ArrayList<>();
        // 添加沪市证券代码。
        addMarket(result, "上海证券交易所", row.getWindCodeSh());
        // 添加深市证券代码。
        addMarket(result, "深圳证券交易所", row.getWindCodeSz());
        // 添加银行间市场证券代码。
        addMarket(result, "银行间市场", row.getWindCodeNib());
        // 添加北交所证券代码。
        addMarket(result, "北京证券交易所", row.getWindCodeBj());
        // 添加其他市场证券代码。
        addMarket(result, "其他", row.getWindCodeNbc());
        return result;
    }

    /**
     * 添加非空市场代码。
     *
     * @param markets 市场拆分行集合
     * @param marketName 投资市场名称
     * @param code 证券代码
     */
    private void addMarket(List<String[]> markets, String marketName, String code) {
        if (StringUtils.hasText(code)) {
            markets.add(new String[]{marketName, code.trim()});
        }
    }

    /**
     * 将空字符串规范化为空白单元格。
     *
     * @param value 原始值
     * @return 非 null 文本
     */
    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    /** 格式化增量时间窗口。 */
    private String formatTime(Date time) {
        return new SimpleDateFormat(DATE_TIME_PATTERN).format(time);
    }

    /** 规范化任务扩展参数。 */
    private static class ExportParams {
        /** 指定叶子投资池；为空时导出全部叶子池。 */
        private List<Long> poolIds;
        /** 是否生成没有数据的投资池 Sheet。 */
        private boolean exportEmptyPool = true;
        /** 导出根目录。 */
        private String outputDir;
        /** 增量任务首次执行的时间窗口下界。 */
        private Date initialStartTime;
    }

    /** 单次工作簿写入结果。 */
    private static class ExportResult {
        /** 导出文件绝对路径。 */
        private final String filePath;
        /** 备份文件绝对路径。 */
        private final String backupPath;
        /** 工作簿 Sheet 数量。 */
        private final int sheetCount;
        /** 实际导出证券行数。 */
        private final int rowCount;

        /** 构造工作簿写入结果。 */
        private ExportResult(String filePath, String backupPath, int sheetCount, int rowCount) {
            this.filePath = filePath;
            this.backupPath = backupPath;
            this.sheetCount = sheetCount;
            this.rowCount = rowCount;
        }
    }
}
