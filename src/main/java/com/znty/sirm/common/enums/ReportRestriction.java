package com.znty.sirm.common.enums;

/** 研报限制（对应 ip_investment_pool.in/out_report_restriction） */
public enum ReportRestriction {
    /** 无限制 */
    NONE("none"),
    /** 任一报告 */
    ANY("any"),
    /** 内部报告 */
    INTERNAL("internal");

    /** 枚举 code 值 */
    private final String code;

    ReportRestriction(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
