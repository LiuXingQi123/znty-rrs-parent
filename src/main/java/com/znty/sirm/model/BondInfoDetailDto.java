package com.znty.sirm.model;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 债券详情返回对象（调库页面顶部信息展示）
 */
@Data
public class BondInfoDetailDto {
    /** 债券全称 */
    private String fullName;
    /** 债券简称 */
    private String bondShortName;
    /** 债券代码 */
    private String bondCode;
    /** 发行人名称 */
    private String issuerName;
    /** 银行间代码 */
    private String nibCode;
    /** 交易所代码 */
    private String exchangeCode;
    /** 发行总额(亿元) */
    private BigDecimal issueAmount;
    /** 当期利率(%) */
    private BigDecimal currentRate;
    /** 行权剩余期限 */
    private String remExeTerm;
    /** 起息日期 */
    private String carryDate;
    /** 到期日 */
    private String maturityDate;
    /** 质押比率(%) */
    private BigDecimal pledgeRatio;
    /** 评级机构 */
    private String ratingAgency;
    /** 债券评级 */
    private String bondRating;
    /** 主体评级 */
    private String issuerRating;
    /** 展望评级 */
    private String ratingOutlook;
    /** 担保情况 */
    private String guaranteeStatus;
    /** 主承销商 */
    private String leadUnderwriter;
    /** 主体内评分档 */
    private String innerIssuerRating;
    /** 债券类型 */
    private String bondType;
    /** 回售行权期限 */
    private String putExeTerm;
    /** 赎回行权剩余期限 */
    private String callRemTerm;
    /** 担保人主体内评分 */
    private String innerGuarantorRating;
    /** 含权债剩余期限 */
    private String optRemTerm;
    /** 债券期限 */
    private String termStr;
    /** 募集资金用途 */
    private String fundUsage;
    /** 提示原因 */
    private String promptReason;
    /** 债券分析 */
    private String analysis;
}
