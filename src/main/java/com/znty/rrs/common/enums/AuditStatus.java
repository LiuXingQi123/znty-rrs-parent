package com.znty.rrs.common.enums;

/**
 * 调库审核状态
 *
 * 对应 {@code ip_adjust_log.audit_status} 和 {@code ip_pool_status.audit_status}，只有审批通过 {@code 20} 才写入池状态
 */
public enum AuditStatus {

    /**
     * 无效调整
     */
    INVALID("-1"),

    /**
     * 流程中（别名：待审批 / 审批中）
     */
    SUBMITTED("00"),

    /**
     * 驳回待修改
     */
    REJECT_MODIFY("11"),

    /**
     * 审批通过
     */
    APPROVED("20"),

    /**
     * 审批驳回
     */
    REJECTED("21"),

    /**
     * O32 自动审批的预留状态
     *
     * 当前自动审批节点通过后写入 {@link #APPROVED}（20），运行时不直接写入 32
     */
    O32("32"),

    /**
     * 发起人已撤回
     */
    REVOKED("99");

    /**
     * 审核状态编码
     */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的审核状态编码
     */
    AuditStatus(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的审核状态编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
