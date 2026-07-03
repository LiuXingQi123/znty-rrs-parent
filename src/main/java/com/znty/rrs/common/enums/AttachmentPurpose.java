package com.znty.rrs.common.enums;

/** 附件逻辑用途（由 sys_attachment.attachment_category 派生，非独立 DB 列） */
public enum AttachmentPurpose {
    /** 信评报告 */
    CREDIT_REPORT("credit_report"),
    /** 其他材料 */
    MATERIAL("material");

    /** 枚举 code 值 */
    private final String code;

    AttachmentPurpose(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
