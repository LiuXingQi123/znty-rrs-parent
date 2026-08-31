package com.znty.rrs.service;

import org.springframework.stereotype.Service;

/** 恒生池增量 Excel 导出任务。 */
@Service
public class HsPoolIncrementExcelExportService extends AbstractHsPoolExcelExportService {
    /** 恒生池增量 Excel 导出任务编码。 */
    public static final String TASK_CODE = "hs_pool_increment_excel_export";

    /** {@inheritDoc} */
    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    /** {@inheritDoc} */
    @Override
    public String getParamHelp() {
        return "参数格式：JSON 对象，例如 <code>{\"poolIds\":[15,16],\"exportEmptyPool\":true,\"initialStartTime\":\"2026-08-01 00:00:00\",\"outputDir\":\"D:/exports\"}</code>。\n"
                + "poolIds（导出池范围）：可选；仅填写叶子池 ID 数组，未填写或空数组时导出全部叶子池。\n"
                + "exportEmptyPool（是否生成空池 Sheet）：可选；默认 true，false 时跳过没有数据的池。\n"
                + "initialStartTime（首次时间下界）：首次执行必填；格式 yyyy-MM-dd HH:mm:ss，后续改用上次成功执行开始时间。\n"
                + "outputDir（导出根目录）：可选；未填写时使用 rrs.hs-pool-export.storage-path，任务会在其下创建 yyyyMMdd 日期目录和 bak 备份目录。\n"
                + "导出内容：导出时间窗口内审批通过的调入和调出事件；调出记录的操作类型为“删除”，不要求证券当前仍在池。交易日列表入口已预留，当前列表为空，因此交易日和非交易日均正常执行。";
    }

    /** {@inheritDoc} */
    @Override
    protected boolean isIncrement() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected String filePrefix() {
        return "hs_pool_increment";
    }
}
