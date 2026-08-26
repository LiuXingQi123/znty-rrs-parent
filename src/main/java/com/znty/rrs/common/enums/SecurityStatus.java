package com.znty.rrs.common.enums;

/**
 * 证券上市状态（对应 {@code rrs_securityinfo.security_status}）。
 * <p>与按到期日计算的 {@link BondStatus}（存续/到期）不是同一概念。</p>
 */
public enum SecurityStatus {
    /** 上市中 */
    LISTED("L"),
    /** 待上市 */
    PENDING("N"),
    /** 退市 */
    DELISTED("D"),
    /** 未知（来源未给状态；勿留 NULL，否则 != 'D' 三值逻辑会滤掉该行） */
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
