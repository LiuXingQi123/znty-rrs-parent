package com.znty.rrs.common.enums;

/** 业务领域编码，股票预留，当前接入债券和基金 */
public enum BusinessDomain {
    /** bond 权限或业务编码 */
    BOND("bond"),
    /** fund 权限或业务编码 */
    FUND("fund"),
    /** stock 权限或业务编码 */
    STOCK("stock");

    /** 编码 */
    private final String code;

    BusinessDomain(String code) {
        this.code = code;
    }

    /** 获取编码 */
    public String getCode() {
        return code;
    }
}
