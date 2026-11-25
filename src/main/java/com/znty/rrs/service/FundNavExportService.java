package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.fundnavexport.FundNavExportReq;
import com.znty.rrs.entity.fundnavexport.FundNavFundDto;
import com.znty.rrs.entity.fundnavexport.FundNavRecordDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FundNavExportMapper;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import javax.annotation.Resource;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * 查询基金基础信息并导出逐日净值 Excel
 *
 * 页面先筛选基金，再提交选中的基金代码和要导出的净值日期范围
 * 按基金组织逐日净值数据，基金名称重复或含非法字符时生成可用且不重名的工作表名
 */
@Service
public class FundNavExportService {
    /** 基金净值导出数据访问组件 */
    @Resource
    private FundNavExportMapper fundNavExportMapper;

    /**
     * 分页查询基金基础信息
     *
     * 执行顺序：
     * 1. 按请求的页码和每页条数开启分页，并用筛选条件查询记录
     * 2. 将本页记录和查询总条数一起返回
     *
     * @param req 基金筛选及分页条件
     * @return 分页基金基础信息列表
     */
    public PageResult<FundNavFundDto> queryFundNavFundPage(FundNavExportReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<FundNavFundDto> list = fundNavExportMapper.queryFundNavFundPage(req);
        PageInfo<FundNavFundDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 导出所选基金在指定日期范围内的逐日单位净值
     *
     * 执行顺序：
     * 1. 校验所选基金代码及净值日期范围
     * 2. 查询选中基金列表
     * 3. 部分所选基金已失效或基金代码对应的基础信息不唯一时终止处理
     * 4. 查询基金净值记录列表
     * 5. 构造 Excel 允许且不重复的工作表名称
     *
     * @param req 所选基金代码及净值日期范围
     * @return 基金净值 Excel 文件信息
     */
    public CommonFileDto exportFundNavExcel(FundNavExportReq req) {
        // 校验所选基金代码及净值日期范围
        validateExportRequest(req);
        List<FundNavFundDto> funds = fundNavExportMapper.querySelectedFundList(req);
        if (funds.size() != req.getFundCodes().size()) {
            throw new BizException("部分所选基金已失效或基金代码对应的基础信息不唯一，请重新查询");
        }
        List<FundNavRecordDto> navRecords = fundNavExportMapper.queryFundNavRecords(req);
        Map<String, List<FundNavRecordDto>> navByFundCode = new HashMap<>();
        for (FundNavRecordDto record : navRecords) {
            List<FundNavRecordDto> fundRecords = navByFundCode.get(record.getFundCode());
            if (fundRecords == null) {
                fundRecords = new ArrayList<>();
                navByFundCode.put(record.getFundCode(), fundRecords);
            }
            fundRecords.add(record);
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            CellStyle navStyle = workbook.createCellStyle();
            navStyle.setDataFormat(workbook.createDataFormat().getFormat("0.000000"));
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8"));

            for (FundNavFundDto fund : funds) {
                // 基金名称重复或含非法字符时生成合法且唯一的工作表名称
                String sheetName = buildUniqueSheetName(workbook, fund.getFundName(), fund.getFundCode());
                Sheet sheet = workbook.createSheet(sheetName);
                sheet.setColumnWidth(0, 14 * 256);
                sheet.setColumnWidth(1, 16 * 256);
                Row header = sheet.createRow(0);
                Cell dateHeader = header.createCell(0);
                dateHeader.setCellValue("日期");
                dateHeader.setCellStyle(headerStyle);
                Cell navHeader = header.createCell(1);
                navHeader.setCellValue("基金净值");
                navHeader.setCellStyle(headerStyle);

                List<FundNavRecordDto> fundRecords = navByFundCode.get(fund.getFundCode());
                if (fundRecords == null) {
                    continue;
                }
                Set<String> exportedDates = new HashSet<>();
                int rowIndex = 1;
                for (FundNavRecordDto record : fundRecords) {
                    String dateText = dateFormat.format(record.getTradeDate());
                    if (!exportedDates.add(dateText)) {
                        throw new BizException("基金 " + fund.getFundCode() + " 在 " + dateText + " 存在多条净值记录");
                    }
                    Row row = sheet.createRow(rowIndex++);
                    row.createCell(0).setCellValue(dateText);
                    Cell navCell = row.createCell(1);
                    BigDecimal unitNav = record.getUnitNav();
                    if (unitNav != null) {
                        navCell.setCellValue(unitNav.doubleValue());
                    }
                    navCell.setCellStyle(navStyle);
                }
            }

            workbook.write(outputStream);
            byte[] bytes = outputStream.toByteArray();
            CommonFileDto file = new CommonFileDto();
            file.setFileName("基金净值_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx");
            file.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            file.setFileSize((long) bytes.length);
            file.setContentBase64(Base64.getEncoder().encodeToString(bytes));
            return file;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("生成基金净值 Excel 失败：" + e.toString());
        }
    }

    /**
     * 校验基金选择和日期范围，并标准化基金代码
     *
     * 执行顺序：
     * 1. 去掉空白基金代码并去重，结果为空时终止处理
     * 2. 按 yyyy-MM-dd 解析净值起止日期，未填写、格式错误或起始日晚于结束日时终止处理
     * 3. 将清理后的基金代码和规范化日期写回请求
     *
     * @param req 基金代码及净值日期范围条件
     */
    private void validateExportRequest(FundNavExportReq req) {
        if (req.getFundCodes() == null || req.getFundCodes().isEmpty()) {
            throw new BizException("请先勾选需要导出净值的基金");
        }
        List<String> fundCodes = new ArrayList<>();
        Set<String> uniqueFundCodes = new HashSet<>();
        for (String fundCode : req.getFundCodes()) {
            if (fundCode != null && !fundCode.trim().isEmpty() && uniqueFundCodes.add(fundCode.trim())) {
                fundCodes.add(fundCode.trim());
            }
        }
        if (fundCodes.isEmpty()) {
            throw new BizException("请先勾选需要导出净值的基金");
        }
        req.setFundCodes(fundCodes);

        if (req.getNavDateStart() == null || req.getNavDateStart().trim().isEmpty()
                || req.getNavDateEnd() == null || req.getNavDateEnd().trim().isEmpty()) {
            throw new BizException("请选择净值日期范围");
        }
        LocalDate startDate;
        LocalDate endDate;
        try {
            startDate = LocalDate.parse(req.getNavDateStart().trim());
            endDate = LocalDate.parse(req.getNavDateEnd().trim());
        } catch (DateTimeParseException e) {
            throw new BizException("净值日期格式必须为 yyyy-MM-dd");
        }
        if (startDate.isAfter(endDate)) {
            throw new BizException("净值开始日期不能晚于结束日期");
        }
        req.setNavDateStart(startDate.toString());
        req.setNavDateEnd(endDate.toString());
    }

    /**
     * 构造 Excel 允许且不重复的工作表名称
     *
     * 执行顺序：
     * 1. 以基金名称为首选，缺失时用基金代码，替换 Excel 不允许的字符
     * 2. 名称已被使用时追加基金代码和必要的序号，直到找到可用名称
     *
     * @param workbook 当前 Excel 工作簿
     * @param fundName 基金名称
     * @param fundCode 基金代码
     * @return 合法且唯一的工作表名称
     */
    private String buildUniqueSheetName(XSSFWorkbook workbook, String fundName, String fundCode) {
        String rawName = fundName == null || fundName.trim().isEmpty() ? fundCode : fundName.trim();
        String baseName = WorkbookUtil.createSafeSheetName(rawName == null || rawName.trim().isEmpty() ? "基金" : rawName);
        if (baseName == null || baseName.trim().isEmpty()) {
            baseName = "基金";
        }
        if (workbook.getSheet(baseName) == null) {
            return baseName;
        }
        String safeCode = WorkbookUtil.createSafeSheetName(fundCode == null ? "基金" : fundCode);
        if (safeCode.length() > 20) {
            safeCode = safeCode.substring(0, 20);
        }
        int suffixIndex = 1;
        while (true) {
            String suffix = "_" + safeCode + (suffixIndex == 1 ? "" : "_" + suffixIndex);
            int prefixLength = Math.max(1, 31 - suffix.length());
            String candidate = baseName.substring(0, Math.min(baseName.length(), prefixLength)) + suffix;
            if (workbook.getSheet(candidate) == null) {
                return candidate;
            }
            suffixIndex++;
        }
    }
}
