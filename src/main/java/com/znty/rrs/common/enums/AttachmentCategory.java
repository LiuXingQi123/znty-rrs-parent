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
    REPORT_OUT("report_out");

    /** 枚举 code 值 */
    private final String code;

    AttachmentCategory(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
