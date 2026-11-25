package com.znty.rrs.common.enums;

/**
 * 自动调库规则类型
 *
 * 对应 {@code ip_pool_auto_rule.rule_type}，区分自动调入和自动调出
 */
public enum RuleType {

    /**
     * 自动调入
     */
    AUTO_IN("auto_in"),

    /**
     * 自动调出
     */
    AUTO_OUT("auto_out");

    /**
     * 自动调库规则类型编码
     */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的自动调库规则类型编码
     */
    RuleType(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的自动调库规则类型编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
