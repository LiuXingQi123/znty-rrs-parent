package com.znty.rrs.common.enums;

/** 临时证券与临时基金的登记状态 */
public enum TempStatus {
    /** 临时 */
    TEMPORARY("temporary"),
    /** 已更新 */
    UPDATED("updated"),
    /** 已取消 */
    CANCELLED("cancelled"),
    /** 已删除，仅用于临时基金 */
    DELETED("deleted");

    /** 枚举 code 值 */
    private final String code;

    TempStatus(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
