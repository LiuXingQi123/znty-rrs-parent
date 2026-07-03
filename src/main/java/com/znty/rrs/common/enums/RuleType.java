package com.znty.rrs.common.enums;

/** 自动调入调出规则类型（对应 ip_pool_auto_rule.rule_type） */
public enum RuleType {
    /** 自动调入 */
    AUTO_IN("auto_in"),
    /** 自动调出 */
    AUTO_OUT("auto_out");

    /** 枚举 code 值 */
    private final String code;

    RuleType(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
