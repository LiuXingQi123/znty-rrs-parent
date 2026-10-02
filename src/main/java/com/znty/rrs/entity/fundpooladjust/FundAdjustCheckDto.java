package com.znty.rrs.entity.fundpooladjust;

import java.util.List;
import lombok.Data;

/** 基金调库校验结果 */
@Data
public class FundAdjustCheckDto {
    /** 展开后的手工、联动和互斥调库项 */
    private List<CheckResultItem> items;

    /** 单个调库项校验结果 */
    @Data
    public static class CheckResultItem {
        /** 基金代码 */
        private String fundCode;
        /** 基金简称 */
        private String fundShortName;
        /** 基金产品类型 */
        private String securityType;
        /** 目标池 ID */
        private Long targetPoolId;
        /** 目标池名称 */
        private String poolName;
        /** 投资池类型 */
        private String poolType;
        /** 调整方向 */
        private String adjustMode;
        /** 项目来源：manual / linkage / mutex */
        private String itemTag;
        /** 同组标识 */
        private String adjustGroupKey;
        /** 是否允许调整 */
        private boolean canAdjust;
        /** 阻断原因 */
        private List<String> failReasons;
        /** 警告 */
        private List<String> warnings;
        /** 一般流程候选 */
        private List<FlowOption> flowOptions;
    }

    /** 流程候选 */
    @Data
    public static class FlowOption {
        /** 流程类型 */
        private String flowType;
        /** 流程名称 */
        private String flowName;
        /** 流程 ID */
        private Long flowId;
        /** 流程 Key */
        private String flowKey;
        /** 是否推荐 */
        private boolean recommended;
        /** 是否命中 */
        private boolean matched;
        /** 是否可选择 */
        private boolean selectable;
        /** 命中流程的原因 */
        private List<String> matchReasons;
        /** 未命中流程的原因 */
        private List<String> unmatchReasons;
    }
}
