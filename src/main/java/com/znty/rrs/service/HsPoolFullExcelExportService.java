package com.znty.rrs.service;

import org.springframework.stereotype.Service;

/** 恒生池全量 Excel 导出任务。 */
@Service
public class HsPoolFullExcelExportService extends AbstractHsPoolExcelExportService {
    /** 恒生池全量 Excel 导出任务编码。 */
    public static final String TASK_CODE = "hs_pool_full_excel_export";

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
                + "导出内容：每个配置恒生池名称的叶子投资池一个 Sheet，导出当前在库普通债券和 ABS。";
    }

    /** {@inheritDoc} */
    @Override
    protected boolean isIncrement() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    protected String filePrefix() {
        return "恒生池全量";
    }
}
