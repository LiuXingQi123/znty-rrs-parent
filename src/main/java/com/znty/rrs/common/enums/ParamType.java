package com.znty.rrs.common.enums;

/**
 * 规则参数类型
 *
 * 对应 {@code rule_param.param_type}，区分字符串、数值、单选和多选
 */
public enum ParamType {

    /** 字符串 */
    STRING("string"),

    /** 数值 */
    NUMBER("number"),

    /** 单选 */
    SELECT("select"),

    /** 多选 */
    MULTISELECT("multiselect");

    /** 规则参数类型编码 */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的规则参数类型编码
     */
    ParamType(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的规则参数类型编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
