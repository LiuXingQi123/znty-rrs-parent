package com.znty.rrs.common.util;

import com.znty.rrs.common.enums.MarketCode;

/**
 * 投资市场匹配工具：使用权威查询得到的当前市场 code，判断是否落在池配置的 market_codes JSON 内。
 */
public final class MarketCodeMatchUtil {

    private MarketCodeMatchUtil() {
    }

    /**
     * 判断证券是否命中池配置的投资市场。
     * <p>池 {@code marketCodesJson} 为空时视为不限制，返回 true。
     * 证券当前市场 code 出现在 JSON 字符串中（形如 {@code "SSE"}）即通过。</p>
     *
     * @param marketCodesJson 池 market_codes JSON 原文，如 {@code ["SSE","CIBM"]}
     * @param marketCode      当前证券所属市场
     * @return true 表示可入该市场配置
     */
    public static boolean matchesPoolMarkets(String marketCodesJson, String marketCode) {
        if (marketCodesJson == null || marketCodesJson.isEmpty() || "[]".equals(marketCodesJson.trim())) {
            return true;
        }
        return marketCode != null && marketCodesJson.contains("\"" + marketCode + "\"");
    }

    /**
     * 池 market_codes JSON 是否包含指定市场 code。
     */
    public static boolean containsCode(String marketCodesJson, MarketCode marketCode) {
        if (marketCodesJson == null || marketCode == null) {
            return false;
        }
        return marketCodesJson.contains("\"" + marketCode.getCode() + "\"");
    }

}
