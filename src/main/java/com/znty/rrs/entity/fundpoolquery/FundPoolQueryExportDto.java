package com.znty.rrs.entity.fundpoolquery;

import java.math.BigDecimal;
import lombok.Data;

/** 基金池查询 Excel 导出行。 */
@Data
public class FundPoolQueryExportDto {
    /** 基金名称。 */
    private String fundName;
    /** 基金代码。 */
    private String fundCode;
    /** 投资池名称。 */
    private String targetPoolName;
    /** 调整人。 */
    private String adjusterName;
    /** 入池时间。 */
    private String entryTime;
    /** 基金类型。 */
    private String securityTypeName;
    /** 基金经理。 */
    private String fundManagerNames;
    /** 基金管理人。 */
    private String fundAdministrator;
    /** 基金托管人。 */
    private String fundCustodian;
    /** 成立日期。 */
    private String establishmentDate;
    /** 发行期限。 */
    private String issueTerm;
    /** 最新单位净值。 */
    private BigDecimal latestNav;
    /** 累计净值。 */
    private BigDecimal accumulatedNav;
    /** 日万份收益。 */
    private BigDecimal dailyTenThousandIncome;
    /** 7 日年化收益率（百分数）。 */
    private BigDecimal sevenDayAnnualizedYield;
    /** 最新规模（亿元）。 */
    private BigDecimal latestScale;
    /** 昨收盘价。 */
    private BigDecimal previousClosePrice;
    /** 折溢价率（百分数）。 */
    private BigDecimal premiumDiscountRate;
}
