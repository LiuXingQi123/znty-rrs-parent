package com.znty.rrs.common.enums;

/**
 * 处理人类型
 *
 * 用于区分按角色或人员配置处理权限，对应 {@code ip_pool_permission.handler_type} 和 {@code wf_node_approval_handler.handler_type}
 */
public enum HandlerType {

    /**
     * 角色
     */
    ROLE("role"),

    /**
     * 人员
     */
    USER("user");

    /**
     * 处理人类型编码
     */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的处理人类型编码
     */
    HandlerType(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的处理人类型编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
