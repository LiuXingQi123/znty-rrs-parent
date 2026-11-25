package com.znty.rrs.common.enums;

/**
 * 规则执行日志类型
 *
 * 对应 {@code rule_test_run_log.log_type}，区分普通信息、成功和错误
 */
public enum LogType {

    /**
     * 信息
     */
    INFO("info"),

    /**
     * 成功
     */
    SUCCESS("success"),

    /**
     * 错误
     */
    ERROR("error");

    /**
     * 规则执行日志类型编码
     */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的规则执行日志类型编码
     */
    LogType(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的规则执行日志类型编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
