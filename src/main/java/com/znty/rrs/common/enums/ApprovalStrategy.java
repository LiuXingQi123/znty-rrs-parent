package com.znty.rrs.common.enums;

/** 审批策略（对应 wf_node_approval_config.approval_strategy） */
public enum ApprovalStrategy {
    /** 抢占审批 */
    PREEMPT("preempt"),
    /** 全部处理（会签） */
    ALL("all"),
    /** 流程发起人处理 */
    INITIATOR("initiator");

    /** 枚举 code 值 */
    private final String code;

    ApprovalStrategy(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
