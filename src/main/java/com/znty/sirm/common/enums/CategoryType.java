package com.znty.sirm.common.enums;

/** 证券大类（对应 dict_security_type.category_type） */
public enum CategoryType {
    /** 债券 */
    BOND("bond"),
    /** 基金 */
    FUND("fund"),
    /** 股票 */
    STOCK("stock"),
    /** 公司主体 */
    COMPANY("company");

    /** 枚举 code 值 */
    private final String code;

    CategoryType(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
