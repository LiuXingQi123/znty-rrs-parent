package com.znty.rrs.common.enums;

/** 流程步骤处理动作（对应 ip_adjust_step.process_action） */
public enum ProcessAction {
    /** 提交 */
    SUBMIT("submit"),
    /** 重新提交 */
    RESUBMIT("resubmit"),
    /** 通过 */
    APPROVE("approve"),
    /** 驳回 */
    REJECT("reject"),
    /** 自动处理 */
    AUTO_PROCESS("auto_process"),
    /** 被跳过 */
    SKIPPED("skipped");

    /** 枚举 code 值 */
    private final String code;

    ProcessAction(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
