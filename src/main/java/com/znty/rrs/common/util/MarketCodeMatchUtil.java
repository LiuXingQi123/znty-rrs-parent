package com.znty.rrs.common.util;

import com.znty.rrs.common.enums.MarketCode;
import com.znty.rrs.entity.bo.SecurityInfoBo;

/**
 * 投资市场匹配工具：根据证券主数据推导市场 code，判断是否落在池配置的 market_codes JSON 内。
 */
public final class MarketCodeMatchUtil {

    private MarketCodeMatchUtil() {
    }

    /**
     * 判断证券是否命中池配置的投资市场。
     * <p>池 {@code marketCodesJson} 为空时视为不限制，返回 true。
     * 证券任一可推导市场 code 出现在 JSON 字符串中（形如 {@code "SSE"}）即通过。</p>
     *
     * @param marketCodesJson 池 market_codes JSON 原文，如 {@code ["SSE","CIBM"]}
     * @param sec             证券主数据
     * @return true 表示可入该市场配置
     */
    public static boolean matchesPoolMarkets(String marketCodesJson, SecurityInfoBo sec) {
        if (marketCodesJson == null || marketCodesJson.isEmpty() || "[]".equals(marketCodesJson.trim())) {
            return true;
        }
        if (sec == null) {
            return false;
        }
        // 上交所
        if (hasText(sec.getWindCodeSh()) && containsCode(marketCodesJson, MarketCode.SSE)) {
            return true;
        }
        // 深交所
        if (hasText(sec.getWindCodeSz()) && containsCode(marketCodesJson, MarketCode.SZSE)) {
            return true;
        }
        // 银行间
        if (hasText(sec.getWindCodeNib()) && containsCode(marketCodesJson, MarketCode.CIBM)) {
            return true;
        }
        // 北交所
        if (hasText(sec.getWindCodeBj()) && containsCode(marketCodesJson, MarketCode.BSE)) {
            return true;
        }
        // 其他（nbc）
        if (hasText(sec.getWindCodeNbc()) && containsCode(marketCodesJson, MarketCode.OTHER)) {
            return true;
        }
        // 主体
        if (isCompanySecurity(sec) && containsCode(marketCodesJson, MarketCode.COMPANY)) {
            return true;
        }
        return false;
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

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    /**
     * 是否主体类证券（securityType = company）。
     */
    private static boolean isCompanySecurity(SecurityInfoBo sec) {
        return sec != null && "company".equals(sec.getSecurityType());
    }
}
