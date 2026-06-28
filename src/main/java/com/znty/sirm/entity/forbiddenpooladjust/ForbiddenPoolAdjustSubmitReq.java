package com.znty.sirm.entity.forbiddenpooladjust;

import lombok.Data;

import java.util.List;

/**
 * 禁投池主体调整提交请求对象。
 */
@Data
public class ForbiddenPoolAdjustSubmitReq {

    /** 主体代码 */
    private String companyCode;
    /** 主体简称 */
    private String companyShortName;
    /** 主体类型 */
    private String securityType;
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

    /**
     * 单条调库明细。
     */
    @Data
    public static class AdjustItem {
        /** 目标投资池 ID */
        private Long targetPoolId;
        /** 目标投资池名称 */
        private String targetPoolName;
        /** 投资池类型 */
        private String poolType;
        /** 调整方向 */
        private String adjustMode;
        /** 调整项来源：manual/linkage/mutex */
        private String itemTag;
        /** 调库分组 Key */
        private String adjustGroupKey;
        /** 流程 ID */
        private Long flowId;
        /** 流程 Key */
        private String flowKey;
        /** 流程类型 */
        private String flowType;
        /** 调整说明 */
        private String adjustmentNote;
        /** 信评报告文件下标 */
        private List<Integer> creditReportFileIndexes;
        /** 其他材料文件下标 */
        private List<Integer> materialFileIndexes;
        /** 信评报告来源附件 ID */
        private List<Long> creditReportSourceAttachmentIds;
        /** 其他材料来源附件 ID */
        private List<Long> materialSourceAttachmentIds;
    }
}
