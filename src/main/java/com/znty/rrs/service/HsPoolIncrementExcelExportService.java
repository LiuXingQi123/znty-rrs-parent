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
        return "参数格式：可选 JSON 对象，例如 <code>{\"outputDir\":\"D:/exports\"}</code>。\n"
                + "outputDir（导出目录）：可选；未填写时使用 rrs.hs-pool-export.storage-path。\n"
                + "导出内容：首次按全量导出；后续仅导出上次成功执行开始时间后的已生效调入记录，调出记录不导出。";
    }

    /** {@inheritDoc} */
    @Override
    protected boolean isIncrement() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected String filePrefix() {
        return "恒生池增量";
    }
}
