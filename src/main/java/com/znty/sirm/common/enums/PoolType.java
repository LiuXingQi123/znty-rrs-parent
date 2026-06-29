package com.znty.sirm.common.enums;

/** 投资池类型（对应 ip_investment_pool.pool_type） */
public enum PoolType {
    /** 信用债 */
    CREDIT_BOND("credit_bond"),
    /** 境外债 */
    OFFSHORE_BOND("offshore_bond"),
    /** 转债 */
    CONVERTIBLE_BOND("convertible_bond"),
    /** 专户产品 */
    SPECIAL_ACCOUNT("special_account"),
    /** CRMW库 */
    CRMW("crmw"),
    /** 禁投池 */
    FORBIDDEN("forbidden"),
    /** 观察池 */
    OBSERVE("observe"),
    /** 研究池 */
    RESEARCH("research"),
    /** 基金池 */
    FUND("fund"),
    /** 限制池 */
    RESTRICTED("restricted"),
    /** 行业池 */
    INDUSTRY("industry"),
    /** 白名单 */
    WHITELIST("whitelist"),
    /** 黑名单 */
    BLACKLIST("blacklist"),
    /** 私募 */
    PRIVATE_PLACEMENT("private_placement"),
    /** 其他 */
    OTHER("other");

    /** 枚举 code 值 */
    private final String code;

    PoolType(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
