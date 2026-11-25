package com.znty.rrs.common.enums;

/**
 * 临时代码操作来源
 *
 * 对应 {@code rrs_temp_security_code.oprt_source}，区分人工、定时任务或其他来源
 */
public enum TempOprtSource {

    /**
     * 人工
     */
    MANUAL("manual"),

    /**
     * 定时任务
     */
    JOB("job"),

    /**
     * 其他
     */
    OTHER("other");

    /**
     * 临时代码操作来源编码
     */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的临时代码操作来源编码
     */
    TempOprtSource(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的临时代码操作来源编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
