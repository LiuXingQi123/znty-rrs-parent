package com.znty.rrs.service;

import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.schedule.HsPoolExportPoolDto;
import com.znty.rrs.entity.schedule.HsPoolExportRowDto;
import com.znty.rrs.mapper.HsPoolExcelExportMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
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

    /** 验证全量导出按叶子池建 Sheet、拆分市场并设置固定列宽。 */
    @Test
    public void fullExportShouldCreateOneSheetPerLeafPoolAndSplitMarkets() throws Exception {
        HsPoolExcelExportMapper exportMapper = mock(HsPoolExcelExportMapper.class);
        ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
        HsPoolFullExcelExportService service = new HsPoolFullExcelExportService();
        Path outputDir = Files.createTempDirectory("hs-pool-export-test");
        try {
            ReflectionTestUtils.setField(service, "hsPoolExcelExportMapper", exportMapper);
            ReflectionTestUtils.setField(service, "scheduledTaskMapper", taskMapper);
            ReflectionTestUtils.setField(service, "defaultOutputDir", outputDir.toString());
            // 构造全量任务配置。
            SysScheduledTaskBo task = task("恒生池全量数据导出");
            when(taskMapper.queryTaskByCode(HsPoolFullExcelExportService.TASK_CODE)).thenReturn(task);
            // 构造两个需要分别生成 Sheet 的叶子投资池。
            HsPoolExportPoolDto bondPool = pool(1L, "恒生债池");
            // 构造一个无证券数据的 ABS 叶子投资池。
            HsPoolExportPoolDto absPool = pool(2L, "恒生 ABS 池");
            when(exportMapper.queryExportPoolList()).thenReturn(Arrays.asList(bondPool, absPool));
            // 构造同时包含两个市场代码的证券。
            HsPoolExportRowDto exportRow = row();
            when(exportMapper.queryFullExportRowList(1L)).thenReturn(Collections.singletonList(exportRow));
            when(exportMapper.queryFullExportRowList(2L)).thenReturn(Collections.emptyList());

            ScheduledTaskResult result = service.execute();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getAffectedCount()).isEqualTo(2);
            String filePath = result.getMessage().substring(result.getMessage().indexOf('：') + 1);
            assertThat(Paths.get(filePath).isAbsolute()).isTrue();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new FileInputStream(filePath))) {
                assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
                assertThat(workbook.getSheet("恒生债池").getRow(0).getCell(0).getStringCellValue()).isEqualTo("证券代码");
                assertThat(workbook.getSheet("恒生债池").getColumnWidth(0)).isEqualTo(25 * 256);
                assertThat(workbook.getSheet("恒生债池").getRow(1).getCell(0).getStringCellValue()).isEqualTo("110001.IB");
                assertThat(workbook.getSheet("恒生债池").getRow(1).getCell(2).getStringCellValue()).isEqualTo("上海证券交易所");
                assertThat(workbook.getSheet("恒生债池").getRow(2).getCell(0).getStringCellValue()).isEqualTo("112001.IB");
                assertThat(workbook.getSheet("恒生 ABS 池").getLastRowNum()).isZero();
                assertThat(workbook.getSheet("恒生 ABS 池").getColumnWidth(0)).isEqualTo(25 * 256);
            }
        } finally {
            // 清理本用例生成的临时导出目录。
            deleteDirectory(outputDir);
        }
    }

    /** 验证增量任务首次执行时按全量数据建立初始文件。 */
    @Test
    public void firstIncrementExportShouldUseFullData() throws Exception {
        HsPoolExcelExportMapper exportMapper = mock(HsPoolExcelExportMapper.class);
        ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
        HsPoolIncrementExcelExportService service = new HsPoolIncrementExcelExportService();
        Path outputDir = Files.createTempDirectory("hs-pool-first-increment-test");
        try {
            ReflectionTestUtils.setField(service, "hsPoolExcelExportMapper", exportMapper);
            ReflectionTestUtils.setField(service, "scheduledTaskMapper", taskMapper);
            ReflectionTestUtils.setField(service, "defaultOutputDir", outputDir.toString());
            // 构造尚无成功执行历史的增量任务配置。
            SysScheduledTaskBo task = task("恒生池增量数据导出");
            when(taskMapper.queryTaskByCode(HsPoolIncrementExcelExportService.TASK_CODE)).thenReturn(task);
            // 构造首次全量导出的叶子投资池。
            HsPoolExportPoolDto bondPool = pool(1L, "恒生债池");
            when(exportMapper.queryExportPoolList()).thenReturn(Collections.singletonList(bondPool));
            // 构造首次全量导出的当前在池证券。
            HsPoolExportRowDto exportRow = row();
            when(exportMapper.queryFullExportRowList(1L)).thenReturn(Collections.singletonList(exportRow));

            ScheduledTaskResult result = service.execute();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getAffectedCount()).isEqualTo(2);
            verify(exportMapper).queryFullExportRowList(1L);
            verify(exportMapper, never()).queryIncrementExportRowList(any(Long.class), any(Date.class), any(Date.class));
        } finally {
            // 清理本用例生成的临时导出目录。
            deleteDirectory(outputDir);
        }
    }

    /** 验证恒生池名称重复时任务失败并列出冲突投资池。 */
    @Test
    public void duplicateHsPoolNameShouldFailWithConflictPools() throws Exception {
        HsPoolExcelExportMapper exportMapper = mock(HsPoolExcelExportMapper.class);
        ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
        HsPoolFullExcelExportService service = new HsPoolFullExcelExportService();
        Path outputDir = Files.createTempDirectory("hs-pool-duplicate-test");
        try {
            ReflectionTestUtils.setField(service, "hsPoolExcelExportMapper", exportMapper);
            ReflectionTestUtils.setField(service, "scheduledTaskMapper", taskMapper);
            ReflectionTestUtils.setField(service, "defaultOutputDir", outputDir.toString());
            // 构造全量任务配置。
            SysScheduledTaskBo task = task("恒生池全量数据导出");
            when(taskMapper.queryTaskByCode(HsPoolFullExcelExportService.TASK_CODE)).thenReturn(task);
            // 构造恒生池名称相同的两个叶子投资池。
            HsPoolExportPoolDto firstPool = pool(1L, "恒生债池");
            firstPool.setPoolName("债券池一");
            HsPoolExportPoolDto secondPool = pool(2L, "恒生债池");
            secondPool.setPoolName("债券池二");
            when(exportMapper.queryExportPoolList()).thenReturn(Arrays.asList(firstPool, secondPool));

            ScheduledTaskResult result = service.execute();

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("恒生池名称重复", "1-债券池一", "2-债券池二");
            verify(exportMapper, never()).queryFullExportRowList(any(Long.class));
        } finally {
            // 清理本用例生成的临时导出目录。
            deleteDirectory(outputDir);
        }
    }

    /** 验证后续增量导出使用上一次成功执行开始时间作为水位线。 */
    @Test
    public void incrementExportShouldUseLastSuccessStartTimeAsWatermark() throws Exception {
        HsPoolExcelExportMapper exportMapper = mock(HsPoolExcelExportMapper.class);
        ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
        HsPoolIncrementExcelExportService service = new HsPoolIncrementExcelExportService();
        Path outputDir = Files.createTempDirectory("hs-pool-increment-test");
        try {
            Date watermark = new Date(System.currentTimeMillis() - 60000L);
            ReflectionTestUtils.setField(service, "hsPoolExcelExportMapper", exportMapper);
            ReflectionTestUtils.setField(service, "scheduledTaskMapper", taskMapper);
            ReflectionTestUtils.setField(service, "defaultOutputDir", outputDir.toString());
            // 构造增量任务配置。
            SysScheduledTaskBo task = task("恒生池增量数据导出");
            when(taskMapper.queryTaskByCode(HsPoolIncrementExcelExportService.TASK_CODE)).thenReturn(task);
            when(taskMapper.queryLastSuccessStartTime(HsPoolIncrementExcelExportService.TASK_CODE)).thenReturn(watermark);
            // 构造增量导出的叶子投资池。
            HsPoolExportPoolDto bondPool = pool(1L, "恒生债池");
            when(exportMapper.queryExportPoolList()).thenReturn(Collections.singletonList(bondPool));
            // 构造增量时间窗口内仍在池的证券。
            HsPoolExportRowDto exportRow = row();
            when(exportMapper.queryIncrementExportRowList(eq(1L), eq(watermark), any(Date.class)))
                    .thenReturn(Collections.singletonList(exportRow));

            ScheduledTaskResult result = service.execute();

            assertThat(result.isSuccess()).isTrue();
            verify(exportMapper).queryIncrementExportRowList(eq(1L), eq(watermark), any(Date.class));
            verify(exportMapper, never()).queryFullExportRowList(any(Long.class));
        } finally {
            // 清理本用例生成的临时导出目录。
            deleteDirectory(outputDir);
        }
    }

    /**
     * 构造任务配置，使用默认导出目录。
     *
     * @param taskName 任务名称
     * @return 定时任务配置
     */
    private SysScheduledTaskBo task(String taskName) {
        SysScheduledTaskBo task = new SysScheduledTaskBo();
        task.setTaskName(taskName);
        return task;
    }

    /**
     * 构造叶子投资池。
     *
     * @param poolId 投资池 ID
     * @param hsPoolName 恒生池名称
     * @return 叶子投资池
     */
    private HsPoolExportPoolDto pool(Long poolId, String hsPoolName) {
        HsPoolExportPoolDto pool = new HsPoolExportPoolDto();
        pool.setPoolId(poolId);
        pool.setHsPoolName(hsPoolName);
        return pool;
    }

    /**
     * 构造同时拥有沪市和银行间市场代码的证券。
     *
     * @return 导出证券原始行
     */
    private HsPoolExportRowDto row() {
        HsPoolExportRowDto row = new HsPoolExportRowDto();
        row.setSecurityShortName("测试债");
        row.setWindCodeSh("110001.IB");
        row.setWindCodeNib("112001.IB");
        row.setAdjustReason("测试原因");
        return row;
    }

    /**
     * 清理测试临时导出目录。
     *
     * @param directory 临时目录
     * @throws Exception 目录遍历失败
     */
    private void deleteDirectory(Path directory) throws Exception {
        if (Files.exists(directory)) {
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }
}
