package com.znty.rrs.common.enums;

/** 附件分类（对应 sys_attachment.attachment_category） */
public enum AttachmentCategory {
    /** 手工上传信评报告 */
    CREDIT_REPORT_HAND("credit_report_hand"),
    /** 内部报告库信评报告 */
    CREDIT_REPORT_IN("credit_report_in"),
    /** 外部报告库信评报告 */
    CREDIT_REPORT_OUT("credit_report_out"),
    /** 手工上传其他材料 */
    MATERIAL_HAND("material_hand"),
    /** 内部报告库其他材料 */
    MATERIAL_IN("material_in"),
    /** 外部报告库其他材料 */
    MATERIAL_OUT("material_out"),
    /** 内部报告库附件 */
    REPORT_IN("report_in"),
    /** 外部报告库附件 */
    REPORT_OUT("report_out"),
    /** 基金手工上传报告 */
    FUND_REPORT_HAND("fund_report_hand"),
    /** 基金内部报告库报告 */
    FUND_REPORT_IN("fund_report_in"),
    /** 基金外部报告库报告 */
    FUND_REPORT_OUT("fund_report_out"),
    /** 基金手工上传其他材料 */
    FUND_MATERIAL_HAND("fund_material_hand"),
    /** 基金内部报告库其他材料 */
    FUND_MATERIAL_IN("fund_material_in"),
    /** 基金外部报告库其他材料 */
    FUND_MATERIAL_OUT("fund_material_out"),
    /** 股票手工上传报告 */
    STOCK_REPORT_HAND("stock_report_hand"),
    /** 股票内部报告库报告 */
    STOCK_REPORT_IN("stock_report_in"),
    /** 股票外部报告库报告 */
    STOCK_REPORT_OUT("stock_report_out"),
    /** 股票手工上传材料 */
    STOCK_MATERIAL_HAND("stock_material_hand"),
    /** 股票内部报告库材料 */
    STOCK_MATERIAL_IN("stock_material_in"),
    /** 股票外部报告库材料 */
    STOCK_MATERIAL_OUT("stock_material_out");

    /** 枚举 code 值 */
    private final String code;

    /**
     * 保存附件分类编码。
     *
     * @param code 附件分类编码
     */
    AttachmentCategory(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
