package com.znty.rrs.common.enums;

/**
 * 附件逻辑用途
 *
 * 从 {@code sys_attachment.attachment_category} 派生，用于区分信评报告和其他材料，数据库保存的是附件分类
 */
public enum AttachmentPurpose {

    /**
     * 信评报告
     */
    CREDIT_REPORT("credit_report"),

    /**
     * 其他材料
     */
    MATERIAL("material");

    /**
     * 附件用途编码
     */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的附件用途编码
     */
    AttachmentPurpose(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的附件用途编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
