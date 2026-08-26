package com.znty.rrs.common.enums;

/**
 * 存续/到期状态（前端按到期日计算，无独立 DB 字段）。
 * <p>与 {@link SecurityStatus}（{@code rrs_securityinfo.security_status} 上市状态 L/N/D/U）不是同一概念。</p>
 */
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
