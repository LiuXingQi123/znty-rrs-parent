package com.znty.rrs.entity.fundpooladjust;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/** 基金列表及只读详情返回对象 */
@Data
public class FundInfoDto {
    /** 基金代码 */
    private String fundCode;
    /** 基金全称 */
    private String fundName;
    /** 基金简称 */
    private String fundShortName;
    /** 基金产品类型编码 */
    private String securityType;
    /** 基金产品类型名称 */
    private String securityTypeName;
    /** 基金状态 */
    private String securityStatus;
    /** 市场编码 */
    private String marketCode;
    /** 币种编码 */
    private String currencyCode;
    /** 最新单位净值 */
    private BigDecimal latestNav;
    /** 成立日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date establishmentDate;
    /** 基金经理 */
    private String fundManagerNames;
    /** 基金管理人 */
    private String fundAdministrator;
    /** 基金托管人 */
    private String fundCustodian;
    /** 累计净值 */
    private BigDecimal accumulatedNav;
    /** 投资风格 */
    private String investmentStyle;
    /** 最新交易价格 */
    private BigDecimal tradePrice;
    /** 发行规模（亿元） */
    private BigDecimal issueScale;
    /** 最新规模（亿元） */
    private BigDecimal latestScale;
    /** 数据日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date dataDate;
    /** 记录更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updtTime;
}
