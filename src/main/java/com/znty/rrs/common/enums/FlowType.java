package com.znty.rrs.common.enums;

/**
 * 调库流程类型
 *
 * 通过业务字段 {@code flow_type} 区分不同审批流程，快速和批量流程分别读取目标池的对应配置
 */
public enum FlowType {

    /**
     * 白名单调入
     */
    WHITELIST_INBOUND("whitelistInbound"),

    /**
     * 简易调入
     */
    SIMPLE_INBOUND("simpleInbound"),

    /**
     * 股票快速调入（投资池 simple_in 配置）
     */
    FAST_INBOUND("fastInbound"),

    /**
     * 股票快速调出（投资池 simple_out 配置）
     */
    FAST_OUTBOUND("fastOutbound"),

    /**
     * 默认调入
     */
    NORMAL_INBOUND("normalInbound"),

    /**
     * 特殊审批
     */
    SPECIAL_INBOUND("specialInbound"),

    /**
     * 上调
     */
    UPGRADE_INBOUND("upgradeInbound"),

    /**
     * 下调
     */
    DOWNGRADE_INBOUND("downgradeInbound"),

    /**
     * 默认调出
     */
    NORMAL_OUTBOUND("normalOutbound"),

    /**
     * 批量调入（投资池 batch_in 配置）
     */
    BATCH_INBOUND("batchInbound"),

    /**
     * 批量调出（投资池 batch_out 配置）
     */
    BATCH_OUTBOUND("batchOutbound");

    /**
     * 调库流程类型编码
     */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的调库流程类型编码
     */
    FlowType(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的调库流程类型编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
