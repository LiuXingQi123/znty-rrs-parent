package com.znty.rrs.common.enums;

/** 基金投资类型（对应基金调库提交字段 fund_investment_type） */
public enum FundInvestmentType {
    /** 股票型 */
    STOCK("stock"),
    /** 偏股混合型 */
    EQUITY_HYBRID("equity_hybrid"),
    /** 货币型 */
    MONEY_MARKET("money_market"),
    /** 偏债混合型 */
    BOND_HYBRID("bond_hybrid"),
    /** 其余类型 */
    OTHER("other");

    /** 枚举 code 值 */
    private final String code;

    /**
     * 绑定基金投资类型编码。
     *
     * @param code 基金投资类型编码
     */
    FundInvestmentType(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }

    /**
     * 判断是否为合法基金投资类型。
     *
     * @param code 待校验的基金投资类型编码
     * @return 是否为合法基金投资类型
     */
    public static boolean isValid(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        for (FundInvestmentType item : values()) {
            if (item.code.equals(code.trim())) {
                return true;
            }
        }
        return false;
    }
}
