package com.znty.rrs.common.util;

import com.znty.rrs.common.enums.MarketCode;

/**
 * 投资池市场匹配工具
 *
 * 使用调用方提供的当前市场编码，市场查询和主档回退由调用方处理
 * 按配置中的带引号条目匹配，避免把编码的一部分误当成完整条目
 */
public final class MarketCodeMatchUtil {

    /** 工具类禁止实例化 */
    private MarketCodeMatchUtil() {
    }

    /**
     * 判断证券市场是否符合投资池的市场限制
     *
     * 执行顺序：
     * 1. 配置值为 null、空字符串或去掉首尾空白后为空数组时，放行
     * 2. 池配置了市场时，检查其中是否包含带引号的证券市场编码
     *
     * @param marketCodesJson 池 market_codes JSON 原文，如 {@code ["SSE","NIB"]}
     * @param marketCode 证券当前所属市场编码
     * @return true=池未限制市场或配置包含该市场，false=配置了市场且证券市场缺失或未命中
     */
    public static boolean matchesPoolMarkets(String marketCodesJson, String marketCode) {
        if (marketCodesJson == null || marketCodesJson.isEmpty() || "[]".equals(marketCodesJson.trim())) {
            return true;
        }
        return marketCode != null && marketCodesJson.contains("\"" + marketCode + "\"");
    }

    /**
     * 检查投资池是否明确配置了指定市场
     *
     * @param marketCodesJson 池 market_codes JSON 原文
     * @param marketCode 指定市场
     * @return true=配置包含该市场编码，false=配置或市场缺失，或编码未命中
     */
    public static boolean containsCode(String marketCodesJson, MarketCode marketCode) {
        if (marketCodesJson == null || marketCode == null) {
            return false;
        }
        return marketCodesJson.contains("\"" + marketCode.getCode() + "\"");
    }

}
