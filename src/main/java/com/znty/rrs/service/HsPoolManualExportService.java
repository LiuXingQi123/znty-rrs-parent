package com.znty.rrs.service;

import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.hspoolexport.HsPoolManualExportReq;
import com.znty.rrs.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 按页面选择的投资池手动导出恒生格式文件
 *
 * 按请求区分全量和增量导出，沿用 {@link AbstractHsPoolExcelExportService} 的工作簿生成逻辑
 * 本次生成的文件直接返回给页面下载，供用户按需取数
 */
@Service
public class HsPoolManualExportService {
    /** 日期时间格式 */
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 全量模式 */
    private static final String MODE_FULL = "full";

    /** 增量模式 */
    private static final String MODE_INCREMENT = "increment";

    /** 恒生池全量导出实现 */
    @Resource
    private HsPoolFullExcelExportService fullExportService;

    /** 恒生池增量导出实现 */
    @Resource
    private HsPoolIncrementExcelExportService incrementExportService;

    /**
     * 按页面条件生成恒生格式 Excel
     *
     * 执行顺序：
     * 1. 检查导出请求、开始时间和导出模式
     * 2. 解析开始和结束时间，结束时间缺省时使用当前时间
     * 3. 检查结束时间不早于开始时间
     * 4. 按指定的全量或增量模式生成恒生格式 Excel
     *
     * @param req 手动导出条件
     * @return 可下载文件
     */
    public CommonFileDto exportHsPoolExcel(HsPoolManualExportReq req) {
        // 1. 检查导出请求、开始时间和导出模式
        if (req == null) {
            throw new BizException("导出参数不能为空");
        }
        if (!StringUtils.hasText(req.getStartTime())) {
            throw new BizException("开始时间不能为空");
        }
        if (!MODE_FULL.equals(req.getExportMode()) && !MODE_INCREMENT.equals(req.getExportMode())) {
            throw new BizException("导出模式必须选择增量或全量");
        }
        // 2. 解析开始和结束时间，结束时间缺省时使用当前时间
        Date startTime = parseTime(req.getStartTime(), "开始时间");
        // 严格解析页面日期时间
        Date endTime = StringUtils.hasText(req.getEndTime()) ? parseTime(req.getEndTime(), "结束时间") : new Date();
        // 3. 检查结束时间不早于开始时间
        if (endTime.before(startTime)) {
            throw new BizException("结束时间不能早于开始时间");
        }
        // 4. 按指定的全量或增量模式生成恒生格式 Excel
        try {
            // 根据导出模式复用对应定时任务的数据口径和工作簿格式
            return MODE_INCREMENT.equals(req.getExportMode())
                    ? incrementExportService.exportManual(req.getPoolIds(), startTime, endTime)
                    : fullExportService.exportManual(req.getPoolIds(), startTime, endTime);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("生成恒生格式文件失败：" + e.getMessage());
        }
    }

    /**
     * 严格解析页面日期时间
     *
     * 执行顺序：
     * 1. 按约定格式严格解析去掉首尾空白的时间文本
     * 2. 将解析值转回文本核对格式，不一致或解析失败时报错
     *
     * @param value 待统一空值的文本
     * @param fieldName 字段名称
     * @return 时间对象
     */
    private Date parseTime(String value, String fieldName) {
        SimpleDateFormat formatter = new SimpleDateFormat(DATE_TIME_PATTERN);
        formatter.setLenient(false);
        String normalizedValue = value.trim();
        try {
            Date parsedTime = formatter.parse(normalizedValue);
            if (!normalizedValue.equals(formatter.format(parsedTime))) {
                throw new ParseException("日期时间格式不完整", 0);
            }
            return parsedTime;
        } catch (ParseException e) {
            throw new BizException(fieldName + "格式错误，应为 " + DATE_TIME_PATTERN);
        }
    }
}
