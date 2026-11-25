package com.znty.rrs.common.enums;

/**
 * 债券存续状态筛选选项
 *
 * 证券状态为 {@code D} 时归为到期，其余状态归为存续
 * 证券主档的上市状态由 {@link SecurityStatus} 表示，取值为 {@code L}、{@code N}、{@code D} 和 {@code U}
 */
public enum BondStatus {

    /** 存续 */
    ACTIVE("active"),

    /** 到期 */
    MATURED("matured");

    /** 债券存续状态筛选编码 */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的债券存续状态筛选编码
     */
    BondStatus(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的债券存续状态筛选编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
