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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Excel 导入解析工具（首 sheet，首行为表头）
 */
public final class ExcelImportHelper {

    /** 单元格格式化器（中文区域） */
    private static final DataFormatter FORMATTER = new DataFormatter(Locale.CHINA);

    /** 工具类禁止实例化 */
    private ExcelImportHelper() {
    }

    /**
     * 解析上传 Excel：返回每行「表头 → 单元格文本」映射，并附带 Excel 物理行号（1-based）键 __rowNo
     */
    public static List<Map<String, String>> parseFirstSheet(MultipartFile file, int maxRows) {
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
                headers.add(trimCell(headerRow.getCell(c)));
            }
            if (headers.isEmpty() || headers.stream().allMatch(String::isEmpty)) {
                throw new BizException("Excel 表头为空");
            }
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
                    String value = trimCell(row.getCell(c));
                    map.put(header, value);
                    if (value != null && !value.isEmpty()) {
                        allEmpty = false;
                    }
                }
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

    /** 读取单元格为文本 */
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
