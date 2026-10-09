package com.znty.rrs.entity.stockpoolquery;

import lombok.Data;

/** 股票池查询 Excel 行。 */
@Data
public class StockPoolQueryExportDto {
    /** 股票名称。 */
    private String stockName;
    /** 股票代码。 */
    private String stockCode;
    /** 所属行业。 */
    private String industryName;
    /** 最新评级。 */
    private String latestRatingLabel;
    /** 上次评级。 */
    private String previousRatingLabel;
    /** 投资池。 */
    private String targetPoolName;
    /** 调整人。 */
    private String adjusterName;
    /** 入池时间。 */
    private String entryTime;
}
