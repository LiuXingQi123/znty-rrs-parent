package com.znty.sirm.common.enums;

/** 处理人类型（对应 ip_pool_permission.handler_type / wf_node_approval_handler.handler_type） */
public enum HandlerType {
    /** 角色 */
    ROLE("role"),
    /** 人员 */
    USER("user");

    /** 枚举 code 值 */
    private final String code;

    HandlerType(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
