package com.znty.sirm.common.enums;

/** 审核状态（对应 ip_adjust_log.audit_status / ip_pool_status.audit_status） */
public enum AuditStatus {
    /** 无效调整 */
    INVALID("-1"),
    /** 已提交待审核 */
    SUBMITTED("00"),
    /** 审核通过待审批 */
    REVIEWED("10"),
    /** 驳回待修改 */
    REJECT_MODIFY("11"),
    /** 审批通过 */
    APPROVED("20"),
    /** 审批驳回 */
    REJECTED("21"),
    /** O32 自动审批 */
    O32("32"),
    /** 发起人已撤回 */
    REVOKED("99");

    /** 枚举 code 值 */
    private final String code;

    AuditStatus(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
