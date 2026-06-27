package com.znty.sirm.common.enums;

/** 调整方向（对应 ip_adjust_log.adjust_mode / ip_pool_status.adjust_mode） */
public enum AdjustMode {
    /** 调入 */
    IN("调入"),
    /** 调出 */
    OUT("调出");

    /** 枚举 code 值 */
    private final String code;

    AdjustMode(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
