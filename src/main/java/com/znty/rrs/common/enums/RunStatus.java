package com.znty.rrs.common.enums;

/** 规则执行状态（对应 rule_test_run.run_status） */
public enum RunStatus {
    /** 执行中 */
    RUNNING("running"),
    /** 通过 */
    PASS("pass"),
    /** 失败 */
    FAIL("fail");

    /** 枚举 code 值 */
    private final String code;

    RunStatus(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
