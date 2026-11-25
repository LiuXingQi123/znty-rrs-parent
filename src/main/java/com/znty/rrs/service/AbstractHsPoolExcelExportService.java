package com.znty.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.common.enums.HsMarketCode;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.commonfile.CommonFileDto;
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
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 提供恒生池定时导出 Excel 的共用处理
 *
 * 统一承载三个恒生池导出任务的参数解析、池范围处理、市场转换、工作簿生成和文件备份
 * 1. 全量任务按当前生效池状态导出，普通证券按任务配置决定是否排除已到期数据，独立 CRMW 组合不校验摘牌日
 * 2. 增量任务按成功执行水位导出审批通过的调入、调出事件，不要求证券当前仍在池
 * 3. 所有任务按叶子池生成 Sheet，支持空池 Sheet、恒生池名称拆分和同名 Sheet 合并
 * 4. 主文件按日期分目录并使用固定名称，覆盖前归档旧版，导出过程和结果写入任务明细日志
 */
public abstract class AbstractHsPoolExcelExportService implements RrsScheduledTask {
    /** 统一证券市场查询组件 */
    @Resource
    private SecurityMarketService securityMarketService;

    /** JSON 参数解析组件 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Excel 固定表头，与老系统恒生定时导出格式一致 */
    private static final String[] TITLES = {"证券名称", "证券代码", "操作类型", "市场名称", "备注"};

    /** Excel 每列固定宽度，单位为字符 */
    private static final int COLUMN_WIDTH = 25;

    /** 流式工作簿在内存中保留的行数 */
    private static final int ROW_ACCESS_WINDOW_SIZE = 500;

    /** 增量首次执行时间参数格式 */
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 默认保留没有数据的投资池 Sheet */
    private static final boolean DEFAULT_EXPORT_EMPTY_POOL = true;

    /** 是否导出恒生池名称为空的池的参数名 */
    private static final String EXPORT_POOL_WITHOUT_HS_NAME = "exportPoolWithoutHsName";

    /** 默认只导出配置了恒生池名称的池 */
    private static final boolean DEFAULT_EXPORT_POOL_WITHOUT_HS_NAME = false;

    /** 恒生池 Excel 默认导出目录 */
    @Value("${rrs.hs-pool-export.storage-path:D:/uploads/znty_rrs/hs-pool-export}")
    private String defaultOutputDir;

    /** 恒生池 Excel 导出数据访问组件 */
    @Resource
    private HsPoolExcelExportMapper hsPoolExcelExportMapper;

    /** 定时任务配置与执行历史数据访问组件 */
    @Resource
    private ScheduledTaskMapper scheduledTaskMapper;

