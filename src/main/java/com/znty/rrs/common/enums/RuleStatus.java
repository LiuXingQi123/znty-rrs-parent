package com.znty.rrs.common.enums;

/**
 * 规则启用状态
 *
 * 对应 {@code rule_definition.status}，区分已启用和已禁用
 */
public enum RuleStatus {

    /** 已启用 */
    ACTIVE("active"),

    /** 已禁用 */
    DISABLED("disabled");

    /** 规则启用状态编码 */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的规则启用状态编码
     */
    RuleStatus(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的规则启用状态编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
