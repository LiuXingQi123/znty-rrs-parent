package com.znty.rrs.common.enums;

/** 规则启用状态（对应 rule_definition.status） */
public enum RuleStatus {
    /** 已启用 */
    ACTIVE("active"),
    /** 已禁用 */
    DISABLED("disabled");

    /** 枚举 code 值 */
    private final String code;

    RuleStatus(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
