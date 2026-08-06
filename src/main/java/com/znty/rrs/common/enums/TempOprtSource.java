package com.znty.rrs.common.enums;

/**
 * 临时代码操作来源（对应 rrs_temp_security_code.oprt_source）
 */
public enum TempOprtSource {

    /** 人工 */
    MANUAL("manual"),
    /** 定时任务 */
    JOB("job"),
    /** 其他 */
    OTHER("other");

    /** 枚举 code 值 */
    private final String code;

    TempOprtSource(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
