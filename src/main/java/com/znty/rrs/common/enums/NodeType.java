package com.znty.rrs.common.enums;

/** 流程节点类型（对应 wf_flow_node.node_type） */
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

    /** 枚举 code 值 */
    private final String code;

    NodeType(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
