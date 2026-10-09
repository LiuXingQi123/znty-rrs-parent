package com.znty.rrs.entity.stockpooladjust;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/** 股票基础信息及实时最新两次评级 */
@Data
public class StockInfoDto {
    /** 主键 ID */
    private Long id;
    /** 股票代码 */
    private String stockCode;
    /** 股票名称 */
    private String stockName;
    /** 股票简称 */
    private String stockShortName;
    /** 行业编码 */
    private String industryCode;
    /** 所属行业 */
    private String industryName;
    /** 股票类型编码 */
    private String securityType;
    /** 股票类型名称 */
    private String securityTypeName;
    /** 股票状态：L 上市、D 退市、N 未上市 */
    private String securityStatus;
    /** 市场编码：SSE、SZSE、HKEX */
    private String marketCode;
    /** 币种编码 */
    private String currencyCode;
    /** 上市日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date listDate;
    /** 退市日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date delistDate;
    /** 昨收价格 */
    private BigDecimal previousClosePrice;
    /** 最高价格 */
    private BigDecimal highPrice;
    /** 最低价格 */
    private BigDecimal lowPrice;
    /** 平均价格 */
    private BigDecimal averagePrice;
    /** 换手率 */
    private BigDecimal turnoverRate;
    /** 交易总量（手） */
    private BigDecimal tradeVolume;
    /** 交易市值（万） */
    private BigDecimal tradeMarketValue;
    /** 总股本（百万） */
    private BigDecimal totalShares;
    /** 总市值（百万） */
    private BigDecimal totalMarketValue;
    /** 流通 A 股（百万） */
    private BigDecimal circulatingAShares;
    /** A 股市值（百万） */
    @JsonProperty("aShareMarketValue")
    private BigDecimal aShareMarketValue;
    /** 数据日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date dataDate;
    /** 数据来源 */
    private String sourceSystem;
    /** 备注 */
    private String memo;
    /** 删除标记 */
    private Integer isDeleted;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date crteTime;
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updtTime;
    /** 最新评级 */
    private String latestRating;
    /** 上次评级 */
    private String previousRating;
    /** 最新评级时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date latestRatingDate;
    /** 上次评级时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date previousRatingDate;
}
