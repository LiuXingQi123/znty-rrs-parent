package com.znty.sirm.common.enums;

/** 附件用途（对应 sys_attachment.purpose） */
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
