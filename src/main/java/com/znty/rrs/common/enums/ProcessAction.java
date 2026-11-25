package com.znty.rrs.common.enums;

/**
 * 流程步骤处理动作
 *
 * 对应 {@code ip_adjust_step.process_action}，记录提交、重新提交、通过、驳回、自动处理或跳过等动作
 */
public enum ProcessAction {

    /**
     * 提交
     */
    SUBMIT("submit"),

    /**
     * 重新提交
     */
    RESUBMIT("resubmit"),

    /**
     * 通过
     */
    APPROVE("approve"),

    /**
     * 驳回
     */
    REJECT("reject"),

    /**
     * 自动处理
     */
    AUTO_PROCESS("auto_process"),

    /**
     * 被跳过
     */
    SKIPPED("skipped");

    /**
     * 流程步骤处理动作编码
     */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的流程步骤处理动作编码
     */
    ProcessAction(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的流程步骤处理动作编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
