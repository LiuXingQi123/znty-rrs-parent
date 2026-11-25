package com.znty.rrs.common.util;

import com.znty.rrs.exception.BizException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Excel 导入解析工具
 *
 * 读取第一张工作表，以首行为表头，并保留每条数据在原表中的行号，方便定位导入问题
 */
public final class ExcelImportHelper {

    /** 按中文区域设置读取单元格的显示文本 */
    private static final DataFormatter FORMATTER = new DataFormatter(Locale.CHINA);

    /** 工具类禁止实例化 */
    private ExcelImportHelper() {
    }

    /**
     * 按单元格显示文本解析上传 Excel 的第一张工作表
     *
     * @param file 上传的 Excel 文件
     * @param maxRows 非空数据行上限
     * @return 每行按表头保存单元格文本，并通过 {@code __rowNo} 记录从 1 开始的原表行号
     * @throws BizException 文件无效、行数超限或无法解析
     */
    public static List<Map<String, String>> parseFirstSheet(MultipartFile file, int maxRows) {
        // 普通导入继续读取单元格显示文本
        return parseFirstSheet(file, maxRows, Collections.emptySet());
    }

    /**
     * 解析第一张工作表，为指定数字列保留单元格实际数值
     *
     * 执行顺序：
     * 1. 校验上传文件非空，并且文件名以 xls 或 xlsx 扩展名结尾
     * 2. 读取第一张工作表的首行作为表头，工作表或有效表头缺失时终止解析
     * 3. 逐行按表头读取单元格，指定的数字列使用实际数值，其余使用显示文本
     * 4. 跳过空行，为有效行保留原表行号，超出行数上限或没有有效数据时终止解析
     *
     * @param file 上传文件
     * @param maxRows 非空数据行上限
     * @param numericValueHeaders 需读取实际数字值的列名
     * @return 按原表顺序返回有效数据行，每行附带 {@code __rowNo}
     * @throws BizException 文件、工作表或数据行无效，行数超限或读取失败
     */
    public static List<Map<String, String>> parseFirstSheet(MultipartFile file, int maxRows,
                                                          Set<String> numericValueHeaders) {
        // 1. 校验文件存在且扩展名受支持
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!originalName.endsWith(".xlsx") && !originalName.endsWith(".xls")) {
            throw new BizException("仅支持 xls / xlsx 文件");
        }
        try (InputStream in = file.getInputStream();
             Workbook workbook = originalName.endsWith(".xlsx")
                     ? new XSSFWorkbook(in) : new HSSFWorkbook(in)) {
            // 2. 读取首张工作表及其表头
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new BizException("Excel 无有效工作表");
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new BizException("Excel 表头为空");
            }
            List<String> headers = new ArrayList<>();
            short lastCell = headerRow.getLastCellNum();
            for (int c = 0; c < lastCell; c++) {
                // 按原列位置保留表头文本，便于后续匹配数据
                headers.add(trimCell(headerRow.getCell(c)));
            }
            if (headers.isEmpty() || headers.stream().allMatch(String::isEmpty)) {
                throw new BizException("Excel 表头为空");
            }
            // 3. 按表头读取各列数据，空表头对应的列跳过
            List<Map<String, String>> rows = new ArrayList<>();
            int firstData = sheet.getFirstRowNum() + 1;
            int lastRow = sheet.getLastRowNum();
            for (int r = firstData; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> map = new LinkedHashMap<>();
                boolean allEmpty = true;
                for (int c = 0; c < headers.size(); c++) {
                    String header = headers.get(c);
                    if (header == null || header.isEmpty()) {
                        continue;
                    }
                    // 指定数字列不使用可能四舍五入的单元格显示格式
                    String value = readCellValue(row.getCell(c), numericValueHeaders.contains(header));
                    map.put(header, value);
                    if (value != null && !value.isEmpty()) {
                        allEmpty = false;
                    }
                }
                // 4. 只计入有数据的行，并保留原表行号
                if (allEmpty) {
                    continue;
                }
                map.put("__rowNo", String.valueOf(r + 1));
                rows.add(map);
                if (rows.size() > maxRows) {
                    throw new BizException("导入行数不能超过 " + maxRows + " 行");
                }
            }
            if (rows.isEmpty()) {
                throw new BizException("Excel 无有效数据行");
            }
            return rows;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("解析 Excel 失败：" + e.getMessage());
        }
    }

    /**
     * 读取单元格值，按需保留数字单元格的实际数值
     *
     * 非指定数字列沿用显示文本，便于保留证券代码等原有格式
     *
     * @param cell 待读取的单元格，可为空
     * @param preserveNumericValue 业务数字列是否读取实际数值以避免显示格式舍入
     * @return 数字文本或去掉首尾空白的显示文本，空单元格或公式读取失败时返回空字符串
     */
    private static String readCellValue(Cell cell, boolean preserveNumericValue) {
        if (preserveNumericValue && cell != null && cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        // 文本、空值和公式继续采用既有读取规则
        return trimCell(cell);
    }

    /**
     * 读取单元格的显示文本并去掉首尾空白
     *
     * @param cell 待读取的单元格
     * @return 单元格文本，空单元格或公式读取失败时返回空字符串
     */
    private static String trimCell(Cell cell) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.FORMULA) {
            try {
                return FORMATTER.formatCellValue(cell).trim();
            } catch (Exception e) {
                return "";
            }
        }
        return FORMATTER.formatCellValue(cell).trim();
    }
}
