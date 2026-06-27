package com.znty.sirm.common.enums;

/** 流程定义/版本状态（对应 wf_flow_definition.status / wf_flow_version.status） */
public enum FlowStatus {
    /** 草稿 */
    DRAFT("draft"),
    /** 已发布 */
    ACTIVE("active"),
    /** 已停用 */
    DISABLED("disabled");

    /** 枚举 code 值 */
    private final String code;

    FlowStatus(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
