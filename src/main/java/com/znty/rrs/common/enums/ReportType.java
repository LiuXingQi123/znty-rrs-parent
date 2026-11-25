package com.znty.rrs.common.enums;

/**
 * 报告类型
 *
 * 对应 {@code rrs_report_in.report_type}，区分债券、基金和股票的入库或出库报告及其他报告
 */
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

    /** 报告类型编码 */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的报告类型编码
     */
    ReportType(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的报告类型编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
