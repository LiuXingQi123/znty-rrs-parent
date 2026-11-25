package com.znty.rrs.common.enums;

/**
 * 临时证券和临时基金的登记状态
 *
 * 表示临时、已更新、已取消或已删除，已删除状态仅用于临时基金
 */
public enum TempStatus {

    /**
     * 临时
     */
    TEMPORARY("temporary"),

    /**
     * 已更新
     */
    UPDATED("updated"),

    /**
     * 已取消
     */
    CANCELLED("cancelled"),

    /**
     * 已删除，仅用于临时基金
     */
    DELETED("deleted");

    /**
     * 临时代码登记状态编码
     */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的临时代码登记状态编码
     */
    TempStatus(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的临时代码登记状态编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
