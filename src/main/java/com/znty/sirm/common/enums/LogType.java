package com.znty.sirm.common.enums;

/** 规则执行日志类型（对应 rule_test_run_log.log_type） */
public enum LogType {
    /** 信息 */
    INFO("info"),
    /** 成功 */
    SUCCESS("success"),
    /** 错误 */
    ERROR("error");

    /** 枚举 code 值 */
    private final String code;

    LogType(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
