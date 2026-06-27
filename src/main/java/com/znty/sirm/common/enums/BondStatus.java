package com.znty.sirm.common.enums;

/** 证券状态（前端按到期日计算，无独立 DB 字段） */
public enum BondStatus {
    /** 存续 */
    ACTIVE("active"),
    /** 到期 */
    MATURED("matured");

    /** 枚举 code 值 */
    private final String code;

    BondStatus(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
