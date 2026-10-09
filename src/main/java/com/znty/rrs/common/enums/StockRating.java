package com.znty.rrs.common.enums;

/** 股票评级及投资池评级准入使用的稳定编码 */
public enum StockRating {
    /** 买入 */
    BUY("buy"),
    /** 增持 */
    OVERWEIGHT("overweight"),
    /** 中性 */
    NEUTRAL("neutral"),
    /** 减持 */
    UNDERWEIGHT("underweight"),
    /** 卖出 */
    SELL("sell");
    /** 评级编码 */
    private final String code;
    StockRating(String code) { this.code = code; }
    /** 获取评级编码 */
    public String getCode() { return code; }
    /** 判断配置是否使用合法股票评级编码 */
    public static boolean isValid(String code) {
        for (StockRating rating : values()) {
            if (rating.code.equals(code)) { return true; }
        }
        return false;
    }
}
