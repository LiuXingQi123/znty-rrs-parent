package com.znty.sirm.common.enums;

/** 报告类型（对应 rrs_report_in.report_type） */
public enum ReportType {
    /** 债券入库报告 */
    BOND_IN_REPORT("bond_in_report"),
    /** 债券出库报告 */
    BOND_OUT_REPORT("bond_out_report"),
    /** 基金入库报告 */
    FUND_IN_REPORT("fund_in_report"),
    /** 基金出库报告 */
    FUND_OUT_REPORT("fund_out_report"),
    /** 股票入库报告 */
    STOCK_IN_REPORT("stock_in_report"),
    /** 股票出库报告 */
    STOCK_OUT_REPORT("stock_out_report"),
    /** 其他报告 */
    OTHER_REPORT("other_report");

    /** 枚举 code 值 */
    private final String code;

    ReportType(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
