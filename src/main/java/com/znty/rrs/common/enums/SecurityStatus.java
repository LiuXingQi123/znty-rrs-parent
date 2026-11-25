package com.znty.rrs.common.enums;

/**
 * 证券上市状态
 *
 * 对应 {@code rrs_securityinfo.security_status}，证券状态为 {@code D} 时，债券业务也用该值判定到期
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

    /** 证券上市状态编码 */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的证券上市状态编码
     */
    SecurityStatus(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的证券上市状态编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
