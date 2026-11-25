package com.znty.rrs.common.enums;

/**
 * 投资池启用状态
 *
 * 对应 {@code ip_investment_pool.status}，区分启用和停用
 */
public enum PoolStatus {

    /** 启用 */
    ENABLED("enabled"),

    /** 停用 */
    DISABLED("disabled");

    /** 投资池启用状态编码 */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的投资池启用状态编码
     */
    PoolStatus(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的投资池启用状态编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
