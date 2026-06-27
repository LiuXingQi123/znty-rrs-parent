package com.znty.sirm.common.enums;

/** 流程业务分类（对应 wf_flow_definition.category） */
public enum FlowCategory {
    /** 债券 */
    BOND("bond"),
    /** 基金 */
    FUND("fund"),
    /** 股票 */
    STOCK("stock"),
    /** 主体 */
    COMPANY("company"),
    /** 其他 */
    OTHER("other");

    /** 枚举 code 值 */
    private final String code;

    FlowCategory(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