    /**
     * 返回配置页预填的扩展参数，null 表示继承系统配置或待填写
     *
     * 执行顺序：
     * 1. 写入默认扫描池、空池导出开关、无恒生名配置及系统导出目录选项
     * 2. 增量任务另外保留初始扫描时间选项，返回配置页预填参数
     *
     * @return 任务扩展参数的默认值映射
     */
    @Override
    public Map<String, Object> getParamDefaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("poolIds", Collections.emptyList());
        defaults.put("exportEmptyPool", DEFAULT_EXPORT_EMPTY_POOL);
        defaults.put(EXPORT_POOL_WITHOUT_HS_NAME, DEFAULT_EXPORT_POOL_WITHOUT_HS_NAME);
        defaults.put("outputDir", null);
        if (isIncrement()) {
            defaults.put("initialStartTime", null);
        }
        return defaults;
    }

    /**
     * 判断当前任务是否为增量导出
     *
     * @return true=增量导出，false=全量导出
     */
    protected abstract boolean isIncrement();

    /**
     * 判断全量任务是否包含已到期普通证券
     *
     * 执行顺序：
     * 1. 按此任务的固定设置返回 false，表示全量导出排除已到期普通证券
     *
     * @return true=全量导出包含已到期普通证券，false=全量导出排除已到期普通证券
     */
    protected boolean includeExpiredForFullExport() {
        return false;
    }

    /**
     * 获取导出文件名前缀
     *
     * @return 文件名前缀
     */
    protected abstract String filePrefix();

    /**
     * 按页面指定条件生成恒生格式 Excel，不写服务器目录
     *
     * 执行顺序：
     * 1. 查询导出投资池列表
     * 2. 按页面指定条件生成恒生格式 Excel，不写服务器目录
     *
     * @param poolIds 指定叶子投资池；为空时导出全部叶子池
     * @param startTime 增量开始时间；全量模式仅用于参数统一
     * @param endTime 增量结束时间和文件时间
     * @return 可供前端下载的 Excel 文件
     * @throws Exception 查询或工作簿生成失败
     */
    public CommonFileDto exportManual(List<Long> poolIds, Date startTime, Date endTime) throws Exception {
        List<HsPoolExportPoolDto> pools = hsPoolExcelExportMapper.queryExportPoolList(poolIds);
        TaskDetailLog detail = new TaskDetailLog();
        SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW_SIZE);
        workbook.setCompressTempFiles(true);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            // 1. 手动导出保留空池和恒生池名称为空的池
            populateWorkbook(workbook, pools, startTime, endTime, true, true, detail);
            workbook.write(outputStream);
            byte[] bytes = outputStream.toByteArray();
            CommonFileDto result = new CommonFileDto();
            result.setFileName(filePrefix() + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(endTime) + ".xlsx");
            result.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            result.setFileSize((long) bytes.length);
            result.setContentBase64(Base64.getEncoder().encodeToString(bytes));
            return result;
        } finally {
            try {
                workbook.close();
            } finally {
                workbook.dispose();
            }
        }
    }

    /**
     * 执行恒生池 Excel 导出任务
     *
     * 执行顺序：
     * 1. 读取任务名称和扩展参数，确定池范围、名称和空池规则、目录及增量起始时间
     * 2. 查询增量水位并构造全量或增量时间窗口
     * 3. 按叶子池查询数据，生成恒生格式 Excel，覆盖前归档已存在的同日主文件
     * 4. 记录文件路径、Sheet 数量、证券行数并返回任务结果
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
            // 1. 解析当前任务的池范围、空池、目录和首次增量时间参数
            ExportParams params = parseParams(taskConfig);
            detail.line("任务开始：【" + taskName + "】");
            String poolScope = params.poolIds == null || params.poolIds.isEmpty()
                    ? "全部叶子池" : params.poolIds.toString();
            if (isIncrement()) {
                detail.line("扫描条件：poolIds=" + poolScope
                        + "；ip_adjust_log.is_deleted=0、audit_status=20、adjust_mode IN (调入,调出)、"
                        + "category_type!=company；CRMW 要求 crmw_scode 非空；时间范围见增量窗口");
            } else {
                detail.line("扫描条件：poolIds=" + poolScope
                        + "；ip_pool_status/ip_pool_status_crmw.is_deleted=0、audit_status=20、"
                        + "category_type!=company；普通证券包含已到期=" + includeExpiredForFullExport()
                        + "，独立 CRMW 组合不校验摘牌日");
            }
            // 2. 预留增量任务交易日过滤入口，交易日数据源和非交易日策略确认后再启用
            if (isIncrement() && shouldSkipByTradeDay(startTime)) {
                detail.line("INFO", "当前日期不满足交易日执行条件，本次增量导出不执行");
                return ScheduledTaskResult.failure(getTaskCode(), taskName, "交易日校验未通过，增量导出未执行", startTime,
                        System.currentTimeMillis() - beginTime, detail.build());
            }
            // 3. 后续增量读取上次成功开始时间，首次增量使用配置的初始时间
            Date previousSuccessTime = queryWatermark();
            boolean firstIncrement = isIncrement() && previousSuccessTime == null;
            Date windowStart = firstIncrement ? params.initialStartTime : previousSuccessTime;
            if (firstIncrement && windowStart == null) {
                throw new BizException("增量任务首次执行必须配置 initialStartTime，格式为 " + DATE_TIME_PATTERN);
            }
            // 4. 按参数筛选叶子池；未指定 poolIds 时查询全部叶子池
            List<HsPoolExportPoolDto> pools = hsPoolExcelExportMapper.queryExportPoolList(params.poolIds);
            // 5. 解析并创建按执行日期划分的绝对导出目录
            Path outputDir = resolveOutputDir(params.outputDir, startTime);
            // 6. 逐池生成恒生 Excel，覆盖同日主文件时归档旧版
            ExportResult result = writeWorkbook(pools, windowStart, startTime, params, outputDir, detail);
            String scope = firstIncrement ? "首次增量" : isIncrement() ? "增量" : "全量";
            String window = "";
            if (isIncrement()) {
                // 格式化增量时间窗口
                window = "，时间窗口=(" + formatTime(windowStart) + ", " + formatTime(startTime) + "]";
            }
            detail.line("INFO", scope + "导出完成，文件=" + result.filePath
                    + (result.backupPath == null ? "" : "，历史备份=" + result.backupPath)
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
     * 查询增量任务水位线
     *
     * 执行顺序：
     * 1. 返回全量导出标记
     *
     * @return 上一次成功执行的开始时间；全量任务或首次执行返回 null
     */
    private Date queryWatermark() {
        return isIncrement() ? scheduledTaskMapper.queryLastSuccessStartTime(getTaskCode()) : null;
    }

    /**
     * 判断本次增量任务是否应因交易日条件跳过
     *
     * 执行顺序：
     * 1. 查询当前执行时间对应的交易日列表，无数据时继续导出
     * 2. 交易日列表没有执行当天时跳过本轮导出
     *
     * @param executeTime 本次任务开始时间
     * @return true=按任务的交易日条件跳过本轮导出，false=继续本轮导出
     */
    private boolean shouldSkipByTradeDay(Date executeTime) {
        // 1. 查询结果为空表示交易日过滤尚未启用，本次不做拦截
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
     * 查询用于校验的交易日日期
     *
     * 执行顺序：
     * 1. 返回空列表
     *
     * @param executeTime 本次任务开始时间
     * @return 交易日日期；当前返回空集合，表示暂不启用交易日过滤
     */
    protected List<Date> queryTradeDayList(Date executeTime) {
        // 1. TODO 待确认交易日数据源、查询异常处理和非交易日强制执行配置后接入正式查询
        return Collections.emptyList();
    }

    /**
     * 查询指定投资池本次应导出的证券或调库事件
     *
     * 执行顺序：
     * 1. 批量查询当前代码市场，避免列表与导出逐行查询
     *
     * @param poolId 投资池 ID
     * @param windowStart 增量时间窗口下界
     * @param endTime 增量时间窗口上界
     * @return 待导出数据
     */
    private List<HsPoolExportRowDto> queryRows(Long poolId, Date windowStart, Date endTime) {
        List<HsPoolExportRowDto> rows = !isIncrement()
                ? hsPoolExcelExportMapper.queryFullExportRowList(poolId, includeExpiredForFullExport())
                : hsPoolExcelExportMapper.queryIncrementExportRowList(poolId, windowStart, endTime);
        List<String> codes = new ArrayList<>();
        for (HsPoolExportRowDto row : rows) {
            codes.add(row.getSecurityCode());
        }
        Map<String, String> markets = securityMarketService.queryMarketCodeMap(codes);
        for (HsPoolExportRowDto row : rows) {
            row.setMarketCode(markets.get(row.getSecurityCode()));
        }
        return rows;
    }

    /**
     * 解析任务扩展参数
     *
     * 执行顺序：
     * 1. 先设置默认导出目录和开关，扩展参数未配置时直接使用默认值
     * 2. 有扩展参数时将其解析为 JSON 对象
     * 3. 读取自定义导出目录、空池导出开关和无恒生池名称导出开关，后者须配置布尔值
     * 4. 解析可选的叶子投资池 ID 数组
     * 5. 配置了非空的首次增量时间时按约定格式解析
     *
     * @param taskConfig 当前任务配置
     * @return 规范化导出参数
     * @throws Exception JSON 或首次时间解析失败
     */
    private ExportParams parseParams(SysScheduledTaskBo taskConfig) throws Exception {
        // 1. 先设置默认导出目录和开关，扩展参数未配置时直接使用默认值
        ExportParams params = new ExportParams();
        params.outputDir = defaultOutputDir;
        if (taskConfig == null || !StringUtils.hasText(taskConfig.getParamJson())) {
            return params;
        }
        // 2. 有扩展参数时将其解析为 JSON 对象
        JsonNode node = OBJECT_MAPPER.readTree(taskConfig.getParamJson());
        if (!node.isObject()) {
            throw new BizException("任务参数必须是 JSON 对象");
        }
        // 3. 读取自定义导出目录、空池导出开关和无恒生池名称导出开关，后者须配置布尔值
        if (node.hasNonNull("outputDir") && StringUtils.hasText(node.get("outputDir").asText())) {
            params.outputDir = node.get("outputDir").asText().trim();
        }
        if (node.has("exportEmptyPool") && !node.get("exportEmptyPool").isNull()) {
            params.exportEmptyPool = node.get("exportEmptyPool").asBoolean(DEFAULT_EXPORT_EMPTY_POOL);
        }
        JsonNode exportPoolWithoutHsName = node.get(EXPORT_POOL_WITHOUT_HS_NAME);
        if (exportPoolWithoutHsName != null) {
            if (!exportPoolWithoutHsName.isBoolean()) {
                throw new BizException("exportPoolWithoutHsName 必须为布尔值 true 或 false");
            }
            params.exportPoolWithoutHsName = exportPoolWithoutHsName.booleanValue();
        }
        // 4. 解析可选的叶子投资池 ID 数组
        params.poolIds = parsePoolIds(node.get("poolIds"));
        // 5. 配置了非空的首次增量时间时按约定格式解析
        if (node.hasNonNull("initialStartTime") && StringUtils.hasText(node.get("initialStartTime").asText())) {
            // 严格解析首次增量时间
            params.initialStartTime = parseTime(node.get("initialStartTime").asText().trim());
        }
        return params;
    }

    /**
     * 解析投资池 ID 数组
     *
     * 执行顺序：
     * 1. 检查poolIds 必须使用数组格式，例如 [15,16]
     * 2. 解析投资池 ID 数组
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
     * 严格解析首次增量时间
     *
     * 执行顺序：
     * 1. 使用约定的日期时间格式关闭宽松解析
     * 2. 解析失败时提示首次增量时间应使用的格式
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
     * 解析并创建“根目录/执行日期”导出目录
     *
     * 执行顺序：
     * 1. 把导出根目录转为规范化的绝对路径
     * 2. 追加本次执行日期，创建目录并返回该路径
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
     * 按投资池生成流式 Excel 工作簿，同名 Sheet 合并追加
     *
     * 执行顺序：
     * 1. 创建压缩临时文件的流式工作簿，按池填充工作表并合并同名表
     * 2. 完成本次文件写入，在处理结束时清理工作簿及相关临时资源
     *
     * @param pools 待处理叶子投资池
     * @param windowStart 增量时间窗口下界
     * @param endTime 增量时间窗口上界
     * @param params 任务导出参数，用于判断是否跳过空池或恒生池名称为空的池
     * @param outputDir 按日期划分的绝对导出目录
     * @param detail 任务过程日志
     * @return 工作簿写入结果
     * @throws Exception 文件写入失败
     */
    private ExportResult writeWorkbook(List<HsPoolExportPoolDto> pools, Date windowStart, Date endTime,
                                       ExportParams params, Path outputDir, TaskDetailLog detail) throws Exception {
        SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW_SIZE);
        workbook.setCompressTempFiles(true);
        try {
            // 1. 按导出参数逐池写入数据并记录跳过原因
            WorkbookSummary summary = populateWorkbook(workbook, pools, windowStart, endTime,
                    params.exportEmptyPool, params.exportPoolWithoutHsName, detail);
            Path filePath = outputDir.resolve(filePrefix() + ".xlsx");
            Path temporaryPath = Files.createTempFile(outputDir, filePrefix() + "_", ".tmp");
            try {
                // 2. 先完整写入新工作簿，生成失败时保留当天已有的主文件
                try (OutputStream outputStream = Files.newOutputStream(temporaryPath)) {
                    workbook.write(outputStream);
                }
                // 3. 覆盖前归档旧主文件，同一秒的备份重名时追加序号
                String backupPath = null;
                if (Files.exists(filePath)) {
                    Path backupDir = Files.createDirectories(outputDir.resolve("bak"));
                    String historyStem = filePrefix() + "_"
                            + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                    Path historyPath = backupDir.resolve(historyStem + ".xlsx");
                    for (int suffix = 1; Files.exists(historyPath); suffix++) {
                        historyPath = backupDir.resolve(historyStem + "_" + suffix + ".xlsx");
                    }
                    Files.copy(filePath, historyPath);
                    backupPath = historyPath.toString();
                }
                // 4. 用新工作簿替换固定名称的主文件
                Files.move(temporaryPath, filePath, StandardCopyOption.REPLACE_EXISTING);
                // TODO 后续接入公司 FTP 配置，将主文件上传至恒生接收目录
                return new ExportResult(filePath.toString(), backupPath, summary.sheetCount, summary.rowCount);
            } finally {
                Files.deleteIfExists(temporaryPath);
            }
        } finally {
            try {
                workbook.close();
            } finally {
                workbook.dispose();
            }
        }
    }

    /**
     * 查询各投资池并写入工作簿
     *
     * 执行顺序：
     * 1. 按导出设置跳过未配置恒生池名称的投资池
     * 2. 逐池查询本次导出记录，按设置跳过空池
     * 3. 解析工作表名称，新建工作表时写入固定表头并设置列宽，同名工作表合并数据
     * 4. 写入记录并累计行数，全量去重，增量保留同一证券的多次调库事件
     * 5. 没有生成任何工作表时创建带表头的空池工作表
     *
     * @param workbook 工作簿
     * @param pools 待处理叶子投资池
     * @param windowStart 增量时间窗口下界
     * @param endTime 增量时间窗口上界
     * @param exportEmptyPool 是否为没有可导出记录的池保留工作表
     * @param exportPoolWithoutHsName 是否导出未配置恒生池名称的投资池
     * @param detail 任务过程日志
     * @return 导出工作簿中的工作表数和写入的总行数
     */
    private WorkbookSummary populateWorkbook(SXSSFWorkbook workbook, List<HsPoolExportPoolDto> pools,
                                               Date windowStart, Date endTime, boolean exportEmptyPool,
                                               boolean exportPoolWithoutHsName, TaskDetailLog detail) {
        int rowCount = 0;
        Map<String, Sheet> sheets = new HashMap<>();
        Map<String, String> sheetOwners = new HashMap<>();
        for (HsPoolExportPoolDto pool : pools) {
            // 1. 按导出设置跳过未配置恒生池名称的投资池
            if (!exportPoolWithoutHsName && !StringUtils.hasText(pool.getHsPoolName())) {
                detail.line("INFO", "投资池 " + pool.getPoolId() + "-" + pool.getPoolName()
                        + "：恒生池名称为空，已跳过导出");
                continue;
            }
            // 2. 逐池查询本次导出记录，按设置跳过空池
            List<HsPoolExportRowDto> poolRows = queryRows(pool.getPoolId(), windowStart, endTime);
            if (!exportEmptyPool && (poolRows == null || poolRows.isEmpty())) {
                detail.line("INFO", "投资池 " + pool.getPoolId() + "-" + pool.getPoolName() + " 为空，已跳过 Sheet");
                continue;
            }
            // 3. 解析工作表名称，新建工作表时写入固定表头并设置列宽，同名工作表合并数据
            for (String sheetName : resolveSheetNames(pool)) {
                String sheetKey = sheetName.toLowerCase(Locale.ROOT);
                Sheet sheet = sheets.get(sheetKey);
                if (sheet == null) {
                    sheet = workbook.createSheet(sheetName);
                    sheets.put(sheetKey, sheet);
                    sheetOwners.put(sheetKey, pool.getPoolId() + "-" + pool.getPoolName());
                    // 新建 Sheet 时写入固定表头并设置列宽
                    createHeader(sheet);
                } else {
                    detail.line("WARN", "Sheet 名称重复，已合并追加：" + sheetName + "；原池="
                            + sheetOwners.get(sheetKey) + "，当前池=" + pool.getPoolId() + "-" + pool.getPoolName());
                }
                // 4. 写入记录并累计行数，全量去重，增量保留同一证券的多次调库事件
                rowCount += writePoolRows(sheet, poolRows, !isIncrement());
            }
        }
        // 5. 没有生成任何工作表时创建带表头的空池工作表
        if (sheets.isEmpty()) {
            Sheet emptySheet = workbook.createSheet("（空池）");
            // 写入固定五列表头，并将全部列宽设置为 25 个字符
            createHeader(emptySheet);
            sheets.put("（空池）", emptySheet);
        }
        return new WorkbookSummary(sheets.size(), rowCount);
    }

    /**
     * 生成投资池对应的 Sheet 名称列表
     *
     * 执行顺序：
     * 1. 优先取恒生池名称，缺失时按投资池全路径或简称生成
     * 2. 按竖线拆分多个名称并处理非法字符或空名称
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
     * 写入固定五列表头，并将全部列宽设置为 25 个字符
     *
     * 执行顺序：
     * 1. 在工作表首行创建表头，按固定顺序写入五列并设置各列宽度
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
     * 写入市场拆分后的证券或调库事件行
     *
     * 执行顺序：
     * 1. 从工作表最后一行之后开始写入，为每个证券或事件展开实际市场
     * 2. 按展开后的记录去重并填写单元格，统计新增行数
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
            // 1. 按当前记录所属市场生成恒生行
            List<String[]> marketRows = markets(row);
            for (String[] market : marketRows) {
                if (deduplicate && !seen.add(market[0] + "|" + market[1])) {
                    continue;
                }
                Row line = sheet.createRow(rowIndex++);
                // 将空字符串规范化为空白单元格
                line.createCell(0).setCellValue(emptyIfNull(row.getSecurityShortName()));
                line.createCell(1).setCellValue(market[1]);
                // 将空字符串规范化为空白单元格
                line.createCell(2).setCellValue(emptyIfNull(row.getOperationType()));
                line.createCell(3).setCellValue(market[0]);
                line.createCell(4).setCellValue("");
            }
        }
        return rowIndex - startIndex;
    }

    /**
     * 将当前证券代码转换为恒生市场编码与无后缀代码组合
     *
     * 执行顺序：
     * 1. 按当前记录唯一市场写出代码
     * 2. 将当前证券代码转换为恒生市场编码与无后缀代码组合
     *
     * @param row 证券原始行
     * @return 市场拆分行
     */
    private List<String[]> markets(HsPoolExportRowDto row) {
        List<String[]> result = new ArrayList<>();
        HsMarketCode hsMarket;
        switch (row.getMarketCode()) {
            case "SSE":
                hsMarket = HsMarketCode.SHANGHAI_STOCK_EXCHANGE;
                break;
            case "SZSE":
                hsMarket = HsMarketCode.SHENZHEN_STOCK_EXCHANGE;
                break;
            case "NIB":
                hsMarket = HsMarketCode.INTERBANK_MARKET;
                break;
            case "BSE":
                hsMarket = HsMarketCode.BEIJING_STOCK_EXCHANGE;
                break;
            case "HKEX":
                hsMarket = HsMarketCode.HONG_KONG_STOCK_EXCHANGE;
                break;
            case "OTC":
                hsMarket = HsMarketCode.OTC_MARKET;
                break;
            case "JWCW":
                hsMarket = HsMarketCode.JWCW_MARKET;
                break;
            case "QDII":
            case "OTHER":
                hsMarket = HsMarketCode.OTHER_QDII_MARKET;
                break;
            default:
                throw new BizException("恒生导出不支持市场：" + row.getSecurityCode() + "，" + row.getMarketCode());
        }
        // 1. 按当前记录唯一市场写出代码
        addMarket(result, hsMarket, row.getSecurityCode());
        return result;
    }

    /**
     * 添加非空市场代码
     *
     * 执行顺序：
     * 1. 证券代码已填写时去掉市场后缀，去掉后仍有代码才追加市场与证券代码
     *
     * @param markets 市场拆分行集合
     * @param marketCode 恒生市场编码
     * @param code 证券代码
     */
    private void addMarket(List<String[]> markets, HsMarketCode marketCode, String code) {
        if (StringUtils.hasText(code)) {
            // 1. 去掉 Wind 证券代码中的市场后缀
            String securityCode = removeSecurityCodeSuffix(code);
            if (StringUtils.hasText(securityCode)) {
                markets.add(new String[]{marketCode.getCode(), securityCode});
            }
        }
    }

    /**
     * 去掉证券代码首个点号及其后的市场后缀
     *
     * 执行顺序：
     * 1. 去掉输入首尾空白，再以第一个点号为分界取证券代码主体
     *
     * @param code 原始证券代码
     * @return 无市场后缀的证券代码
     */
    private String removeSecurityCodeSuffix(String code) {
        String securityCode = code.trim();
        int suffixIndex = securityCode.indexOf('.');
        return suffixIndex < 0 ? securityCode : securityCode.substring(0, suffixIndex);
    }

    /**
     * 将缺失文本转换为空白单元格可用的值
     *
     * 执行顺序：
     * 1. 输入为 null 时返回空字符串，有值时原样返回
     *
     * @param value 原始值
     * @return 输入为 null 时返回空字符串，有值时返回原文本
     */
    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    /**
     * 格式化增量时间窗口
     *
     * 执行顺序：
     * 1. 按 yyyy-MM-dd HH:mm:ss 将窗口时间转换成文本
     *
     * @param time 待输出的增量窗口起止时间
     * @return yyyy-MM-dd HH:mm:ss 格式的时间文本
     */
    private String formatTime(Date time) {
        return new SimpleDateFormat(DATE_TIME_PATTERN).format(time);
    }

    /**
     * 保存恒生导出任务解析后的扩展参数
     *
     * 记录指定的叶子池、空池和无恒生名称池的导出选择、输出目录及首次增量起点
     */
    private static class ExportParams {
        /** 指定叶子投资池；为空时导出全部叶子池 */
        private List<Long> poolIds;

        /** 是否生成没有数据的投资池 Sheet */
        private boolean exportEmptyPool = DEFAULT_EXPORT_EMPTY_POOL;

        /** 是否导出恒生池名称为空的投资池 */
        private boolean exportPoolWithoutHsName = DEFAULT_EXPORT_POOL_WITHOUT_HS_NAME;

        /** 导出根目录 */
        private String outputDir;

        /** 增量任务首次执行的时间窗口下界 */
        private Date initialStartTime;
    }

    /**
     * 记录一次恒生工作簿的写入结果
     *
     * 返回导出文件路径以及实际工作表和数据行数，覆盖主文件时一并提供原文件的备份路径
     */
    private static class ExportResult {
        /** 导出文件绝对路径 */
        private final String filePath;

        /** 旧主文件的历史备份绝对路径，本次未覆盖主文件时为 null */
        private final String backupPath;

        /** 工作簿 Sheet 数量 */
        private final int sheetCount;

        /** 实际导出证券行数 */
        private final int rowCount;

        /**
         * 构造工作簿写入结果
         *
         * 执行顺序：
         * 1. 保存输出文件、备份文件路径及写入的工作表和数据行数量
         *
         * @param filePath 导出文件绝对路径
         * @param backupPath 旧主文件的历史备份绝对路径，本次未覆盖主文件时为 null
         * @param sheetCount 工作表数量
         * @param rowCount 数据行数量
         */
        private ExportResult(String filePath, String backupPath, int sheetCount, int rowCount) {
            this.filePath = filePath;
            this.backupPath = backupPath;
            this.sheetCount = sheetCount;
            this.rowCount = rowCount;
        }
    }

    /**
     * 记录恒生工作簿的内容统计
     *
     * 累计本次创建的工作表和实际写入的数据行，供导出完成后生成结果
     */
    private static class WorkbookSummary {
        /** Sheet 数量 */
        private final int sheetCount;

        /** 实际数据行数 */
        private final int rowCount;

        /**
         * 构造工作簿统计
         *
         * 执行顺序：
         * 1. 保存工作簿的工作表数和数据行数
         *
         * @param sheetCount 工作表数量
         * @param rowCount 数据行数量
         */
        private WorkbookSummary(int sheetCount, int rowCount) {
            this.sheetCount = sheetCount;
            this.rowCount = rowCount;
        }
    }
}
