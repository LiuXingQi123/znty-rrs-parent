package com.znty.rrs.common.enums;

/** 投资池启用状态（对应 ip_investment_pool.status） */
public enum PoolStatus {
    /** 启用 */
    ENABLED("enabled"),
    /** 停用 */
    DISABLED("disabled");

    /** 枚举 code 值 */
    private final String code;

    PoolStatus(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
