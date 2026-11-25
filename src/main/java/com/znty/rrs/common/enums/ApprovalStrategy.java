package com.znty.rrs.common.enums;

/**
 * 审批策略
 *
 * 审批节点通过 {@code wf_node_approval_config.approval_strategy} 指定由谁处理、何时完成节点以及是否自动审批
 */
public enum ApprovalStrategy {

    /**
     * 抢占审批
     */
    PREEMPT("preempt"),

    /**
     * 全部处理（会签）
     */
    ALL("all"),

    /**
     * 流程发起人处理
     */
    INITIATOR("initiator"),

    /**
     * 按 O32 策略处理审批
     *
     * 非临时代码由系统代审，临时代码转人工抢占
     */
    O32("o32"),

    /**
     * 系统自动审批通过，临时代码同样适用
     */
    AUTO("auto");

    /**
     * 审批策略编码
     */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的审批策略编码
     */
    ApprovalStrategy(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的审批策略编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
