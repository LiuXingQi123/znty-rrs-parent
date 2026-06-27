package com.znty.sirm.common.enums;

/** 测试用例最近执行结果（对应 rule_test_case.last_result） */
public enum TestResult {
    /** 未执行 */
    PENDING("pending"),
    /** 执行中 */
    RUNNING("running"),
    /** 通过 */
    PASS("pass"),
    /** 失败 */
    FAIL("fail");

    /** 枚举 code 值 */
    private final String code;

    TestResult(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
