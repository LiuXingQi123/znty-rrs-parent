package com.znty.rrs.common.enums;

/**
 * 股票评级
 *
 * 用于股票评级和投资池评级准入校验，编码保持稳定供配置和查询使用
 */
public enum StockRating {

    /**
     * 买入
     */
    BUY("buy"),

    /**
     * 增持
     */
    OVERWEIGHT("overweight"),

    /**
     * 中性
     */
    NEUTRAL("neutral"),

    /**
     * 减持
     */
    UNDERWEIGHT("underweight"),

    /**
     * 卖出
     */
    SELL("sell");

    /**
     * 股票评级编码
     */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的股票评级编码
     */
    StockRating(String code) { this.code = code; }

    /**
     * 获取当前枚举项对应的股票评级编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() { return code; }

    /**
     * 判断配置是否使用合法的股票评级编码
     *
     * @param code 待校验的评级编码，按原值精确匹配
     * @return true=原值与某个评级编码完全一致，false=输入为空或未命中
     */
    public static boolean isValid(String code) {
        for (StockRating rating : values()) {
            if (rating.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
