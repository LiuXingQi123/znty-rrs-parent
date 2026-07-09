package com.znty.rrs.entity.crmwpooladjust;

import lombok.Data;

import java.util.List;

/**
 * 证券调库校验结果 DTO，返回每个目标池是否可调整及不可调整的原因列表
 */
@Data
public class AdjustCheckDto {

    /** 各目标池校验结果列表（顺序与请求 items 一致） */
    private List<CheckResultItem> items;

    /** 本次调库可选流程列表 */
    private List<FlowOption> flowOptions;

    /** 推荐流程 ID */
    private Long recommendedFlowId;

    /** 推荐流程 Key */
    private String recommendedFlowKey;

    /** 推荐流程类型 */
    private String recommendedFlowType;

    /**
     * 单个目标池校验结果
     */
    @Data
    public static class CheckResultItem {

        /** 目标投资池 ID */
        private Long targetPoolId;

        /** 投资池路径名称（格式：父级名称/子级名称，如"专户产品/二级库"） */
        private String poolName;

        /** 投资池类型 */
        private String poolType;

        /** 调整方向：调入 / 调出 */
        private String adjustMode;

        /** 调整项来源：manual=用户主动选择, linkage=联动调整, mutex=互斥调整 */
        private String itemTag;

        /** 调库分组 Key：手工项及其触发的联动/互斥项共用 */
        private String adjustGroupKey;

        /** 是否可调整 */
        private boolean canAdjust;

        /** 不可调整原因列表（可调整时为空列表） */
        private List<String> failReasons;

        /** 警告原因列表（可调整但有警告时非空，如弹性禁投池命中，不阻断调库） */
        private List<String> warnings;

        /** 当前调库项可选流程列表 */
        private List<FlowOption> flowOptions;
    }

    /**
     * 调库流程候选项
     */
    @Data
    public static class FlowOption {

        /** 流程类型：whitelistInbound / simpleInbound / normalInbound / specialInbound / upgradeInbound / downgradeInbound / normalOutbound */
        private String flowType;

        /** 流程名称 */
        private String flowName;

        /** 流程 ID */
        private Long flowId;

        /** 流程 Key */
        private String flowKey;

        /** 是否为后端推荐流程 */
        private boolean recommended;

        /** 业务条件是否命中 */
        private boolean matched;

        /** 前端是否可选择 */
        private boolean selectable;

        /** 匹配原因 */
        private List<String> matchReasons;

        /** 未匹配原因 */
        private List<String> unmatchReasons;
    }
}
