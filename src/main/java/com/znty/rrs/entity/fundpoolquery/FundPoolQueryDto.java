package com.znty.rrs.entity.fundpoolquery;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/** 基金池查询返回对象。 */
@Data
public class FundPoolQueryDto {
    /** 当前池状态主键 ID。 */
    private Long id;
    /** 基金代码。 */
    private String fundCode;
    /** 当前池记录关联的调库日志 ID。 */
    private Long adjustLogId;
    /** 当前池记录关联的调库批次号。 */
    private String adjustBatchNo;
    /** 基金名称。 */
    private String fundName;
    /** 目标投资池 ID。 */
    private Long targetPoolId;
    /** 投资池名称。 */
    private String targetPoolName;
    /** 调整人。 */
    private String adjusterName;
    /** 入池时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date entryTime;
    /** 基金产品类型编码。 */
    private String securityType;
    /** 基金产品类型名称。 */
    private String securityTypeName;
    /** 基金经理。 */
    private String fundManagerNames;
    /** 基金管理人。 */
    private String fundAdministrator;
    /** 基金托管人。 */
    private String fundCustodian;
    /** 成立日期。 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date establishmentDate;
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
