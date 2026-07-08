package com.znty.rrs.entity.securitypooladjustflow;

import lombok.Data;

import java.util.List;

/**
 * 证券池调库审批处理请求，用于提交当前流程步骤的通过或驳回意见。
 */
@Data
public class SecurityPoolAdjustAuditReq {

    /** 调库记录 ID */
    private Long adjustLogId;

    /** 调库批次号 */
    private String adjustBatchNo;

    /** 当前处理的流程步骤 ID */
    private Long stepId;

    /** 处理动作：approve=通过 / reject=驳回 */
    private String processAction;

    /** 处理意见 */
    private String processComment;

    /** 当前处理人 ID */
    private String handlerId;

    /** 当前处理人名称 */
    private String handlerName;

    /**
     * 调入改判目标池 ID（评级联动，可选）。
     *
     * <p>仅信用债大库调入审批通过时有意义：审核人按主体评级×期限档矩阵改判到不同层级池（上调/降库）。
     * 为空时按原提交目标池落地；非空时校验须在矩阵允许池列表内，并改判落地到该池。
     */
    private Long redirectPoolId;

    /** 调库记录附件变更列表，仅驳回待修改提交时使用 */
    private List<AttachmentChange> attachmentChanges;

    /**
     * 单条调库记录的附件变更
     */
    @Data
    public static class AttachmentChange {

        /** 调库记录 ID */
        private Long adjustLogId;

        /** 信评报告本地上传附件在 multipart 文件数组中的下标 */
        private List<Integer> creditReportFileIndexes;

        /** 其他材料本地上传附件在 multipart 文件数组中的下标 */
        private List<Integer> materialFileIndexes;

        /** 需要复制为信评报告附件的报告库附件 ID */
        private List<Long> creditReportSourceAttachmentIds;

        /** 需要复制为其他材料附件的报告库附件 ID */
        private List<Long> materialSourceAttachmentIds;

        /** 需要逻辑删除的原调库附件 ID */
        private List<Long> deleteAttachmentIds;
    }
}
