package com.znty.rrs.common.enums;

/** 恒生市场编码。 */
public enum HsMarketCode {
    /** 未知。 */
    UNKNOWN("0"),
    /** 上海证券交易所。 */
    SHANGHAI_STOCK_EXCHANGE("1"),
    /** 深圳证券交易所。 */
    SHENZHEN_STOCK_EXCHANGE("2"),
    /** 香港证券交易所。 */
    HONG_KONG_STOCK_EXCHANGE("3"),
    /** 银行间市场。 */
    INTERBANK_MARKET("4"),
    /** 场外市场。 */
    OTC_MARKET("5"),
    /** 北京证券交易所。 */
    BEIJING_STOCK_EXCHANGE("6"),
    /** 主体。 */
    COMPANY("250"),
    /** JWCW 市场。 */
    JWCW_MARKET("400"),
    /** 新加坡交易所。 */
    SINGAPORE_STOCK_EXCHANGE("10200"),
    /** 日本证券交易所。 */
    JAPAN_STOCK_EXCHANGE("10300"),
    /** 韩国证券交易所。 */
    KOREA_STOCK_EXCHANGE("10400"),
    /** 印度国家证券交易所。 */
    INDIA_NATIONAL_STOCK_EXCHANGE("10500"),
    /** 印度尼西亚证券交易所。 */
    INDONESIA_STOCK_EXCHANGE("10600"),
    /** 马来西亚证券交易所。 */
    MALAYSIA_STOCK_EXCHANGE("10700"),
    /** 菲律宾证券交易所。 */
    PHILIPPINES_STOCK_EXCHANGE("10800"),
    /** 泰国证券交易所。 */
    THAILAND_STOCK_EXCHANGE("10900"),
    /** 台湾证券交易所。 */
    TAIWAN_STOCK_EXCHANGE("11000"),
    /** 英国证券交易所。 */
    UNITED_KINGDOM_STOCK_EXCHANGE("20100"),
    /** 德国证券交易所。 */
    GERMANY_STOCK_EXCHANGE("20200"),
    /** 法国证券交易所。 */
    FRANCE_STOCK_EXCHANGE("20300"),
    /** 美国证券交易所。 */
    UNITED_STATES_STOCK_EXCHANGE("30100"),
    /** 澳大利亚证券交易所。 */
    AUSTRALIA_STOCK_EXCHANGE("50100"),
    /** 其他 QDII 市场。 */
    OTHER_QDII_MARKET("99999");

    /** 恒生市场编码。 */
    private final String code;

    HsMarketCode(String code) {
        this.code = code;
    }

    /** 获取恒生市场编码。 */
    public String getCode() {
        return code;
    }
}
