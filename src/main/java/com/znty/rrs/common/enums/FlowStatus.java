package com.znty.rrs.common.enums;

/**
 * 流程定义和版本状态
 *
 * 对应 {@code wf_flow_definition.status} 和 {@code wf_flow_version.status}
 */
public enum FlowStatus {

    /**
     * 草稿
     */
    DRAFT("draft"),

    /**
     * 已发布
     */
    ACTIVE("active"),

    /**
     * 已停用
     */
    DISABLED("disabled");

    /**
     * 流程状态编码
     */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的流程状态编码
     */
    FlowStatus(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的流程状态编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
