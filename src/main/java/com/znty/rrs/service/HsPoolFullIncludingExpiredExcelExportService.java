package com.znty.rrs.service;

import org.springframework.stereotype.Service;

/** 恒生池包含已到期普通证券的全量 Excel 导出任务。 */
@Service
public class HsPoolFullIncludingExpiredExcelExportService extends AbstractHsPoolExcelExportService {
    /** 恒生池包含已到期数据的全量 Excel 导出任务编码。 */
    public static final String TASK_CODE = "hs_pool_full_including_expired_excel_export";

    /** {@inheritDoc} */
    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    /** {@inheritDoc} */
    @Override
    public String getParamHelp() {
        return "参数格式：可选 JSON 对象，例如 <code>{\"poolIds\":[15,16],\"exportEmptyPool\":true,\"outputDir\":\"D:/exports\"}</code>。\n"
                + "poolIds（导出池范围）：可选；仅填写叶子池 ID 数组，未填写或空数组时导出全部叶子池。\n"
                + "exportEmptyPool（是否生成空池 Sheet）：可选；默认 true，false 时跳过没有数据的池。\n"
                + "outputDir（导出根目录）：可选；未填写时使用 rrs.hs-pool-export.storage-path，任务会在其下创建 yyyyMMdd 日期目录和 bak 备份目录。\n"
                + "导出内容：当前已生效的非主体证券和 CRMW；普通证券包含已到期数据，CRMW 不校验到期日；恒生池名称为空时使用投资池完整名称，竖线可配置多个 Sheet 名。";
    }

    /** {@inheritDoc} */
    @Override
    protected boolean isIncrement() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    protected boolean includeExpiredForFullExport() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected String filePrefix() {
        return "hs_pool_full_including_expired";
    }
}
