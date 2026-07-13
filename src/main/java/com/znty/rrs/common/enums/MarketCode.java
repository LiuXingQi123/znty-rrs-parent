package com.znty.rrs.common.enums;

/**
 * 证券市场 / 投资市场编码（唯一权威集合）。
 * <p>池字段 {@code market_codes}、临时代码市场、批量筛选与调库校验均只使用下列 code。
 * 名称由前端字典维护，后端枚举不放中文 label。</p>
 */
public enum MarketCode {
    /** 上海证券交易所 */
    SSE("SSE"),
    /** 深圳证券交易所 */
    SZSE("SZSE"),
    /** 银行间市场 */
    CIBM("CIBM"),
    /** 北京证券交易所 */
    BSE("BSE"),
    /** 主体 */
    COMPANY("COMPANY"),
    /** 场外市场 */
    OTC("OTC"),
    /** 其他QDII市场 */
    QDII("QDII"),
    /** 其他 */
    OTHER("OTHER");

    /** 枚举 code 值 */
    private final String code;

    MarketCode(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }

    /**
     * 判断是否为合法市场编码。
     *
     * @param code 市场编码
     * @return true 表示属于本枚举
     */
    public static boolean isValid(String code) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        for (MarketCode item : values()) {
            if (item.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
