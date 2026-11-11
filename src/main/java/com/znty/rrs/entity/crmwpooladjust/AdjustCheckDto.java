package com.znty.rrs.entity.crmwpooladjust;

import lombok.Data;

import java.util.List;

/**
 * 返回 CRMW 凭证与标的证券组合的调库校验结果
 *
 * 1. 提供手工项及系统补充的联动、互斥和同券跨市场项，逐项说明是否可调整及原因
 * 2. 汇总各项的审批流程候选，并返回本次推荐的流程
 */
@Data
public class AdjustCheckDto {

    /** 本次调库的逐项校验结果，包含手工项及系统补充的调整项 */
    private List<CheckResultItem> items;

    /** 本次调库汇总去重后的流程候选 */
    private List<FlowOption> flowOptions;

    /** 推荐流程 ID */
    private Long recommendedFlowId;

    /** 推荐流程编码 */
    private String recommendedFlowKey;

    /** 推荐流程类型 */
    private String recommendedFlowType;

    /**
     * 保存一条调整项的校验结果，记录实际凭证、标的证券、目标池和调整方向
     * 手工项与配套项通过分组标识关联，校验原因和提示随该项返回
     */
    @Data
    public static class CheckResultItem {

        /** 标的证券代码 */
        private String securityCode;

        /** 标的证券简称 */
        private String securityShortName;

        /** 标的证券类型 */
        private String securityType;

        /** CRMW 凭证名称 */
        private String crmwName;

        /** CRMW 凭证代码 */
        private String crmwScode;

        /** CRMW 凭证的证券类型 */
        private String crmwStype;

        /** 触发本项调整的主组合标识（crmwScode|securityCode） */
        private String sourceSecurityCode;

        /** 目标投资池 ID */
        private Long targetPoolId;

        /** 投资池全名，用斜线连接各级池名，例如“专户产品/二级库” */
        private String poolName;

        /** 投资池类型 */
        private String poolType;

        /** 调整方向（调入 / 调出） */
        private String adjustMode;

        /** 调整项来源（manual=手工选择 / linkage=联动调整 / mutex=互斥调整 / related=同券跨市场调整） */
        private String itemTag;

        /** 调库分组标识，由手工项及其触发的联动、互斥和同券跨市场项共用 */
        private String adjustGroupKey;

        /** 当前调整项是否允许提交 */
        private boolean canAdjust;

        /** 校验原因列表，Excel 中允许提交的业务失败也保留在这里供用户查看 */
        private List<String> failReasons;

        /** 不影响提交的规则提示，例如命中弹性禁投池 */
        private List<String> warnings;

        /** 当前调整项匹配到的审批流程候选 */
        private List<FlowOption> flowOptions;
    }

    /**
     * 保存一个审批流程候选及其推荐、匹配和可选状态
     * 页面使用匹配与未匹配原因说明该流程为什么可选或不可选
     */
    @Data
    public static class FlowOption {

        /** 流程类型编码（whitelistInbound / simpleInbound / normalInbound / specialInbound / upgradeInbound / downgradeInbound / normalOutbound / batchInbound / batchOutbound） */
        private String flowType;

        /** 流程名称 */
        private String flowName;

        /** 流程 ID */
        private Long flowId;

        /** 流程编码 */
        private String flowKey;

        /** 是否为本次校验推荐的流程 */
        private boolean recommended;

        /** 调整项是否满足该流程的业务条件 */
        private boolean matched;

        /** 页面是否允许选择该流程 */
        private boolean selectable;

        /** 匹配原因 */
        private List<String> matchReasons;

        /** 未匹配原因 */
        private List<String> unmatchReasons;
    }
}
