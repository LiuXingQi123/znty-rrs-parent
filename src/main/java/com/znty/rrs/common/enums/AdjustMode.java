package com.znty.rrs.common.enums;

/**
 * 调整方向
 *
 * 调库日志和池状态使用本编码区分调入、调出，对应 {@code ip_adjust_log.adjust_mode} 和 {@code ip_pool_status.adjust_mode}
 */
public enum AdjustMode {

    /**
     * 调入
     */
    IN("调入"),

    /**
     * 调出
     */
    OUT("调出");

    /**
     * 调库方向编码
     */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的调库方向编码
     */
    AdjustMode(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的调库方向编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
