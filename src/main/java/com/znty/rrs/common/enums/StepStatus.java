package com.znty.rrs.common.enums;

/** 流程步骤状态（对应 ip_adjust_step.step_status） */
public enum StepStatus {
    /** 待处理 */
    PENDING("pending"),
    /** 通过 */
    APPROVE("approve"),
    /** 驳回 */
    REJECT("reject"),
    /** 提交 */
    SUBMIT("submit"),
    /** 自动处理 */
    AUTO_PROCESS("auto_process"),
    /** 已撤回 */
    CANCELED("canceled");

    /** 枚举 code 值 */
    private final String code;

    StepStatus(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
