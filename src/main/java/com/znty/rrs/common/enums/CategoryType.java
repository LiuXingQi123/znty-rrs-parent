package com.znty.rrs.common.enums;

/**
 * 证券大类
 *
 * 对应 {@code dict_security_type.category_type}，取值与 {@code sql/rrs_dict_demo_data.sql} 中去重后的证券大类一致
 * 投资池 {@code ip_investment_pool.variety_codes} 的各项也使用这里的编码
 */
public enum CategoryType {

    /**
     * 债券
     */
    BOND("bond"),

    /**
     * 股票
     */
    STOCK("stock"),

    /**
     * 基金
     */
    FUND("fund"),

    /**
     * 公司主体
     */
    COMPANY("company"),

    /**
     * 指数
     */
    INDEX("index"),

    /**
     * 权证
     */
    WARRANT("warrant"),

    /**
     * 信托
     */
    TRUST("trust"),

    /**
     * 私募理财
     */
    PRIVATE_WEALTH("private_wealth"),

    /**
     * 未知
     */
    UNKNOWN("unknown");

    /**
     * 证券大类编码
     */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的证券大类编码
     */
    CategoryType(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的证券大类编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return this.code;
    }
}
