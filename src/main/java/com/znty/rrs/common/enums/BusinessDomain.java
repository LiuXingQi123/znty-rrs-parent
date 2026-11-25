package com.znty.rrs.common.enums;

/**
 * 业务领域
 *
 * 区分债券、基金和股票，供业务归类及权限判断使用
 */
public enum BusinessDomain {

    /**
     * bond 权限或业务编码
     */
    BOND("bond"),

    /**
     * fund 权限或业务编码
     */
    FUND("fund"),

    /**
     * stock 权限或业务编码
     */
    STOCK("stock");

    /**
     * 业务领域编码
     */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的业务领域编码
     */
    BusinessDomain(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的业务领域编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
