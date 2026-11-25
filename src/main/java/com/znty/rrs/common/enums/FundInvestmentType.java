package com.znty.rrs.common.enums;

/**
 * 基金投资类型
 *
 * 对应基金调库提交字段 {@code fund_investment_type}
 */
public enum FundInvestmentType {

    /**
     * 股票型
     */
    STOCK("stock"),

    /**
     * 偏股混合型
     */
    EQUITY_HYBRID("equity_hybrid"),

    /**
     * 货币型
     */
    MONEY_MARKET("money_market"),

    /**
     * 偏债混合型
     */
    BOND_HYBRID("bond_hybrid"),

    /**
     * 其余类型
     */
    OTHER("other");

    /**
     * 基金投资类型编码
     */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的基金投资类型编码
     */
    FundInvestmentType(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的基金投资类型编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 校验提交的基金投资类型编码
     *
     * 执行顺序：
     * 1. 输入为 null 或空白时返回 false
     * 2. 去掉首尾空白后逐项比较枚举编码
     *
     * @param code 待校验的基金投资类型编码
     * @return true=去掉首尾空白后命中枚举，false=输入为空白或未命中
     */
    public static boolean isValid(String code) {
        // 1. 缺少编码时直接返回校验失败
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        // 2. 去掉首尾空白后按枚举编码比较
        for (FundInvestmentType item : values()) {
            if (item.code.equals(code.trim())) {
                return true;
            }
        }
        return false;
    }
}
