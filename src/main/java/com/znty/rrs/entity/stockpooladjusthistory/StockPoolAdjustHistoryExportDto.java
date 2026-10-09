package com.znty.rrs.entity.stockpooladjusthistory;

import lombok.Data;

/** 股票池调整历史 Excel 行。 */
@Data
public class StockPoolAdjustHistoryExportDto {
    /** 调整人。 */
    private String adjusterName;
    /** 提交日期。 */
    private String submitTime;
    /** 证券名称。 */
    private String stockName;
    /** 证券代码。 */
    private String stockCode;
    /** 行业。 */
    private String industryName;
    /** 调整类型。 */
    private String adjustType;
    /** 调整方向。 */
    private String adjustMode;
    /** 投资池。 */
    private String targetPoolPath;
    /** 审核状态。 */
    private String auditStatusLabel;
}
