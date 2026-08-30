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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    /** Excel 固定表头。 */
    private static final String[] TITLES = {"证券代码", "证券名称", "投资市场", "备注"};
    /** Excel 每列固定宽度，单位为字符。 */
    private static final int COLUMN_WIDTH = 25;
    /** 流式工作簿在内存中保留的行数。 */
    private static final int ROW_ACCESS_WINDOW_SIZE = 500;

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
            // 查询增量任务上一次成功执行的开始时间。
            Date watermark = queryWatermark();
            boolean firstIncrement = isIncrement() && watermark == null;
            List<HsPoolExportPoolDto> pools = hsPoolExcelExportMapper.queryExportPoolList();
            // 校验全部待导出叶子池的 Sheet 名称。
            validatePools(pools);
            // 解析并规范化本次任务的绝对导出目录。
            Path outputDir = resolveOutputDir(taskConfig);
            // 按叶子池逐池查询，并使用流式工作簿生成恒生池 Excel 文件。
            ExportResult result = writeWorkbook(pools, watermark, startTime, firstIncrement, outputDir);
            String scope = firstIncrement ? "首次全量" : isIncrement() ? "增量" : "全量";
            String window = "";
            if (isIncrement() && !firstIncrement) {
                // 格式化增量时间窗口下界。
                String startText = formatTime(watermark);
                // 格式化增量时间窗口上界。
                String endText = formatTime(startTime);
                window = "，时间窗口=(" + startText + ", " + endText + "]";
            }
            detail.line("INFO", scope + "导出完成，文件=" + result.filePath + "，Sheet=" + result.sheetCount
                    + "，证券行=" + result.rowCount + window);
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
     * 根据全量、首次增量及后续增量场景查询指定投资池应导出的证券。
     *
     * @param poolId 投资池 ID
     * @param watermark 增量时间窗口下界
     * @param endTime 增量时间窗口上界
     * @param firstIncrement 是否为增量任务首次执行
     * @return 待导出证券列表
     */
    private List<HsPoolExportRowDto> queryRows(Long poolId, Date watermark, Date endTime,
                                               boolean firstIncrement) {
        if (!isIncrement() || firstIncrement) {
            return hsPoolExcelExportMapper.queryFullExportRowList(poolId);
        }
        return hsPoolExcelExportMapper.queryIncrementExportRowList(poolId, watermark, endTime);
    }

    /**
     * 解析任务导出目录，并统一转换为规范化绝对路径。
     *
     * @param taskConfig 当前任务配置
     * @return 已创建的绝对导出目录
     * @throws Exception JSON 或目录解析失败
     */
    private Path resolveOutputDir(SysScheduledTaskBo taskConfig) throws Exception {
        String outputDir = defaultOutputDir;
        if (taskConfig != null && StringUtils.hasText(taskConfig.getParamJson())) {
            JsonNode node = OBJECT_MAPPER.readTree(taskConfig.getParamJson());
            if (node.hasNonNull("outputDir") && StringUtils.hasText(node.get("outputDir").asText())) {
                outputDir = node.get("outputDir").asText().trim();
            }
        }
        Path outputPath = Paths.get(outputDir).toAbsolutePath().normalize();
        Files.createDirectories(outputPath);
        return outputPath;
    }

    /**
     * 校验恒生池名称唯一且符合 Excel Sheet 命名规则。
     *
     * @param pools 待导出的叶子投资池
     */
    private void validatePools(List<HsPoolExportPoolDto> pools) {
        Map<String, HsPoolExportPoolDto> names = new HashMap<>();
        for (HsPoolExportPoolDto pool : pools) {
            // 校验单个恒生池名称能否直接作为 Excel Sheet 名称。
            validateSheetName(pool.getHsPoolName());
            String uniqueName = pool.getHsPoolName().toLowerCase(Locale.ROOT);
            HsPoolExportPoolDto existedPool = names.put(uniqueName, pool);
            if (existedPool != null && !existedPool.getPoolId().equals(pool.getPoolId())) {
                throw new BizException("恒生池名称重复：" + pool.getHsPoolName() + "（"
                        + existedPool.getPoolId() + "-" + existedPool.getPoolName() + "、"
                        + pool.getPoolId() + "-" + pool.getPoolName() + "）");
            }
        }
    }

    /**
     * 校验 Excel Sheet 名称长度和非法字符。
     *
     * @param name 恒生池名称
     */
    private void validateSheetName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BizException("恒生池名称不能作为 Excel Sheet 名：" + name);
        }
        try {
            WorkbookUtil.validateSheetName(name);
        } catch (IllegalArgumentException e) {
            throw new BizException("恒生池名称不能作为 Excel Sheet 名：" + name);
        }
    }

    /**
     * 按叶子投资池生成流式 Excel 工作簿。
     *
     * @param pools 待生成 Sheet 的叶子投资池
     * @param watermark 增量时间窗口下界
     * @param endTime 增量时间窗口上界
     * @param firstIncrement 是否为增量任务首次执行
     * @param outputDir 绝对导出目录
     * @return 工作簿写入结果
     * @throws Exception 文件写入失败
     */
    private ExportResult writeWorkbook(List<HsPoolExportPoolDto> pools, Date watermark, Date endTime,
                                       boolean firstIncrement, Path outputDir) throws Exception {
        SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW_SIZE);
        workbook.setCompressTempFiles(true);
        try {
            int rowCount = 0;
            for (HsPoolExportPoolDto pool : pools) {
                Sheet sheet = workbook.createSheet(pool.getHsPoolName());
                // 写入固定表头并设置固定列宽。
                createHeader(sheet);
                // 仅查询当前投资池数据，避免一次性将全部池证券加载到内存。
                List<HsPoolExportRowDto> poolRows = queryRows(pool.getPoolId(), watermark, endTime, firstIncrement);
                // 写入当前投资池的市场拆分证券行。
                rowCount += writePoolRows(sheet, poolRows);
            }
            String fileName = filePrefix() + "_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx";
            Path filePath = outputDir.resolve(fileName).toAbsolutePath().normalize();
            try (OutputStream outputStream = Files.newOutputStream(filePath)) {
                workbook.write(outputStream);
            }
            return new ExportResult(filePath.toString(), pools.size(), rowCount);
        } finally {
            try {
                workbook.close();
            } finally {
                workbook.dispose();
            }
        }
    }

    /**
     * 写入固定四列表头，并将全部列宽设置为 25 个字符。
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
     * 以投资池、市场、证券代码去重后写入市场拆分行。
     *
     * @param sheet 当前工作表
     * @param rows 当前投资池的证券原始行
     * @return 实际写入证券行数
     */
    private int writePoolRows(Sheet sheet, List<HsPoolExportRowDto> rows) {
        if (rows == null) {
            return 0;
        }
        int rowIndex = 1;
        Set<String> seen = new HashSet<>();
        for (HsPoolExportRowDto row : rows) {
            // 将证券各市场代码展开为独立市场行。
            List<String[]> marketRows = markets(row);
            for (String[] market : marketRows) {
                if (seen.add(market[0] + "|" + market[1])) {
                    Row line = sheet.createRow(rowIndex++);
                    line.createCell(0).setCellValue(market[1]);
                    line.createCell(1).setCellValue(row.getSecurityShortName() == null
                            ? "" : row.getSecurityShortName());
                    line.createCell(2).setCellValue(market[0]);
                    line.createCell(3).setCellValue(row.getAdjustReason() == null
                            ? "" : row.getAdjustReason());
                }
            }
        }
        return rowIndex - 1;
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
     * 格式化增量时间窗口。
     *
     * @param time 待格式化时间
     * @return yyyy-MM-dd HH:mm:ss 格式时间
     */
    private String formatTime(Date time) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(time);
    }

    /** 单次工作簿写入结果。 */
    private static class ExportResult {
        /** 导出文件绝对路径。 */
        private final String filePath;
        /** 工作簿 Sheet 数量。 */
        private final int sheetCount;
        /** 实际导出证券行数。 */
        private final int rowCount;

        /**
         * 构造工作簿写入结果。
         *
         * @param filePath 导出文件绝对路径
         * @param sheetCount 工作簿 Sheet 数量
         * @param rowCount 实际导出证券行数
         */
        private ExportResult(String filePath, int sheetCount, int rowCount) {
            this.filePath = filePath;
            this.sheetCount = sheetCount;
            this.rowCount = rowCount;
        }
    }
}
