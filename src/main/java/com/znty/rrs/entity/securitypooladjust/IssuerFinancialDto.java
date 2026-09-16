package com.znty.rrs.entity.securitypooladjust;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 发行主体财务指标返回对象
 */
@Data
public class IssuerFinancialDto {

    /** 报告日期（yyyyMMdd） */
    private Long reportDate;

    /** 总资产（亿元） */
    private BigDecimal totAssets;

    /** 所有者权益（亿元） */
    private BigDecimal shareholderEquity;

    /** 资产负债率（%） */
    private BigDecimal debtAssetsRatio;

    /** 营业收入（亿元） */
    private BigDecimal totRev;

    /** 净利润（亿元） */
    private BigDecimal netProfit;

    /** 经营性净现金流（亿元） */
    private BigDecimal netCashOper;

    /** 投资性净现金流（亿元） */
    private BigDecimal netCashInv;

    /** 地区生产总值（亿元） */
    private BigDecimal grp;

    /** 一般预算收入（亿元） */
    private BigDecimal generalBudgetRev;

    /** 一般预算支出（亿元） */
    private BigDecimal generalBudgetExp;

    /** 净资产收益率（%） */
    private BigDecimal roe;

    /** EBITDA 利息保障倍数 */
    private BigDecimal ebitIntCov;

    /** EBITDA（亿元） */
    private BigDecimal ebitda;

    /** 总债务/EBITDA */
    private BigDecimal ebitdaToDebt;
}
