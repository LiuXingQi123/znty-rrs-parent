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

/** 恒生格式手动导出服务。 */
@Service
public class HsPoolManualExportService {
    /** 日期时间格式。 */
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    /** 全量模式。 */
    private static final String MODE_FULL = "full";
    /** 增量模式。 */
    private static final String MODE_INCREMENT = "increment";

    /** 恒生池全量导出实现。 */
    @Resource
    private HsPoolFullExcelExportService fullExportService;
    /** 恒生池增量导出实现。 */
    @Resource
    private HsPoolIncrementExcelExportService incrementExportService;

    /**
     * 按页面条件生成恒生格式 Excel。
     *
     * @param req 手动导出条件
     * @return 可下载文件
     */
    public CommonFileDto exportHsPoolExcel(HsPoolManualExportReq req) {
        if (req == null) {
            throw new BizException("导出参数不能为空");
        }
        if (!StringUtils.hasText(req.getStartTime())) {
            throw new BizException("开始时间不能为空");
        }
        if (!MODE_FULL.equals(req.getExportMode()) && !MODE_INCREMENT.equals(req.getExportMode())) {
            throw new BizException("导出模式必须选择增量或全量");
        }
        Date startTime = parseTime(req.getStartTime(), "开始时间");
        Date endTime = StringUtils.hasText(req.getEndTime()) ? parseTime(req.getEndTime(), "结束时间") : new Date();
        if (endTime.before(startTime)) {
            throw new BizException("结束时间不能早于开始时间");
        }
        try {
            // 根据导出模式复用对应定时任务的数据口径和工作簿格式。
            return MODE_INCREMENT.equals(req.getExportMode())
                    ? incrementExportService.exportManual(req.getPoolIds(), startTime, endTime)
                    : fullExportService.exportManual(req.getPoolIds(), startTime, endTime);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("生成恒生格式文件失败：" + e.getMessage());
        }
    }

    /** 严格解析页面日期时间。 */
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
