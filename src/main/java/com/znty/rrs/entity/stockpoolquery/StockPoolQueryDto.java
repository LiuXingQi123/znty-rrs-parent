package com.znty.rrs.entity.stockpoolquery;

import lombok.Data;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** 股票池当前生效状态及实时基础信息。 */
@Data
public class StockPoolQueryDto {
    /** 当前池状态 ID。 */
    private Long id;
    /** 股票代码。 */
    private String stockCode;
    /** 股票名称。 */
    private String stockName;
    /** 股票简称。 */
    private String stockShortName;
    /** 行业编码。 */
    private String industryCode;
    /** 所属行业。 */
    private String industryName;
    /** 最新有效评级编码。 */
    private String latestRating;
    /** 上次有效评级编码。 */
    private String previousRating;
    /** 投资池 ID。 */
    private Long targetPoolId;
    /** 投资池完整路径。 */
    private String targetPoolName;
    /** 调整人。 */
    private String adjusterName;
    /** 入池时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date entryTime;
    /** 生效来源调整日志 ID。 */
    private Long adjustLogId;
    /** 调整批次号。 */
    private String adjustBatchNo;
    /** 当前用户有效自选记录 ID。 */
    private Long myStockPoolId;
    /** 股票品种编码。 */
    private String securityType;
    /** 股票市场编码。 */
    private String marketCode;
    /** 昨收。 */
    private BigDecimal previousClosePrice;
    /** 最高。 */
    private BigDecimal highPrice;
    /** 最低。 */
    private BigDecimal lowPrice;
    /** 平均。 */
    private BigDecimal averagePrice;
    /** 换手率，1.25 表示 1.25%。 */
    private BigDecimal turnoverRate;
    /** 交易总量（手），使用来源提供的手数。 */
    private BigDecimal tradeVolume;
    /** 当日成交金额（万），币种跟随市场。 */
    private BigDecimal tradeMarketValue;
    /** 总股本（百万）。 */
    private BigDecimal totalShares;
    /** 总市值（百万）。 */
    private BigDecimal totalMarketValue;
    /** 流通 A 股（百万）。 */
    private BigDecimal circulatingAShares;
    /** A 股市值（百万）。 */
    @JsonProperty("aShareMarketValue")
    private BigDecimal aShareMarketValue;
}
