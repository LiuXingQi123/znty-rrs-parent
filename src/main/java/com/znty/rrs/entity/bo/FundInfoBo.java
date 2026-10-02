package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/** 基金基础信息业务对象，对应 rrs_fundinfo */
@Data
public class FundInfoBo {
    /** 主键 ID */
    private Long id;
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
    /** 成立日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date establishmentDate;
    /** 上市日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date listDate;
    /** 退市日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date delistDate;
    /** 基金管理公司代码 */
    private String fundCompanyCode;
    /** 基金管理公司名称 */
    private String fundCompanyName;
    /** 是否为港股标的 */
    private Integer isH;
    /** 是否属于港股通标的 */
    private Integer isHksc;
    /** 最新单位净值 */
    private BigDecimal latestNav;
    /** 基金经理姓名 */
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
