package com.znty.rrs.entity.fundpooladjust;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/** 基金池调整提交请求 */
@Data
public class FundPoolAdjustSubmitReq {
    /** 基金代码 */
    private String fundCode;
    /** 基金评分，整单必填 */
    private BigDecimal fundScore;
    /** 基金投资类型，整单必填 */
    private String fundInvestmentType;
    /** 是否需要风管领导审批，必填：1=是 / 0=否 */
    private Integer needRiskLeaderApproval;
    /** 调整类型 */
    private String adjustType;
    /** 调整原因 */
    private String adjustReason;
    /** 调整意见 */
    private String adjustAdvice;
    /** 调整人 ID */
    private String adjusterId;
    /** 调整人名称 */
    private String adjusterName;
    /** 调库明细 */
    private List<AdjustItem> items;

    /** 单条基金调库明细 */
    @Data
    public static class AdjustItem {
        /** 目标池 ID */
        private Long targetPoolId;
        /** 目标池名称 */
        private String targetPoolName;
        /** 投资池类型 */
        private String poolType;
        /** 调整方向 */
        private String adjustMode;
        /** 项目来源：manual / linkage / mutex */
        private String itemTag;
        /** 同组标识 */
        private String adjustGroupKey;
        /** 流程 ID */
        private Long flowId;
        /** 流程 Key */
        private String flowKey;
        /** 流程类型 */
        private String flowType;
        /** 调整说明 */
        private String adjustmentNote;
        /** 基金报告上传文件下标 */
        private List<Integer> reportFileIndexes;
        /** 其他材料上传文件下标 */
        private List<Integer> materialFileIndexes;
        /** 报告库附件 ID */
        private List<Long> reportSourceAttachmentIds;
        /** 其他材料来源附件 ID */
        private List<Long> materialSourceAttachmentIds;
    }
}
