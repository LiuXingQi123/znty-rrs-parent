package com.znty.rrs.common.enums;

/**
 * 规则执行状态
 *
 * 对应 {@code rule_test_run.run_status}，记录一次规则执行当前是执行中、通过还是失败
 */
public enum RunStatus {

    /** 执行中 */
    RUNNING("running"),

    /** 通过 */
    PASS("pass"),

    /** 失败 */
    FAIL("fail");

    /** 规则执行状态编码 */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的规则执行状态编码
     */
    RunStatus(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的规则执行状态编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
