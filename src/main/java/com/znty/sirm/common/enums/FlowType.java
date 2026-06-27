package com.znty.sirm.common.enums;

/** 调库流程类型（flow_type 业务字段） */
public enum FlowType {
    /** 白名单调入 */
    WHITELIST_INBOUND("whitelistInbound"),
    /** 简易调入 */
    SIMPLE_INBOUND("simpleInbound"),
    /** 默认调入 */
    NORMAL_INBOUND("normalInbound"),
    /** 上调 */
    UPGRADE_INBOUND("upgradeInbound"),
    /** 下调 */
    DOWNGRADE_INBOUND("downgradeInbound"),
    /** 默认调出 */
    NORMAL_OUTBOUND("normalOutbound");

    /** 枚举 code 值 */
    private final String code;

    FlowType(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
