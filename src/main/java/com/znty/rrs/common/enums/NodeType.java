package com.znty.rrs.common.enums;

/**
 * 流程节点类型
 *
 * 对应 {@code wf_flow_node.node_type}，用于区分开始、结束、审批、条件判断、自动执行和通知节点
 */
public enum NodeType {

    /** 开始节点 */
    START("start"),

    /** 结束节点 */
    END("end"),

    /** 审批节点 */
    APPROVAL("approval"),

    /** 条件判断节点 */
    CONDITION("condition"),

    /** 自动执行节点 */
    AUTO("auto"),

    /** 消息通知节点 */
    NOTIFY("notify");

    /** 流程节点类型编码 */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的流程节点类型编码
     */
    NodeType(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的流程节点类型编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
