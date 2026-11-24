package com.znty.rrs.common.enums;

/**
 * 证券上市状态（对应 {@code rrs_securityinfo.security_status}）。
 * 债券的 D 同时表示到期
 */
public enum SecurityStatus {
    /** 上市中 */
    LISTED("L"),
    /** 待上市 */
    PENDING("N"),
    /** 退市 */
    DELISTED("D"),
    /** 未知（债券到期判断时归为未到期） */
    UNKNOWN("U");

    /** 枚举 code 值 */
    private final String code;

    SecurityStatus(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
