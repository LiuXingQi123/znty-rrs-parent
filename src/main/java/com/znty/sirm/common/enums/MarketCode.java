package com.znty.sirm.common.enums;

/** 证券市场编码 */
public enum MarketCode {
    /** 上海证券交易所 */
    SSE("SSE"),
    /** 深圳证券交易所 */
    SZSE("SZSE"),
    /** 银行间市场 */
    CIBM("CIBM"),
    /** 场外市场 */
    OTC("OTC"),
    /** JWCW 市场 */
    JWCW("JWCW"),
    /** 未知市场 */
    UNKNOWN("UNKNOWN");

    /** 枚举 code 值 */
    private final String code;

    MarketCode(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
