package com.znty.rrs.common.enums;

/**
 * 流程步骤状态
 *
 * 对应 {@code ip_adjust_step.step_status}，表示该步骤当前等待处理或已产生的处理结果
 */
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

    /** 流程步骤状态编码 */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的流程步骤状态编码
     */
    StepStatus(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的流程步骤状态编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
