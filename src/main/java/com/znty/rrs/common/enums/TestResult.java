package com.znty.rrs.common.enums;

/**
 * 测试用例最近执行结果
 *
 * 对应 {@code rule_test_case.last_result}，记录尚未执行、执行中、通过或失败
 */
public enum TestResult {

    /** 未执行 */
    PENDING("pending"),

    /** 执行中 */
    RUNNING("running"),

    /** 通过 */
    PASS("pass"),

    /** 失败 */
    FAIL("fail");

    /** 测试用例最近执行结果编码 */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的测试用例最近执行结果编码
     */
    TestResult(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的测试用例最近执行结果编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
