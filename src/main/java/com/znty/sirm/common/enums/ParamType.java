package com.znty.sirm.common.enums;

/** 规则参数类型（对应 rule_param.param_type） */
public enum ParamType {
    /** 字符串 */
    STRING("string"),
    /** 数值 */
    NUMBER("number"),
    /** 单选 */
    SELECT("select"),
    /** 多选 */
    MULTISELECT("multiselect");

    /** 枚举 code 值 */
    private final String code;

    ParamType(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
