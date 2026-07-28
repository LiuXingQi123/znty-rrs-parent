package com.znty.rrs.common.enums;

/**
 * 证券大类（对应 dict_security_type.category_type）。
 * 取值与 sql/rrs_dict_demo_data.sql 演示数据去重后的大类一致；
 * 投资池 ip_investment_pool.variety_codes 元素亦取本枚举 code。
 */
public enum CategoryType {
    /** 债券 */
    BOND("bond"),
    /** 股票 */
    STOCK("stock"),
    /** 基金 */
    FUND("fund"),
    /** 公司主体 */
    COMPANY("company"),
    /** 指数 */
    INDEX("index"),
    /** 权证 */
    WARRANT("warrant"),
    /** 信托 */
    TRUST("trust"),
    /** 私募理财 */
    PRIVATE_WEALTH("private_wealth"),
    /** 未知 */
    UNKNOWN("unknown");

    /** 枚举 code 值 */
    private final String code;

    CategoryType(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return this.code;
    }
}
