package com.znty.rrs.service;

import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.schedule.HsPoolExportPoolDto;
import com.znty.rrs.entity.schedule.HsPoolExportRowDto;
import com.znty.rrs.mapper.HsPoolExcelExportMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 恒生池 Excel 导出任务单元测试。 */
public class HsPoolExcelExportServiceTest {

    /** 验证全量导出按叶子池建 Sheet、使用五列表头、拆分市场并生成备份。 */
    @Test
    public void fullExportShouldCreateSheetsSplitMarketsAndBackupFile() throws Exception {
        HsPoolExcelExportMapper exportMapper = mock(HsPoolExcelExportMapper.class);
        ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
        HsPoolFullExcelExportService service = new HsPoolFullExcelExportService();
        Path outputDir = Files.createTempDirectory("hs-pool-export-test");
        try {
            inject(service, exportMapper, taskMapper, outputDir);
            SysScheduledTaskBo task = task("恒生池全量数据导出", null);
            when(taskMapper.queryTaskByCode(HsPoolFullExcelExportService.TASK_CODE)).thenReturn(task);
            HsPoolExportPoolDto bondPool = pool(1L, "恒生债池");
            HsPoolExportPoolDto emptyPool = pool(2L, "恒生空池");
            when(exportMapper.queryExportPoolList(null)).thenReturn(Arrays.asList(bondPool, emptyPool));
            HsPoolExportRowDto exportRow = row();
            when(exportMapper.queryFullExportRowList(1L)).thenReturn(Collections.singletonList(exportRow));
            when(exportMapper.queryFullExportRowList(2L)).thenReturn(Collections.emptyList());

            ScheduledTaskResult result = service.execute();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getAffectedCount()).isEqualTo(2);
            String filePath = result.getMessage().substring(result.getMessage().indexOf('：') + 1);
            assertThat(Paths.get(filePath).isAbsolute()).isTrue();
            assertThat(Paths.get(filePath).getFileName().toString()).startsWith("hs_pool_full_");
            assertThat(Paths.get(filePath).getParent().getFileName().toString()).matches("\\d{8}");
            assertThat(Paths.get(filePath).getParent().resolve("bak").resolve(Paths.get(filePath).getFileName()))
                    .exists();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new FileInputStream(filePath))) {
                assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
                assertThat(workbook.getSheet("恒生债池").getRow(0).getCell(0).getStringCellValue()).isEqualTo("证券名称");
                assertThat(workbook.getSheet("恒生债池").getRow(0).getCell(2).getStringCellValue()).isEqualTo("操作类型");
                assertThat(workbook.getSheet("恒生债池").getColumnWidth(0)).isEqualTo(25 * 256);
                assertThat(workbook.getSheet("恒生债池").getRow(1).getCell(0).getStringCellValue()).isEqualTo("测试债");
                assertThat(workbook.getSheet("恒生债池").getRow(1).getCell(1).getStringCellValue()).isEqualTo("110001.IB");
                assertThat(workbook.getSheet("恒生债池").getRow(1).getCell(3).getStringCellValue()).isEqualTo("上海证券交易所");
                assertThat(workbook.getSheet("恒生空池").getLastRowNum()).isZero();
            }
        } finally {
            deleteDirectory(outputDir);
        }
    }

    /** 验证首次增量使用配置时间作为窗口下界，并导出调出删除记录。 */
    @Test
    public void firstIncrementExportShouldUseConfiguredInitialWindow() throws Exception {
        HsPoolExcelExportMapper exportMapper = mock(HsPoolExcelExportMapper.class);
        ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
        HsPoolIncrementExcelExportService service = new HsPoolIncrementExcelExportService();
        Path outputDir = Files.createTempDirectory("hs-pool-first-increment-test");
        try {
            inject(service, exportMapper, taskMapper, outputDir);
            String initialText = "2026-08-01 00:00:00";
            Date initialTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(initialText);
            SysScheduledTaskBo task = task("恒生池增量数据导出",
                    "{\"initialStartTime\":\"" + initialText + "\"}");
            when(taskMapper.queryTaskByCode(HsPoolIncrementExcelExportService.TASK_CODE)).thenReturn(task);
            HsPoolExportPoolDto bondPool = pool(1L, "恒生债池");
            when(exportMapper.queryExportPoolList(null)).thenReturn(Collections.singletonList(bondPool));
            HsPoolExportRowDto exportRow = row();
            exportRow.setOperationType("删除");
            when(exportMapper.queryIncrementExportRowList(eq(1L), eq(initialTime), any(Date.class)))
                    .thenReturn(Collections.singletonList(exportRow));

            ScheduledTaskResult result = service.execute();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).contains("首次增量");
            verify(exportMapper).queryIncrementExportRowList(eq(1L), eq(initialTime), any(Date.class));
            verify(exportMapper, never()).queryFullExportRowList(any(Long.class));
            String filePath = result.getMessage().substring(result.getMessage().indexOf('：') + 1);
            try (XSSFWorkbook workbook = new XSSFWorkbook(new FileInputStream(filePath))) {
                assertThat(workbook.getSheet("恒生债池").getRow(1).getCell(2).getStringCellValue()).isEqualTo("删除");
            }
        } finally {
            deleteDirectory(outputDir);
        }
    }

    /** 验证恒生池名称为空时使用完整池名，竖线拆分多 Sheet。 */
    @Test
    public void blankHsPoolNameShouldFallbackAndPipeShouldCreateMultipleSheets() throws Exception {
        HsPoolExcelExportMapper exportMapper = mock(HsPoolExcelExportMapper.class);
        ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
        HsPoolFullExcelExportService service = new HsPoolFullExcelExportService();
        Path outputDir = Files.createTempDirectory("hs-pool-name-test");
        try {
            inject(service, exportMapper, taskMapper, outputDir);
            when(taskMapper.queryTaskByCode(HsPoolFullExcelExportService.TASK_CODE))
                    .thenReturn(task("恒生池全量数据导出", null));
            HsPoolExportPoolDto fallbackPool = pool(1L, null);
            fallbackPool.setPoolFullName("投资池/债券池");
            HsPoolExportPoolDto multiNamePool = pool(2L, "恒生池A|恒生池B");
            when(exportMapper.queryExportPoolList(null)).thenReturn(Arrays.asList(fallbackPool, multiNamePool));
            when(exportMapper.queryFullExportRowList(any(Long.class))).thenReturn(Collections.emptyList());

            ScheduledTaskResult result = service.execute();

            assertThat(result.isSuccess()).isTrue();
            String filePath = result.getMessage().substring(result.getMessage().indexOf('：') + 1);
            try (XSSFWorkbook workbook = new XSSFWorkbook(new FileInputStream(filePath))) {
                assertThat(workbook.getSheet("投资池_债券池")).isNotNull();
                assertThat(workbook.getSheet("恒生池A")).isNotNull();
                assertThat(workbook.getSheet("恒生池B")).isNotNull();
            }
        } finally {
            deleteDirectory(outputDir);
        }
    }

    /** 验证同名 Sheet 合并而不失败，并在过程日志标注冲突池。 */
    @Test
    public void duplicateHsPoolNameShouldMergeAndWriteWarning() throws Exception {
        HsPoolExcelExportMapper exportMapper = mock(HsPoolExcelExportMapper.class);
        ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
        HsPoolFullExcelExportService service = new HsPoolFullExcelExportService();
        Path outputDir = Files.createTempDirectory("hs-pool-duplicate-test");
        try {
            inject(service, exportMapper, taskMapper, outputDir);
            when(taskMapper.queryTaskByCode(HsPoolFullExcelExportService.TASK_CODE))
                    .thenReturn(task("恒生池全量数据导出", null));
            HsPoolExportPoolDto firstPool = pool(1L, "恒生债池");
            firstPool.setPoolName("债券池一");
            HsPoolExportPoolDto secondPool = pool(2L, "恒生债池");
            secondPool.setPoolName("债券池二");
            when(exportMapper.queryExportPoolList(null)).thenReturn(Arrays.asList(firstPool, secondPool));
            when(exportMapper.queryFullExportRowList(1L)).thenReturn(Collections.singletonList(row()));
            HsPoolExportRowDto secondRow = row();
            secondRow.setSecurityShortName("测试债二");
            secondRow.setWindCodeSh("110002.SH");
            secondRow.setWindCodeNib("112002.IB");
            when(exportMapper.queryFullExportRowList(2L)).thenReturn(Collections.singletonList(secondRow));

            ScheduledTaskResult result = service.execute();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getDetailLog()).contains("Sheet 名称重复", "1-债券池一", "2-债券池二");
            String filePath = result.getMessage().substring(result.getMessage().indexOf('：') + 1);
            try (XSSFWorkbook workbook = new XSSFWorkbook(new FileInputStream(filePath))) {
                assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
                assertThat(workbook.getSheet("恒生债池").getLastRowNum()).isEqualTo(4);
            }
        } finally {
            deleteDirectory(outputDir);
        }
    }

    /** 验证后续增量继续使用上一次成功执行开始时间作为水位线。 */
    @Test
    public void incrementExportShouldUseLastSuccessStartTimeAsWatermark() throws Exception {
        HsPoolExcelExportMapper exportMapper = mock(HsPoolExcelExportMapper.class);
        ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
        HsPoolIncrementExcelExportService service = new HsPoolIncrementExcelExportService();
        Path outputDir = Files.createTempDirectory("hs-pool-increment-test");
        try {
            Date watermark = new Date(System.currentTimeMillis() - 60000L);
            inject(service, exportMapper, taskMapper, outputDir);
            when(taskMapper.queryTaskByCode(HsPoolIncrementExcelExportService.TASK_CODE))
                    .thenReturn(task("恒生池增量数据导出", null));
            when(taskMapper.queryLastSuccessStartTime(HsPoolIncrementExcelExportService.TASK_CODE))
                    .thenReturn(watermark);
            HsPoolExportPoolDto bondPool = pool(1L, "恒生债池");
            when(exportMapper.queryExportPoolList(null)).thenReturn(Collections.singletonList(bondPool));
            when(exportMapper.queryIncrementExportRowList(eq(1L), eq(watermark), any(Date.class)))
                    .thenReturn(Collections.singletonList(row()));

            ScheduledTaskResult result = service.execute();

            assertThat(result.isSuccess()).isTrue();
            verify(exportMapper).queryIncrementExportRowList(eq(1L), eq(watermark), any(Date.class));
        } finally {
            deleteDirectory(outputDir);
        }
    }

    /** 验证 poolIds 和关闭空池参数会传递池范围并跳过空 Sheet。 */
    @Test
    public void configuredPoolIdsAndDisabledEmptyPoolShouldLimitSheets() throws Exception {
        HsPoolExcelExportMapper exportMapper = mock(HsPoolExcelExportMapper.class);
        ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
        HsPoolFullExcelExportService service = new HsPoolFullExcelExportService();
        Path outputDir = Files.createTempDirectory("hs-pool-params-test");
        try {
            inject(service, exportMapper, taskMapper, outputDir);
            when(taskMapper.queryTaskByCode(HsPoolFullExcelExportService.TASK_CODE)).thenReturn(task(
                    "恒生池全量数据导出", "{\"poolIds\":[15,16],\"exportEmptyPool\":false}"));
            HsPoolExportPoolDto emptyPool = pool(15L, "空池");
            when(exportMapper.queryExportPoolList(Arrays.asList(15L, 16L)))
                    .thenReturn(Collections.singletonList(emptyPool));
            when(exportMapper.queryFullExportRowList(15L)).thenReturn(Collections.emptyList());

            ScheduledTaskResult result = service.execute();

            assertThat(result.isSuccess()).isTrue();
            verify(exportMapper).queryExportPoolList(Arrays.asList(15L, 16L));
            assertThat(result.getDetailLog()).contains("已跳过 Sheet");
        } finally {
            deleteDirectory(outputDir);
        }
    }

    /** 验证交易日列表为空时暂不启用过滤，增量任务仍正常执行。 */
    @Test
    public void incrementExportShouldContinueWhenTradeDayListIsEmpty() throws Exception {
        HsPoolExcelExportMapper exportMapper = mock(HsPoolExcelExportMapper.class);
        ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
        HsPoolIncrementExcelExportService service = new HsPoolIncrementExcelExportService();
        Path outputDir = Files.createTempDirectory("hs-pool-trade-day-test");
        try {
            Date watermark = new Date(System.currentTimeMillis() - 60000L);
            inject(service, exportMapper, taskMapper, outputDir);
            when(taskMapper.queryTaskByCode(HsPoolIncrementExcelExportService.TASK_CODE))
                    .thenReturn(task("恒生池增量数据导出", null));
            when(taskMapper.queryLastSuccessStartTime(HsPoolIncrementExcelExportService.TASK_CODE))
                    .thenReturn(watermark);
            when(exportMapper.queryExportPoolList(null)).thenReturn(Collections.singletonList(pool(1L, "恒生债池")));
            when(exportMapper.queryIncrementExportRowList(eq(1L), eq(watermark), any(Date.class)))
                    .thenReturn(Collections.singletonList(row()));

            ScheduledTaskResult result = service.execute();

            assertThat(result.isSuccess()).isTrue();
            verify(exportMapper).queryIncrementExportRowList(eq(1L), eq(watermark), any(Date.class));
        } finally {
            deleteDirectory(outputDir);
        }
    }

    /** 验证手动导出直接返回可下载的 Base64 工作簿，不写服务器目录。 */
    @Test
    public void manualExportShouldReturnDownloadableWorkbook() throws Exception {
        HsPoolExcelExportMapper exportMapper = mock(HsPoolExcelExportMapper.class);
        ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
        HsPoolFullExcelExportService service = new HsPoolFullExcelExportService();
        Path outputDir = Files.createTempDirectory("hs-pool-manual-test");
        try {
            inject(service, exportMapper, taskMapper, outputDir);
            when(exportMapper.queryExportPoolList(Collections.singletonList(15L)))
                    .thenReturn(Collections.singletonList(pool(15L, "恒生债池")));
            when(exportMapper.queryFullExportRowList(15L)).thenReturn(Collections.singletonList(row()));
            Date endTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2026-08-31 10:30:00");

            CommonFileDto file = service.exportManual(Collections.singletonList(15L), null, endTime);

            assertThat(file.getFileName()).isEqualTo("hs_pool_full_20260831_103000.xlsx");
            assertThat(file.getContentType())
                    .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            byte[] bytes = Base64.getDecoder().decode(file.getContentBase64());
            assertThat(file.getFileSize()).isEqualTo((long) bytes.length);
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                assertThat(workbook.getSheet("恒生债池").getRow(0).getCell(0).getStringCellValue())
                        .isEqualTo("证券名称");
                assertThat(workbook.getSheet("恒生债池").getLastRowNum()).isEqualTo(2);
            }
            try (Stream<Path> paths = Files.list(outputDir)) {
                assertThat(paths.count()).isZero();
            }
        } finally {
            deleteDirectory(outputDir);
        }
    }

    /** 验证全量 SQL 仅过滤普通证券到期日，不过滤 CRMW 到期日。 */
    @Test
    public void fullExportSqlShouldNotFilterCrmwMaturityDate() throws Exception {
        Path mapperPath = Paths.get("src/main/resources/mapper/HsPoolExcelExportMapper.xml");
        String mapperXml = new String(Files.readAllBytes(mapperPath), StandardCharsets.UTF_8);
        int crmwBlockStart = mapperXml.indexOf("<!-- CRMW 当前状态");
        int crmwBlockEnd = mapperXml.indexOf(") export_row", crmwBlockStart);

        assertThat(crmwBlockStart).isGreaterThanOrEqualTo(0);
        assertThat(crmwBlockEnd).isGreaterThan(crmwBlockStart);
        assertThat(mapperXml.substring(crmwBlockStart, crmwBlockEnd)).doesNotContain("maturity_date");
    }

    /** 注入任务测试依赖。 */
    private void inject(AbstractHsPoolExcelExportService service, HsPoolExcelExportMapper exportMapper,
                        ScheduledTaskMapper taskMapper, Path outputDir) {
        ReflectionTestUtils.setField(service, "hsPoolExcelExportMapper", exportMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "defaultOutputDir", outputDir.toString());
    }

    /** 构造任务配置。 */
    private SysScheduledTaskBo task(String taskName, String paramJson) {
        SysScheduledTaskBo task = new SysScheduledTaskBo();
        task.setTaskName(taskName);
        task.setParamJson(paramJson);
        return task;
    }

    /** 构造叶子投资池。 */
    private HsPoolExportPoolDto pool(Long poolId, String hsPoolName) {
        HsPoolExportPoolDto pool = new HsPoolExportPoolDto();
        pool.setPoolId(poolId);
        pool.setPoolName("池" + poolId);
        pool.setPoolFullName("投资池/池" + poolId);
        pool.setHsPoolName(hsPoolName);
        return pool;
    }

    /** 构造同时拥有沪市和银行间市场代码的证券。 */
    private HsPoolExportRowDto row() {
        HsPoolExportRowDto row = new HsPoolExportRowDto();
        row.setSecurityShortName("测试债");
        row.setWindCodeSh("110001.IB");
        row.setWindCodeNib("112001.IB");
        return row;
    }

    /** 清理测试临时导出目录。 */
    private void deleteDirectory(Path directory) throws Exception {
        if (Files.exists(directory)) {
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }
}
